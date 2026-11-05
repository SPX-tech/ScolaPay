import { Injectable, Logger } from '@nestjs/common';
import africastalkingFactory from './sms-client';

type AfricastalkingInstance = ReturnType<typeof africastalkingFactory>;

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

type WithdrawalNotificationPayload = {
  withdrawalId: string;
  tenantId: string;
  amountCents: string;
  method: string;
  destination: string;
  reference: string;
  requesterId: string;
  requesterName: string;
  requesterPhone?: string | null;
};

type WithdrawalCompletedPayload = WithdrawalNotificationPayload & {
  externalReference: string;
};

type WithdrawalFailedPayload = WithdrawalNotificationPayload & {
  reason: string;
};

type MobilePaymentNotificationPayload = {
  transactionId: string;
  tenantId: string;
  amountCents: string;
  payerPhone?: string | null;
};

type MobilePaymentConfirmedPayload = MobilePaymentNotificationPayload & {
  studentName: string;
  receiptNumber: string;
};

type MobilePaymentFailedPayload = MobilePaymentNotificationPayload & {
  reason: string;
};

@Injectable()
export class NotificationsService {
  private readonly logger = new Logger(NotificationsService.name);
  private africastalking: AfricastalkingInstance | null = null;
  private initialized = false;

  private initialize(): void {
    if (this.initialized) return;

    const apiKey = process.env.AT_API_KEY;
    const username = process.env.AT_USERNAME || 'sandbox';

    if (apiKey) {
      try {
        // Le SDK n'est instancié que lorsqu'une clé API est fournie ;
        // sans clé, on reste en mode mock (voir plus bas).
        this.africastalking = africastalkingFactory({ apiKey, username });
        this.logger.log(`Africa's Talking initialisé (username: ${username})`);
      } catch (err) {
        this.logger.warn(
          `Impossible d'initialiser Africa's Talking: ${(err as Error).message}. Fallback vers mock SMS.`,
        );
      }
    } else {
      this.logger.warn(
        'AT_API_KEY non défini. Les SMS seront simulés (mock). Définissez AT_API_KEY et AT_USERNAME dans .env pour activer les vrais SMS.',
      );
    }

    this.initialized = true;
  }

  async queueCashPaymentNotification(eventPayload: unknown): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as CashPaymentPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    this.initialize();

    const message = `ScolaPay Tchad - Paiement reçu pour ${payload.studentName}. Montant: ${amountFormatted} XAF. Reçu n°: ${payload.receiptNumber}. Merci pour votre confiance.`;

    // Si Africa's Talking est configuré, envoyer un vrai SMS
    if (this.africastalking && payload.guardianPhone) {
      try {
        const result = await this.africastalking.SMS.send({
          to: [payload.guardianPhone],
          message,
          from: process.env.AT_SENDER_ID || undefined,
        });

        this.logger.log(
          `📱 SMS envoyé via Africa's Talking à ${payload.guardianPhone} (SMS ID: ${result?.SMSMessageData?.Recipients?.[0]?.messageId || 'N/A'})`,
        );

        return {
          status: 'sent',
          provider: 'africastalking',
          payload,
        };
      } catch (error) {
        this.logger.error(
          `Erreur d'envoi SMS Africa's Talking: ${(error as Error).message}`,
        );

        return {
          status: 'failed',
          provider: 'africastalking',
          payload,
        };
      }
    }

    // Fallback: mock SMS en dev
    this.logger.log('═══════════════════════════════════════════');
    this.logger.log('📱 [MOCK SMS] Notification de paiement');
    this.logger.log('═══════════════════════════════════════════');
    this.logger.log(`   Élève    : ${payload.studentName}`);
    this.logger.log(`   Montant  : ${amountFormatted} XAF`);
    this.logger.log(`   Reçu n°  : ${payload.receiptNumber}`);
    this.logger.log(`   Date     : ${payload.recordedAt}`);
    this.logger.log('───────────────────────────────────────────');

    if (payload.guardianPhone) {
      this.logger.log(`   📞 SMS à envoyer à : ${payload.guardianPhone}`);
      this.logger.log(`   📄 Message : "${message}"`);
    }

    this.logger.log('═══════════════════════════════════════════\n');

