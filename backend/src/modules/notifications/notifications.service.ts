import { Injectable, Logger } from '@nestjs/common';

type CashPaymentPayload = {
  transactionId: string;
  tenantId: string;
  studentId: string;
  studentName: string;
  studentPhone?: string;
  guardianName?: string;
  guardianPhone?: string;
  amountCents: string;
  receiptNumber: string;
  recordedAt: string;
};

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);

  async queueCashPaymentNotification(eventPayload: unknown): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as CashPaymentPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    // En DEV, on mock l'envoi SMS (simple log console)
    this.logger.log('═══════════════════════════════════════════');
    this.logger.log('📱 [DEV MOCK SMS] Notification de paiement');
    this.logger.log('═══════════════════════════════════════════');
    this.logger.log(`   Élève    : ${payload.studentName}`);
    this.logger.log(`   Montant  : ${amountFormatted} XAF`);
    this.logger.log(`   Reçu n°  : ${payload.receiptNumber}`);
    this.logger.log(`   Date     : ${payload.recordedAt}`);
    this.logger.log('───────────────────────────────────────────');

    if (payload.guardianPhone) {
      this.logger.log(`   📞 SMS envoyé à : ${payload.guardianPhone}`);
      this.logger.log(`   📄 Message :`);
      this.logger.log(`   "Paiement de ${amountFormatted} XAF`);
      this.logger.log(`    reçu pour ${payload.studentName}.`);
      this.logger.log(`    Reçu n°: ${payload.receiptNumber}.`);
      this.logger.log(`    Merci pour votre confiance."`);
    }

    this.logger.log('═══════════════════════════════════════════\n');

    return {
      status: 'queued',
      provider: 'mock-sms',
      payload,
    };
  }
}
