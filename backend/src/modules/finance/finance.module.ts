import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { VirtualAccountService } from './virtual-account.service';
import { WithdrawalsService } from './withdrawals.service';
import { SchoolsController } from './schools.controller';

@Module({
  imports: [PrismaModule],
  controllers: [SchoolsController],
  providers: [VirtualAccountService, WithdrawalsService],
  // Exporté pour un futur branchement depuis le ledger (crédit sur MOBILE_PAYMENT).
  exports: [VirtualAccountService],
})
export class FinanceModule {}
