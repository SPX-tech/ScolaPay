import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { ListTransactionsQueryDto } from './dto/list-transactions-query.dto';
import { VoidTransactionDto } from './dto/void-transaction.dto';
import { Prisma } from '@prisma/client';

@Injectable()
export class TransactionsService {
  constructor(private readonly prisma: PrismaService) {}

  async listTenantTransactions(tenantId: string, query: ListTransactionsQueryDto) {
    const { page = 1, limit = 50, type, status, studentId, startDate, endDate } = query;
    const skip = (page - 1) * limit;

    const where: any = { tenantId };

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

    const where: any = { tenantId, studentId };

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
    dto: VoidTransactionDto,
  ) {
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

    // Calculate reversal amounts
    const debtAdjustment = transaction.amountCents;
    const newDebtCents = student.debtCents + debtAdjustment;
    const newCreditCents = student.creditCents > 0n
      ? student.creditCents - transaction.amountCents
      : student.creditCents;

    return await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      // Void the transaction
      await tx.transaction.update({
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
          debtCents: newDebtCents < 0n ? 0n : newDebtCents,
          creditCents: newCreditCents < 0n ? 0n : newCreditCents,
        },
      });

      return { message: 'Transaction voided successfully.', transactionId };
    });
  }
}
