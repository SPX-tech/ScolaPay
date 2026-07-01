import { Controller, Inject, Post, Req, UnauthorizedException } from '@nestjs/common';
import type { RawBodyRequest } from '@nestjs/common';
import type { Request } from 'express';
import { Public } from '../../common/decorators/public.decorator';
import { LedgerService } from './ledger.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import type { PaymentProvider } from '../payment/payment-provider.interface';

/**
 * Forme provisoire du payload : le contrat réel du webhook Kadryza (noms
 * de champs, schéma de signature) n'est pas encore documenté. À ajuster
 * dès que la doc réelle est fournie — voir KadryzaPaymentProvider.
 */
type KadryzaWebhookPayload = {
  reference: string;
  status: 'SUCCESS' | 'FAILED';
  reason?: string;
};

/**
 * Endpoint public (aucun contexte tenant/utilisateur disponible) : la
 * confirmation identifie la transaction et son tenant uniquement via
 * `providerReference`, qui est unique globalement. Voir
 * LedgerService.confirmMobilePayment().
 */
@Controller('payments')
export class PaymentsWebhookController {
  constructor(
    private readonly ledgerService: LedgerService,
    @Inject(PAYMENT_PROVIDER) private readonly paymentProvider: PaymentProvider,
  ) {}

  @Public()
  @Post('webhook-kadryza')
  async handleKadryzaWebhook(@Req() request: RawBodyRequest<Request>) {
    const rawBody = request.rawBody ?? Buffer.from(JSON.stringify(request.body ?? {}));

    const isValid = this.paymentProvider.verifyWebhookSignature(
      rawBody,
      request.headers as Record<string, string | string[] | undefined>,
    );

    if (!isValid) {
      throw new UnauthorizedException('Invalid webhook signature.');
    }

    const payload = request.body as KadryzaWebhookPayload;

    const result = await this.ledgerService.confirmMobilePayment({
      providerReference: payload.reference,
      status: payload.status,
      failureReason: payload.reason,
    });

    // Toujours 200 pour un webhook compris (même déjà traité / référence
    // inconnue) : seule une signature invalide justifie un rejet explicite,
    // pour éviter des tempêtes de retry inutiles côté prestataire.
    return { received: true, outcome: result.outcome };
  }
}
