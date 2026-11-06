import {
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { ListTransactionsQueryDto } from './dto/list-transactions-query.dto';
import { VoidTransactionDto } from './dto/void-transaction.dto';
import { Prisma } from '../../generated/prisma/client';
import { AuditService } from '../audit/audit.service';
import { parseDatePeriod } from '../../common/validation/date-period';
import {
  BALANCE_TRANSACTION_OPTIONS,
  lockStudentBalance,
} from '../students/student-balance-lock';

@Injectable()
export class TransactionsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly auditService: AuditService,
  ) {}

  async listTenantTransactions(
    tenantId: string,
    query: ListTransactionsQueryDto,
  ) {
    const {
      page = 1,
      limit = 50,
      type,
      status,
      studentId,
      startDate,
      endDate,
    } = query;
    const skip = (page - 1) * limit;

    const where: Prisma.TransactionWhereInput = { tenantId };

    if (type) where.type = type;
    if (status) where.status = status;
    if (studentId) where.studentId = studentId;
    if (startDate || endDate) {
      const { timezone } = await this.prisma.tenant.findUniqueOrThrow({
        where: { id: tenantId },
        select: { timezone: true },
      });
      const { start, end } = parseDatePeriod({ startDate, endDate }, timezone);
      where.recordedAt = { gte: start, lte: end };
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

  async listStudentTransactions(
    tenantId: string,
    studentId: string,
    query: ListTransactionsQueryDto,
  ) {
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
      throw new ForbiddenException(
        'Only admins and accountants can void transactions.',
      );
    }

    await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      // L'élève d'une transaction ne change jamais : cette lecture sert seulement
      // à savoir quel verrou prendre, le statut est relu ensuite sous ce verrou.
      const target = await tx.transaction.findFirst({
        where: { id: transactionId, tenantId },
        select: { studentId: true },
      });
      if (!target) {
        throw new NotFoundException('Transaction not found or already voided.');
      }

      const student = await lockStudentBalance(tx, tenantId, target.studentId);
      if (!student) {
        throw new NotFoundException('Associated student not found.');
      }

      const transaction = await tx.transaction.findFirst({
        where: { id: transactionId, tenantId, status: 'POSTED' },
      });
      if (!transaction) {
        throw new NotFoundException('Transaction not found or already voided.');
      }

      // Annuler un paiement numérique sans contrepasser le compte virtuel
      // laisserait de l'argent retirable : il faudra un vrai remboursement.
      if (transaction.type !== 'CASH_PAYMENT') {
        throw new ConflictException(
          'Only cash payments can be voided. Digital payments require a refund workflow.',
        );
      }

      const laterPostedTransaction = await tx.transaction.findFirst({
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

      const { amountCents, previousDebtCents, newDebtCents } = transaction;
      const overpaymentCents =
        amountCents > previousDebtCents ? amountCents - previousDebtCents : 0n;

      // La restauration repose sur un instantané : tout écart signifie que le
      // solde a changé depuis ce paiement (ajustement manuel) et serait écrasé.
      if (
        student.debtCents !== newDebtCents ||
        student.creditCents < overpaymentCents
      ) {
        throw new ConflictException(
          'Student balance changed since this payment; it cannot be voided automatically.',
        );
      }

      const restoredCreditCents = student.creditCents - overpaymentCents;

      const voidedTransaction = await tx.transaction.update({
        where: { id: transactionId },
        data: {
          status: 'VOIDED',
          voidedAt: new Date(),
          voidReason: dto.reason,
        },
      });

      await tx.student.update({
        where: { id: student.id },
        data: {
          debtCents: previousDebtCents,
          creditCents: restoredCreditCents,
        },
      });

      await this.auditService.record(
        {
          tenantId,
          actorUserId: userId,
          action: 'transaction.void',
          targetType: 'transaction',
          targetId: transactionId,
          before: transaction,
          after: voidedTransaction,
          metadata: {
            reason: dto.reason,
            studentId: student.id,
            restoredDebtCents: previousDebtCents.toString(),
            restoredCreditCents: restoredCreditCents.toString(),
          },
        },
        tx,
      );
    }, BALANCE_TRANSACTION_OPTIONS);

    return { message: 'Transaction voided successfully.', transactionId };
  }
}
