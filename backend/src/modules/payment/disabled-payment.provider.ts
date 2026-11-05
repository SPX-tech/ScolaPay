import { Injectable } from '@nestjs/common';
import {
  CollectPaymentResult,
  PayoutResult,
  PaymentProvider,
} from './payment-provider.interface';

const DISABLED_REASON =
  'Les paiements numériques ne sont pas activés sur cette plateforme.';

/**
 * Fournisseur par défaut : aucun argent ne circule et aucun webhook n'est cru.
 * Les appelants traitent déjà `FAILED` (transaction échouée, blocage de retrait
 * libéré), ce qui évite tout état PENDING sans confirmation possible.
 */
@Injectable()
export class DisabledPaymentProvider implements PaymentProvider {
  collect(): Promise<CollectPaymentResult> {
    return Promise.resolve({
      status: 'FAILED',
      failureReason: DISABLED_REASON,
    });
  }

  payout(): Promise<PayoutResult> {
    return Promise.resolve({
      status: 'FAILED',
      failureReason: DISABLED_REASON,
    });
  }

  verifyWebhookSignature(): boolean {
    return false;
  }
}
