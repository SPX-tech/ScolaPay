import { Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import {
  CollectPaymentInput,
  CollectPaymentResult,
  PayoutInput,
  PayoutResult,
  PaymentProvider,
} from './payment-provider.interface';

/**
 * Simule un push USSD et des virements sortants, sans jamais atteindre un
 * vrai réseau mobile money/bancaire. Ne rappelle jamais le webhook
 * lui-même : la confirmation doit être déclenchée séparément (ex. appel
 * direct au webhook en dev/tests).
 */
@Injectable()
export class MockPaymentProvider implements PaymentProvider {
  private readonly logger = new Logger(MockPaymentProvider.name);

  collect(input: CollectPaymentInput): Promise<CollectPaymentResult> {
    this.logger.log(
      `[MOCK USSD] Push envoyé à ${input.payerPhone} pour ${input.studentName} : ` +
        `${input.amountCents} ${input.currency} (réf. ${input.reference})`,
    );

    return Promise.resolve({
      status: 'PENDING',
      providerTransactionId: `MOCK-${randomUUID()}`,
    });
  }

  payout(input: PayoutInput): Promise<PayoutResult> {
    this.logger.log(
      `[MOCK PAYOUT] Virement accepté vers ${input.destination} (${input.method}) : ` +
        `${input.amountCents} (réf. ${input.reference})`,
    );

    return Promise.resolve({
      status: 'PROCESSING',
      providerTransactionId: `MOCK-PAYOUT-${randomUUID()}`,
    });
  }

  verifyWebhookSignature(
    _rawBody?: Buffer | string,
    _headers?: Record<string, string | string[] | undefined>,
  ): boolean {
    // Pas de sécurité réelle en mode mock : usage dev/tests uniquement.
    return true;
  }
}
