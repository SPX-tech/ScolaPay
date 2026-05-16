import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { OutboxService } from '../outbox/outbox.service';
import { ReceiptService } from './receipt.service';
import { RecordCashPaymentDto } from './dto/record-cash-payment.dto';

type RoleType = 'TENANT_ADMIN' | 'CASHIER' | 'ACCOUNTANT' | 'AUDITOR';

type RecordPaymentResult = {
  transactionId: string;
  studentId: string;
  amountCents: bigint;
  previousDebtCents: bigint;
  newDebtCents: bigint;
  creditCents: bigint;
  currency: string;
  receiptNumber: string;
  receiptHash: string;
  qrCodeData: string;
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
    tenantId: string,
    userId: string,
    userRole: string,
    dto: RecordCashPaymentDto,
  ): Promise<RecordPaymentResult> {
    const allowedRoles: RoleType[] = ['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT'];
    if (!allowedRoles.includes(userRole as RoleType)) {
      throw new ForbiddenException(
        'Only admins, cashiers, and accountants can record payments.',
      );
    }

    // Idempotency check
    if (dto.idempotencyKey) {
      const existing = await this.prisma.transaction.findUnique({
        where: {
          tenantId_idempotencyKey: {
            tenantId,
            idempotencyKey: dto.idempotencyKey,
          },
        },
      });

      if (existing) {
        throw new ConflictException('Transaction already recorded (idempotent).');
      }
    }

    // Get tenant + validate
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
    });

    if (!tenant) {
      throw new NotFoundException('Tenant not found.');
    }

    // Get student + validate
    const student = await this.prisma.student.findFirst({
      where: { id: dto.studentId, tenantId, status: 'ACTIVE' },
    });

    if (!student) {
      throw new NotFoundException('Active student not found.');
    }

    const amountCents = BigInt(dto.amountCents);
    const previousDebtCents = student.debtCents;
    const zero = 0n;
    let newDebtCents = previousDebtCents - amountCents;
    let newCreditCents: bigint = student.creditCents;

    // If overpayment, apply excess to credit
    if (newDebtCents < zero) {
      newCreditCents = newCreditCents + newDebtCents * -1n;
      newDebtCents = zero;
    }

    const receiptNumber = this.receiptService.generateReceiptNumber(tenant.code);
    const receiptHash = this.receiptService.generateReceiptHash({
      tenantId,
      studentId: student.id,
      recordedByUserId: userId,
      amountCents,
      previousDebtCents,
      newDebtCents,
      currency: tenant.currency,
      receiptNumber,
      recordedAt: new Date(),
    });

    // Generate QR code data (stored as JSON string)
    const qrCodeData = this.receiptService.generateReceiptData({
      id: 'pending',
      receiptNumber,
      receiptHash,
      amountCents,
      previousDebtCents,
      newDebtCents,
      currency: tenant.currency,
      recordedAt: new Date(),
      student: { fullName: student.fullName, studentNumber: student.studentNumber },
      recordedBy: { fullName: 'pending' },
    });

    // Execute ACID transaction
    try {
      const result = await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
        const transaction = await tx.transaction.create({
          data: {
            tenantId,
            studentId: student.id,
            recordedByUserId: userId,
            type: 'CASH_PAYMENT',
            status: 'POSTED',
            amountCents,
            previousDebtCents,
            newDebtCents,
            currency: tenant.currency,
            receiptNumber,
            receiptHash,
            qrCodeData,
            idempotencyKey: dto.idempotencyKey,
            note: dto.note,
            metadata: dto.metadata as any,
            recordedAt: new Date(),
          },
        });

        await tx.student.update({
          where: { id: student.id },
          data: {
            debtCents: newDebtCents,
            creditCents: newCreditCents,
            lastPaymentAt: new Date(),
          },
        });

        // Outbox event for notification
        await this.outboxService.createEvent({
          tx,
          tenantId,
          aggregateType: 'transaction',
          aggregateId: transaction.id,
          eventType: 'cash-payment.recorded',
          payload: {
            transactionId: transaction.id,
            tenantId,
            studentId: student.id,
            studentName: student.fullName,
            guardianPhone: student.guardianPhone,
            guardianName: student.guardianName,
            amountCents: amountCents.toString(),
            receiptNumber,
            recordedAt: transaction.recordedAt.toISOString(),
          },
        });

        return transaction;
      });

      return {
        transactionId: result.id,
        studentId: student.id,
        amountCents: result.amountCents,
        previousDebtCents: result.previousDebtCents,
        newDebtCents: result.newDebtCents,
        creditCents: newCreditCents,
        currency: result.currency,
        receiptNumber: result.receiptNumber,
        receiptHash: result.receiptHash,
        qrCodeData: result.qrCodeData!,
        recordedAt: result.recordedAt,
      };
    } catch (error: any) {
      if (error?.code === 'P2002') {
        throw new ConflictException('Transaction already recorded (duplicate).');
      }
      throw error;
    }
  }
}
