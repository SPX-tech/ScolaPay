import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { OutboxModule } from '../outbox/outbox.module';
import { FinanceModule } from '../finance/finance.module';
import { PaymentModule } from '../payment/payment.module';
import { LedgerController } from './ledger.controller';
import { PaymentsWebhookController } from './payments-webhook.controller';
import { LedgerService } from './ledger.service';
import { ReceiptService } from './receipt.service';
import { DashboardService } from './dashboard.service';
import { DashboardController } from './dashboard.controller';

@Module({
  imports: [
    PrismaModule,
    OutboxModule,
    AuditModule,
    FinanceModule,
    PaymentModule,
  ],
  controllers: [
    LedgerController,
    PaymentsWebhookController,
    DashboardController,
  ],
  providers: [LedgerService, ReceiptService, DashboardService],
  exports: [LedgerService, ReceiptService, DashboardService],
})
export class LedgerModule {}
