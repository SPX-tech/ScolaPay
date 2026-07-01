export type CollectPaymentInput = {
  /** Référence marchande générée localement, transmise au prestataire comme identifiant de la demande. */
  reference: string;
  amountCents: bigint;
  currency: string;
  payerPhone: string;
  tenantId: string;
  studentName: string;
};

export type CollectPaymentResult = {
  status: 'PENDING' | 'FAILED';
  /** Identifiant de suivi côté prestataire (support/debug), si fourni. */
  providerTransactionId?: string;
  failureReason?: string;
};

export interface PaymentProvider {
  /**
   * Déclenche la collecte (ex: push USSD). Ne doit jamais être appelé à
   * l'intérieur d'une transaction Prisma : c'est un appel réseau externe
   * de latence non bornée.
   */
  collect(input: CollectPaymentInput): Promise<CollectPaymentResult>;

  /**
   * Vérifie l'authenticité d'un appel webhook entrant avant de faire
   * confiance à son contenu (obligatoire avant tout crédit de fonds).
   */
  verifyWebhookSignature(
    rawBody: Buffer | string,
    headers: Record<string, string | string[] | undefined>,
  ): boolean;
}

export const PAYMENT_PROVIDER = Symbol('PAYMENT_PROVIDER');
