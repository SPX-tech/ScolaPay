import { Module } from '@nestjs/common';
import { ScheduleModule } from '@nestjs/schedule';
import { HealthModule } from './modules/health/health.module';
import { PrismaModule } from './prisma/prisma.module';
import { AuthModule } from './modules/auth/auth.module';
import { TenantsModule } from './modules/tenants/tenants.module';
import { UsersModule } from './modules/users/users.module';
import { StudentsModule } from './modules/students/students.module';
import { LedgerModule } from './modules/ledger/ledger.module';
import { TransactionsModule } from './modules/transactions/transactions.module';
import { OutboxModule } from './modules/outbox/outbox.module';
import { NotificationsModule } from './modules/notifications/notifications.module';
import { AuditModule } from './modules/audit/audit.module';
import { FinanceModule } from './modules/finance/finance.module';
import { DashboardModule } from './modules/ledger/dashboard.module';

@Module({
  imports: [
    ScheduleModule.forRoot(),
    PrismaModule,
    HealthModule,
    AuthModule,
    TenantsModule,
    UsersModule,
    StudentsModule,
    LedgerModule,
    TransactionsModule,
    OutboxModule,
    NotificationsModule,
    AuditModule,
    FinanceModule,
    DashboardModule,
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
