import { Module } from '@nestjs/common';
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

@Module({
  imports: [
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
  ],
  controllers: [],
  providers: [],
})
export class AppModule {}
