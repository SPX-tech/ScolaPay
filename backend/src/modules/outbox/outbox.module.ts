import { Module } from '@nestjs/common';
import { PrismaModule } from '../../prisma/prisma.module';
import { OutboxController } from './outbox.controller';
import { OutboxService } from './outbox.service';
import { OutboxWorkerService } from './outbox-worker.service';
import { NotificationsModule } from '../notifications/notifications.module';

@Module({
  imports: [PrismaModule, NotificationsModule],
  controllers: [OutboxController],
  providers: [OutboxService, OutboxWorkerService],
  exports: [OutboxService, OutboxWorkerService],
})
export class OutboxModule {}
