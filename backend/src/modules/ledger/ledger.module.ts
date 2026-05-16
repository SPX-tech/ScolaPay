import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { OutboxModule } from '../outbox/outbox.module';
import { LedgerController } from './ledger.controller';
import { LedgerService } from './ledger.service';
import { ReceiptService } from './receipt.service';
import { DashboardService } from './dashboard.service';
import { DashboardController } from './dashboard.controller';

@Module({
  imports: [PrismaModule, OutboxModule],
  controllers: [LedgerController, DashboardController],
  providers: [LedgerService, ReceiptService, DashboardService],
  exports: [LedgerService, ReceiptService, DashboardService],
})
export class LedgerModule {}
