import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

@Injectable()
export class DashboardService {
  constructor(private readonly prisma: PrismaService) {}

  async getSummary(tenantId: string) {
    const now = new Date();
    const startOfDay = new Date(
      now.getFullYear(),
      now.getMonth(),
      now.getDate(),
    );
    const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);

    const todayStart = startOfDay.toISOString();
    const monthStart = startOfMonth.toISOString();

    const [
      totalStudents,
      activeStudents,
      totalTransactions,
      todayTransactions,
      monthTransactions,
      studentAgg,
      recentTransactions,
    ] = await Promise.all([
      this.prisma.student.count({ where: { tenantId } }),
      this.prisma.student.count({ where: { tenantId, status: 'ACTIVE' } }),
      this.prisma.transaction.count({ where: { tenantId, status: 'POSTED' } }),
      this.prisma.transaction.count({
        where: {
          tenantId,
          status: 'POSTED',
          recordedAt: { gte: new Date(todayStart) },
        },
      }),
      this.prisma.transaction.count({
        where: {
          tenantId,
          status: 'POSTED',
          recordedAt: { gte: new Date(monthStart) },
        },
      }),
      this.prisma.student.aggregate({
        where: { tenantId },
        _sum: { debtCents: true, creditCents: true },
      }),
      this.prisma.transaction.findMany({
        where: { tenantId, status: 'POSTED' },
        orderBy: { recordedAt: 'desc' },
        take: 5,
        include: {
          student: {
            select: { id: true, studentNumber: true, fullName: true },
          },
          recordedBy: {
            select: { id: true, fullName: true },
          },
        },
      }),
    ]);

    // Calculate today's cash collected
    const todayCashResult = await this.prisma.transaction.aggregate({
      where: {
        tenantId,
        status: 'POSTED',
        type: 'CASH_PAYMENT',
        recordedAt: { gte: new Date(todayStart) },
      },
      _sum: { amountCents: true },
    });

    // Calculate month's cash collected
    const monthCashResult = await this.prisma.transaction.aggregate({
      where: {
        tenantId,
        status: 'POSTED',
        type: 'CASH_PAYMENT',
        recordedAt: { gte: new Date(monthStart) },
      },
      _sum: { amountCents: true },
    });

    // Students with debt
    const studentsWithDebt = await this.prisma.student.count({
      where: { tenantId, debtCents: { gt: 0 } },
    });

    return {
      students: {
        total: totalStudents,
        active: activeStudents,
        withDebt: studentsWithDebt,
        totalDebtCents: studentAgg._sum.debtCents || 0n,
        totalCreditCents: studentAgg._sum.creditCents || 0n,
      },
      transactions: {
        total: totalTransactions,
        today: todayTransactions,
        thisMonth: monthTransactions,
      },
      cashCollected: {
        todayCents: todayCashResult._sum.amountCents || 0n,
        thisMonthCents: monthCashResult._sum.amountCents || 0n,
      },
      recentTransactions,
    };
  }
}
