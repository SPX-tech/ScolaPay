import { Controller, Get, Query } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { PrismaService } from '../../prisma/prisma.service';

type DailyCollectionRow = { date: string; total: bigint; count: bigint };

type StudentInfo = { id: string; fullName: string; studentNumber: string };

@Controller('dashboard')
export class ReportsController {
  constructor(
    private readonly prisma: PrismaService,
  ) {}

  @Get('reports/collections')
  async getCollectionsReport(
    @ReqContext() context: RequestContext,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string,
  ) {
    const tenantId = context.tenantId;
    const start = startDate ? new Date(startDate) : new Date(new Date().getFullYear(), new Date().getMonth(), 1);
    const end = endDate ? new Date(endDate) : new Date();

    // Daily collections
    const dailyAgg = await this.prisma.$queryRaw<DailyCollectionRow[]>`
      SELECT
        DATE(recorded_at) as date,
        COALESCE(SUM(amount_cents), 0) as total,
        COUNT(*) as count
      FROM transactions
      WHERE tenant_id = ${tenantId}
        AND status = 'POSTED'
        AND type = 'CASH_PAYMENT'
        AND recorded_at >= ${start}
        AND recorded_at <= ${end}
      GROUP BY DATE(recorded_at)
      ORDER BY date ASC
    `;

    // Totals
    const totals = await this.prisma.transaction.aggregate({
      where: {
        tenantId,
        status: 'POSTED',
        type: 'CASH_PAYMENT',
        recordedAt: { gte: start, lte: end },
      },
      _sum: { amountCents: true },
      _count: true,
    });

    // Top paying students
    const topStudents = await this.prisma.transaction.groupBy({
      by: ['studentId'],
      where: {
        tenantId,
        status: 'POSTED',
        type: 'CASH_PAYMENT',
        recordedAt: { gte: start, lte: end },
      },
      _sum: { amountCents: true },
      _count: true,
      orderBy: { _sum: { amountCents: 'desc' } },
      take: 10,
    });

    // Resolve student names for top payers
    const studentIds = topStudents.map((s: { studentId: string }) => s.studentId);
    const students = await this.prisma.student.findMany({
      where: { id: { in: studentIds } },
      select: { id: true, fullName: true, studentNumber: true },
    });
    const studentMap = new Map<string, StudentInfo>(students.map((s: StudentInfo) => [s.id, s]));

    const topPayers = topStudents.map((s: { studentId: string; _sum: { amountCents: bigint | null }; _count: number }) => ({
      studentId: s.studentId,
      fullName: studentMap.get(s.studentId)?.fullName || 'N/A',
      studentNumber: studentMap.get(s.studentId)?.studentNumber || 'N/A',
      totalCents: s._sum.amountCents || 0n,
      transactionCount: s._count,
    }));

    // Debt overview
    const debtStats = await this.prisma.student.aggregate({
      where: { tenantId },
      _sum: { debtCents: true, creditCents: true },
      _count: true,
    });

    const studentsWithDebt = await this.prisma.student.count({
      where: { tenantId, debtCents: { gt: 0 } },
    });

    return {
      period: {
        startDate: start.toISOString(),
        endDate: end.toISOString(),
      },
      summary: {
        totalCollectedCents: totals._sum.amountCents || 0n,
        transactionCount: totals._count,
      },
      dailyCollections: dailyAgg.map((d: DailyCollectionRow) => ({
        date: d.date,
        totalCents: d.total,
        transactionCount: Number(d.count),
      })),
      topPayers,
      debtOverview: {
        totalStudents: debtStats._count,
        studentsWithDebt,
        totalDebtCents: debtStats._sum.debtCents || 0n,
        totalCreditCents: debtStats._sum.creditCents || 0n,
      },
    };
  }

  @Get('reports/students-debt')
  async getStudentDebtReport(@ReqContext() context: RequestContext) {
    const tenantId = context.tenantId;

    const students = await this.prisma.student.findMany({
      where: { tenantId, status: 'ACTIVE' },
      orderBy: { debtCents: 'desc' },
      take: 100,
      include: {
        class: { select: { id: true, name: true } },
      },
    });

    return students.map((s: { id: string; studentNumber: string; fullName: string; class: { name: string } | null; debtCents: bigint; creditCents: bigint; guardianPhone: string | null; status: string }) => ({
      id: s.id,
      studentNumber: s.studentNumber,
      fullName: s.fullName,
      className: s.class?.name || null,
      debtCents: s.debtCents,
      creditCents: s.creditCents,
      guardianPhone: s.guardianPhone,
      status: s.status,
    }));
  }
}
