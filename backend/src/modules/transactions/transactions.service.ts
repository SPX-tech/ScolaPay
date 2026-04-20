import { Injectable, NotFoundException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class TransactionsService {
  constructor(private readonly prisma: PrismaService) {}

  async listTenantTransactions(tenantId: string) {
    return this.prisma.transaction.findMany({
      where: { tenantId },
      orderBy: {
        recordedAt: 'desc',
      },
      include: {
        student: {
          select: {
            id: true,
            studentNumber: true,
            fullName: true,
          },
        },
        recordedBy: {
          select: {
            id: true,
            fullName: true,
            email: true,
          },
        },
      },
      take: 200,
    });
  }

  async getTenantTransactionById(tenantId: string, transactionId: string) {
    const transaction = await this.prisma.transaction.findFirst({
      where: {
        id: transactionId,
        tenantId,
      },
      include: {
        student: {
          select: {
            id: true,
            studentNumber: true,
            fullName: true,
          },
        },
        recordedBy: {
          select: {
            id: true,
            fullName: true,
            email: true,
          },
        },
      },
    });

    if (!transaction) {
      throw new NotFoundException('Transaction not found.');
    }

    return transaction;
  }

  async listStudentTransactions(tenantId: string, studentId: string) {
    return this.prisma.transaction.findMany({
      where: {
        tenantId,
        studentId,
      },
      orderBy: {
        recordedAt: 'desc',
      },
      take: 100,
    });
  }
}
