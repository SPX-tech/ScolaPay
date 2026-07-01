import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { AuditModule } from '../audit/audit.module';
import { OutboxModule } from '../outbox/outbox.module';
import { PaymentModule } from '../payment/payment.module';
import { VirtualAccountService } from './virtual-account.service';
import { WithdrawalsService } from './withdrawals.service';
import { SchoolsController } from './schools.controller';

@Module({
  imports: [PrismaModule, AuditModule, OutboxModule, PaymentModule],
  controllers: [SchoolsController],
  providers: [VirtualAccountService, WithdrawalsService],
  // VirtualAccountService : branché depuis le ledger (crédit sur MOBILE_PAYMENT).
  // WithdrawalsService : branché depuis PaymentsWebhookController (confirmation des virements).
  exports: [VirtualAccountService, WithdrawalsService],
})
export class FinanceModule {}
