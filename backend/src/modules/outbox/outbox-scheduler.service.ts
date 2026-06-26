import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { OutboxWorkerService } from './outbox-worker.service';

/**
 * Déclencheur planifié du worker outbox.
 *
 * Responsabilité unique : invoquer périodiquement le traitement des
 * événements en attente. Le traitement lui-même reste dans
 * OutboxWorkerService (pur, testable, et toujours invocable manuellement
 * via POST /outbox/process).
 *
 * Le polling peut être désactivé via OUTBOX_POLLING_ENABLED=false
 * (utile en tests/CI ou pour une instance qui ne doit pas traiter l'outbox).
 */
@Injectable()
export class OutboxSchedulerService {
  private readonly logger = new Logger(OutboxSchedulerService.name);

  constructor(private readonly worker: OutboxWorkerService) {}

  @Cron(CronExpression.EVERY_MINUTE, { name: 'outbox-poll' })
  async handlePoll(): Promise<void> {
    if (process.env.OUTBOX_POLLING_ENABLED === 'false') {
      return;
    }

    try {
      await this.worker.processPendingEvents();
    } catch (error) {
      this.logger.error(
        `Échec du polling outbox planifié: ${(error as Error).message}`,
      );
    }
  }
}
