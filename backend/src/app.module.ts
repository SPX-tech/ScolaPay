import { Module } from '@nestjs/common';
import { APP_FILTER, APP_GUARD } from '@nestjs/core';
import { ScheduleModule } from '@nestjs/schedule';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import { GlobalExceptionFilter } from './common/filters/global-exception.filter';
import { AppLogger } from './common/logging/app-logger.service';
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

@Module({
  imports: [
    ScheduleModule.forRoot(),
    // Rate-limiting global par IP (défaut souple : 120 req/min). Les routes
    // sensibles resserrent via @Throttle, les webhooks/health via @SkipThrottle.
    ThrottlerModule.forRoot([{ ttl: 60_000, limit: 120 }]),
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
  ],
  controllers: [],
  providers: [
    AppLogger,
    // ThrottlerGuard en APP_GUARD (résolu par DI) : coexiste avec les guards
    // Auth/Tenant/Roles enregistrés dans main.ts.
    { provide: APP_GUARD, useClass: ThrottlerGuard },
    // Filet de sécurité unique : toute exception non rattrapée (y compris
    // Prisma) sort au format standardisé, sans fuite d'information interne.
    { provide: APP_FILTER, useClass: GlobalExceptionFilter },
  ],
})
export class AppModule {}
