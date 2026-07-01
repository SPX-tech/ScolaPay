import { Injectable } from '@nestjs/common';
import {
  CollectPaymentInput,
  CollectPaymentResult,
  PaymentProvider,
} from './payment-provider.interface';

/**
 * Squelette en attente du contrat réel de l'API Kadryza (URL de base,
 * authentification, schéma de la requête de collecte, format et
 * signature du webhook). Volontairement non fonctionnel : mieux vaut
 * échouer bruyamment qu'inventer une intégration de paiement ou une
 * vérification de signature.
 */
@Injectable()
export class KadryzaPaymentProvider implements PaymentProvider {
  async collect(_input: CollectPaymentInput): Promise<CollectPaymentResult> {
    throw new Error(
      "KadryzaPaymentProvider.collect() n'est pas implémenté : contrat de l'API Kadryza " +
        "(URL de base, authentification, format de la requête de collecte) requis avant activation. " +
        'Utilisez PAYMENT_PROVIDER=mock en attendant.',
    );
  }

  verifyWebhookSignature(): boolean {
    throw new Error(
      "KadryzaPaymentProvider.verifyWebhookSignature() n'est pas implémenté : schéma de signature " +
        "du webhook Kadryza requis avant activation. Ne jamais faire confiance à un webhook sans " +
        'vérification réelle.',
    );
  }
}
