import { Injectable, Logger } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import {
  CollectPaymentInput,
  CollectPaymentResult,
  PaymentProvider,
} from './payment-provider.interface';

/**
 * Simule un push USSD : la demande est toujours acceptée immédiatement
 * (statut PENDING), sans jamais atteindre un vrai réseau mobile money.
 * Ne rappelle jamais le webhook lui-même : la confirmation doit être
 * déclenchée séparément (ex. appel direct au webhook en dev/tests).
 */
@Injectable()
export class MockPaymentProvider implements PaymentProvider {
  private readonly logger = new Logger(MockPaymentProvider.name);

  async collect(input: CollectPaymentInput): Promise<CollectPaymentResult> {
    this.logger.log(
      `[MOCK USSD] Push envoyé à ${input.payerPhone} pour ${input.studentName} : ` +
        `${input.amountCents} ${input.currency} (réf. ${input.reference})`,
    );

    return {
      status: 'PENDING',
      providerTransactionId: `MOCK-${randomUUID()}`,
    };
  }

  verifyWebhookSignature(): boolean {
    // Pas de sécurité réelle en mode mock : usage dev/tests uniquement.
    return true;
  }
}
