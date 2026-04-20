import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { OutboxService } from '../outbox/outbox.service';
import { RecordCashPaymentDto } from './dto/record-cash-payment.dto';
import { ReceiptService } from './receipt.service';
import { RequestContext } from '../../common/types/request-context.type';

type RecordPaymentResult = {
  id: string;
  tenantId: string;
  studentId: string;
  amountCents: bigint;
  previousDebtCents: bigint;
  newDebtCents: bigint;
  currency: string;
  receiptNumber: string;
  receiptHash: string;
  recordedAt: Date;
};

@Injectable()
export class LedgerService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly outboxService: OutboxService,
    private readonly receiptService: ReceiptService,
  ) {}

  async recordCashPayment(
    context: RequestContext,
    dto: RecordCashPaymentDto,
  ): Promise<RecordPaymentResult> {
    const authorizedRoles = new Set(['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT']);
    if (!authorizedRoles.has(context.userRole)) {
      throw new ForbiddenException('User is not allowed to record cash payments.');
    }

    const studentId = dto.studentId.trim();
    const idempotencyKey = dto.idempotencyKey?.trim();
    const note = dto.note?.trim();
    const amountCents = BigInt(dto.amountCents);
    if (amountCents <= 0n) {
      throw new BadRequestException('amountCents must be strictly greater than zero.');
    }

    if (idempotencyKey === '') {
      throw new BadRequestException('idempotencyKey cannot be an empty string.');
    }

    return this.prisma.$transaction(async (tx) => {
      if (idempotencyKey) {
        const existingTransaction = await tx.transaction.findFirst({
          where: {
            tenantId: context.tenantId,
            idempotencyKey,
          },
        });

        if (existingTransaction) {
          return existingTransaction;
        }
      }

      const tenant = await tx.tenant.findUnique({
        where: { id: context.tenantId },
        select: {
          id: true,
          code: true,
          currency: true,
          isActive: true,
        },
      });

      if (!tenant || !tenant.isActive) {
        throw new NotFoundException('Tenant not found or inactive.');
      }

      const student = await tx.student.findFirst({
        where: {
          id: studentId,
          tenantId: context.tenantId,
          status: 'ACTIVE',
        },
      });

      if (!student) {
        throw new NotFoundException('Student not found for this tenant.');
      }

      const previousDebtCents = student.debtCents;
      const newDebtCents = previousDebtCents > amountCents ? previousDebtCents - amountCents : 0n;
      const overPaymentCents = amountCents > previousDebtCents ? amountCents - previousDebtCents : 0n;

      const receiptNumber = this.receiptService.generateReceiptNumber(tenant.code);
      const recordedAt = new Date();
      const receiptHash = this.receiptService.generateReceiptHash({
        tenantId: context.tenantId,
        studentId: student.id,
        recordedByUserId: context.userId,
        amountCents,
        previousDebtCents,
        newDebtCents,
        currency: tenant.currency,
        receiptNumber,
        recordedAt,
      });

      try {
        const transaction = await tx.transaction.create({
          data: {
            tenantId: context.tenantId,
            studentId: student.id,
            recordedByUserId: context.userId,
            type: 'CASH_PAYMENT',
            status: 'POSTED',
            amountCents,
            previousDebtCents,
            newDebtCents,
            currency: tenant.currency,
            receiptNumber,
            receiptHash,
            idempotencyKey,
            note,
            metadata: dto.metadata,
            recordedAt,
          },
        });

        await tx.student.update({
          where: { id: student.id },
          data: {
            debtCents: newDebtCents,
            creditCents: {
              increment: overPaymentCents,
            },
            lastPaymentAt: recordedAt,
          },
        });

        await this.outboxService.createEvent({
          tx,
          tenantId: context.tenantId,
          aggregateType: 'Transaction',
          aggregateId: transaction.id,
          eventType: 'cash.payment.recorded',
          payload: {
            transactionId: transaction.id,
            tenantId: context.tenantId,
            studentId: student.id,
            amountCents: amountCents.toString(),
            currency: tenant.currency,
            receiptNumber,
            receiptHash,
            guardianPhone: student.guardianPhone,
            recordedAt: recordedAt.toISOString(),
          },
        });

        return transaction;
      } catch (error) {
        if (
          error instanceof Prisma.PrismaClientKnownRequestError &&
          error.code === 'P2002'
        ) {
          throw new ConflictException('Duplicate transaction detected.');
        }

        throw error;
      }
    });
  }
}