    return {
      status: 'queued',
      provider: 'mock-sms',
      payload,
    };
  }

  async queueWithdrawalCompletedNotification(eventPayload: unknown): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as WithdrawalCompletedPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    this.initialize();

    const message = `ScolaPay Tchad - Votre retrait de ${amountFormatted} XAF a été effectué avec succès vers ${payload.destination}. Référence : ${payload.externalReference}. Merci pour votre confiance.`;

    return this.dispatchSms(payload.requesterPhone, message, payload, [
      '📱 [MOCK SMS] Notification de retrait complété',
      `   Demandeur    : ${payload.requesterName}`,
      `   Montant      : ${amountFormatted} XAF`,
      `   Destination  : ${payload.destination}`,
      `   Référence ext.: ${payload.externalReference}`,
    ]);
  }

  async queueWithdrawalFailedNotification(eventPayload: unknown): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as WithdrawalFailedPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    this.initialize();

    const message = `ScolaPay Tchad - Votre retrait de ${amountFormatted} XAF n'a pas abouti (${payload.reason}). Les fonds ont été remis à disposition sur votre compte.`;

    return this.dispatchSms(payload.requesterPhone, message, payload, [
      '📱 [MOCK SMS] Notification de retrait échoué',
      `   Demandeur   : ${payload.requesterName}`,
      `   Montant     : ${amountFormatted} XAF`,
      `   Motif       : ${payload.reason}`,
    ]);
  }

  async queueMobilePaymentConfirmedNotification(
    eventPayload: unknown,
  ): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as MobilePaymentConfirmedPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    this.initialize();

    const message = `ScolaPay Tchad - Paiement mobile de ${amountFormatted} XAF reçu pour ${payload.studentName}. Reçu n° : ${payload.receiptNumber}. Merci pour votre confiance.`;

    return this.dispatchSms(payload.payerPhone, message, payload, [
      '📱 [MOCK SMS] Notification de paiement mobile confirmé',
      `   Élève     : ${payload.studentName}`,
      `   Montant   : ${amountFormatted} XAF`,
      `   Reçu n°   : ${payload.receiptNumber}`,
    ]);
  }

  async queueMobilePaymentFailedNotification(eventPayload: unknown): Promise<{
    status: string;
    provider: string;
    payload: unknown;
  }> {
    const payload = eventPayload as MobilePaymentFailedPayload;
    const amountFormatted = Number(payload.amountCents)
      ? (Number(payload.amountCents) / 100).toLocaleString('fr-FR')
      : '0';

    this.initialize();

    const message = `ScolaPay Tchad - Votre paiement mobile de ${amountFormatted} XAF n'a pas abouti (${payload.reason}). Veuillez réessayer ou contacter l'école.`;

    return this.dispatchSms(payload.payerPhone, message, payload, [
      '📱 [MOCK SMS] Notification de paiement mobile échoué',
      `   Montant : ${amountFormatted} XAF`,
      `   Motif   : ${payload.reason}`,
    ]);
  }

  /** Chemin d'envoi partagé (Africa's Talking réel ou mock) pour toutes les notifications SMS transactionnelles. */
  private async dispatchSms(
    phone: string | null | undefined,
    message: string,
    payload: unknown,
    mockDetailLines: string[],
  ): Promise<{ status: string; provider: string; payload: unknown }> {
    if (this.africastalking && phone) {
      try {
        const result = await this.africastalking.SMS.send({
          to: [phone],
          message,
          from: process.env.AT_SENDER_ID || undefined,
        });

        this.logger.log(
          `📱 SMS envoyé via Africa's Talking à ${phone} (SMS ID: ${result?.SMSMessageData?.Recipients?.[0]?.messageId || 'N/A'})`,
        );

        return { status: 'sent', provider: 'africastalking', payload };
      } catch (error) {
        this.logger.error(
          `Erreur d'envoi SMS Africa's Talking: ${(error as Error).message}`,
        );

        return { status: 'failed', provider: 'africastalking', payload };
      }
    }

    this.logger.log('═══════════════════════════════════════════');
    for (const line of mockDetailLines) {
      this.logger.log(line);
    }
    this.logger.log('───────────────────────────────────────────');

    if (phone) {
      this.logger.log(`   📞 SMS à envoyer à : ${phone}`);
      this.logger.log(`   📄 Message : "${message}"`);
    }

    this.logger.log('═══════════════════════════════════════════\n');

    return { status: 'queued', provider: 'mock-sms', payload };
  }
}
