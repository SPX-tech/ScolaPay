import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { ListTransactionsQueryDto } from './dto/list-transactions-query.dto';
import { VoidTransactionDto } from './dto/void-transaction.dto';
import { Prisma } from '../../generated/prisma/client';
import { AuditService } from '../audit/audit.service';

@Injectable()
export class TransactionsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async listTenantTransactions(tenantId: string, query: ListTransactionsQueryDto) {
    const { page = 1, limit = 50, type, status, studentId, startDate, endDate } = query;
    const skip = (page - 1) * limit;

    const where: Prisma.TransactionWhereInput = { tenantId };

    if (type) where.type = type;
    if (status) where.status = status;
    if (studentId) where.studentId = studentId;
    if (startDate || endDate) {
      where.recordedAt = {};
      if (startDate) where.recordedAt.gte = new Date(startDate);
      if (endDate) where.recordedAt.lte = new Date(endDate);
    }

    const [transactions, total] = await Promise.all([
      this.prisma.transaction.findMany({
        where,
        orderBy: { recordedAt: 'desc' },
        skip,
        take: limit,
        include: {
          student: {
            select: { id: true, studentNumber: true, fullName: true },
          },
          recordedBy: {
            select: { id: true, fullName: true, email: true },
          },
        },
      }),
      this.prisma.transaction.count({ where }),
    ]);

    return {
      data: transactions,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async getTenantTransactionById(tenantId: string, transactionId: string) {
    const transaction = await this.prisma.transaction.findFirst({
      where: { id: transactionId, tenantId },
      include: {
        student: {
          select: { id: true, studentNumber: true, fullName: true },
        },
        recordedBy: {
          select: { id: true, fullName: true, email: true },
        },
      },
    });

    if (!transaction) {
      throw new NotFoundException('Transaction not found.');
    }

    return transaction;
  }

  async listStudentTransactions(tenantId: string, studentId: string, query: ListTransactionsQueryDto) {
    const { page = 1, limit = 50 } = query;
    const skip = (page - 1) * limit;

    const where: Prisma.TransactionWhereInput = { tenantId, studentId };

    const [transactions, total] = await Promise.all([
      this.prisma.transaction.findMany({
        where,
        orderBy: { recordedAt: 'desc' },
        skip,
        take: limit,
      }),
      this.prisma.transaction.count({ where }),
    ]);

    return {
      data: transactions,
      meta: {
        total,
        page,
        limit,
        totalPages: Math.ceil(total / limit),
      },
    };
  }

  async voidTransaction(
    tenantId: string,
    transactionId: string,
    userId: string,
    userRole: string,
    dto: VoidTransactionDto,
  ) {
    const allowedRoles = ['TENANT_ADMIN', 'ACCOUNTANT'];
    if (!allowedRoles.includes(userRole)) {
      throw new ForbiddenException('Only admins and accountants can void transactions.');
    }

    const transaction = await this.prisma.transaction.findFirst({
      where: { id: transactionId, tenantId, status: 'POSTED' },
    });

    if (!transaction) {
      throw new NotFoundException('Transaction not found or already voided.');
    }

    // Revert student debt/credit
    const student = await this.prisma.student.findFirst({
      where: { id: transaction.studentId, tenantId },
    });

    if (!student) {
      throw new NotFoundException('Associated student not found.');
    }

    const laterPostedTransaction = await this.prisma.transaction.findFirst({
      where: {
        tenantId,
        studentId: transaction.studentId,
        status: 'POSTED',
        recordedAt: { gt: transaction.recordedAt },
      },
      orderBy: { recordedAt: 'asc' },
    });

    if (laterPostedTransaction) {
      throw new ForbiddenException(
        'Cannot void an older transaction while newer posted transactions exist.',
      );
    }

    const amountCents = BigInt(transaction.amountCents);
    const previousDebtCents = BigInt(transaction.previousDebtCents);
    const currentCreditCents = BigInt(student.creditCents);
    const overpaymentCents =
      amountCents > previousDebtCents
        ? amountCents - previousDebtCents
        : 0n;
    const newCreditCents = currentCreditCents - overpaymentCents;

    const result = await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      // Void the transaction
      const voidedTransaction = await tx.transaction.update({
        where: { id: transactionId },
        data: {
          status: 'VOIDED',
          voidedAt: new Date(),
          voidReason: dto.reason,
        },
      });

      // Restore student balance
      await tx.student.update({
        where: { id: student.id },
        data: {
          debtCents: previousDebtCents,
          creditCents: newCreditCents < 0n ? 0n : newCreditCents,
        },
      });

      return voidedTransaction;
    });

    await this.auditService.record({
      tenantId,
      actorUserId: userId,
      action: 'transaction.void',
      targetType: 'transaction',
      targetId: transactionId,
      before: transaction,
      after: result,
      metadata: {
        reason: dto.reason,
        studentId: student.id,
        restoredDebtCents: previousDebtCents.toString(),
        restoredCreditCents: newCreditCents < 0n ? '0' : newCreditCents.toString(),
      },
    });

    return { message: 'Transaction voided successfully.', transactionId };
  }
}
