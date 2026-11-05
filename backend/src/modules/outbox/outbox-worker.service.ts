import { Injectable, Logger } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { NotificationsService } from '../notifications/notifications.service';

@Injectable()
export class OutboxWorkerService {
  private readonly logger = new Logger(OutboxWorkerService.name);
  private readonly MAX_RETRIES = 3;
  private isProcessing = false;

  constructor(
    private readonly prisma: PrismaService,
    private readonly notificationsService: NotificationsService,
  ) {}

  /**
   * Traite les événements outbox en attente.
   * Appelé manuellement via un endpoint ou programmé par un scheduler externe.
   */
  async processPendingEvents() {
    if (this.isProcessing) {
      return { message: 'Already processing events, try again later.' };
    }

    this.isProcessing = true;
    this.logger.debug('Polling pending outbox events...');

    try {
      const pendingEvents = await this.prisma.outboxEvent.findMany({
        where: {
          status: { in: ['PENDING', 'FAILED'] },
          availableAt: { lte: new Date() },
          attempts: { lt: this.MAX_RETRIES },
        },
        orderBy: { createdAt: 'asc' },
        take: 50,
      });

      let processed = 0;

      for (const event of pendingEvents) {
        try {
          await this.prisma.outboxEvent.update({
            where: { id: event.id },
            data: {
              status: 'PROCESSING',
              attempts: event.attempts + 1,
            },
          });

          if (event.eventType === 'cash-payment.recorded') {
            await this.notificationsService.queueCashPaymentNotification(
              event.payload as Record<string, unknown>,
            );
          } else if (event.eventType === 'withdrawal.completed') {
            await this.notificationsService.queueWithdrawalCompletedNotification(
              event.payload as Record<string, unknown>,
            );
          } else if (event.eventType === 'withdrawal.failed') {
            await this.notificationsService.queueWithdrawalFailedNotification(
              event.payload as Record<string, unknown>,
            );
          } else if (event.eventType === 'mobile-payment.confirmed') {
            await this.notificationsService.queueMobilePaymentConfirmedNotification(
              event.payload as Record<string, unknown>,
            );
          } else if (event.eventType === 'mobile-payment.failed') {
            await this.notificationsService.queueMobilePaymentFailedNotification(
              event.payload as Record<string, unknown>,
            );
          }

          await this.prisma.outboxEvent.update({
            where: { id: event.id },
            data: {
              status: 'SENT',
              publishedAt: new Date(),
            },
          });

          processed++;
          this.logger.log(
            `Event ${event.id} (${event.eventType}) processed successfully.`,
          );
        } catch (error) {
          this.logger.error(
            `Failed to process event ${event.id}: ${(error as Error).message}`,
          );

          await this.prisma.outboxEvent.update({
            where: { id: event.id },
            data: {
              status:
                event.attempts + 1 >= this.MAX_RETRIES ? 'FAILED' : 'PENDING',
              lastError: (error as Error).message,
              availableAt: new Date(
                Date.now() + 60_000 * Math.pow(2, event.attempts),
              ),
            },
          });
        }
      }

      if (pendingEvents.length > 0) {
        this.logger.log(
          `Processed ${processed}/${pendingEvents.length} outbox events.`,
        );
      }

      return { processed, total: pendingEvents.length };
    } finally {
      this.isProcessing = false;
    }
  }
}
