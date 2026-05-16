import { Module } from '@nestjs/common';
import { DashboardController } from './dashboard.controller';
import { DashboardService } from './dashboard.service';
import { ReportsController } from '../dashboard/reports.controller';
import { ReceiptService } from './receipt.service';
import { PrismaModule } from '../../prisma/prisma.module';

@Module({
  imports: [PrismaModule],
  controllers: [DashboardController, ReportsController],
  providers: [DashboardService, ReceiptService],
  exports: [DashboardService],
})
export class DashboardModule {}
