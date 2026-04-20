import { Module } from '@nestjs/common';
import { OutboxModule } from '../outbox/outbox.module';
import { LedgerController } from './ledger.controller';
import { LedgerService } from './ledger.service';
import { ReceiptService } from './receipt.service';

@Module({
  imports: [OutboxModule],
  controllers: [LedgerController],
  providers: [LedgerService, ReceiptService],
})
export class LedgerModule {}
