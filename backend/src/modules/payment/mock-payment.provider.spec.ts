import { MockPaymentProvider } from './mock-payment.provider';

describe('MockPaymentProvider', () => {
  let provider: MockPaymentProvider;

  beforeEach(() => {
    provider = new MockPaymentProvider();
  });

  describe('collect', () => {
    it('accepte toujours la demande immédiatement, en PENDING', async () => {
      const result = await provider.collect({
        reference: 'MP-1',
        amountCents: 5_000n,
        currency: 'XAF',
        payerPhone: '+23566000000',
        tenantId: 'tenant_1',
        studentName: 'Jean Dupont',
      });

      expect(result.status).toBe('PENDING');
      expect(result.providerTransactionId).toMatch(/^MOCK-/);
    });

    it('ne rappelle jamais lui-même un webhook (aucun effet de bord réseau)', async () => {
      const first = await provider.collect({
        reference: 'MP-2',
        amountCents: 1_000n,
        currency: 'XAF',
        payerPhone: '+23566000001',
        tenantId: 'tenant_1',
        studentName: 'Marie Curie',
      });
      const second = await provider.collect({
        reference: 'MP-3',
        amountCents: 1_000n,
        currency: 'XAF',
        payerPhone: '+23566000001',
        tenantId: 'tenant_1',
        studentName: 'Marie Curie',
      });

      // Deux collectes distinctes obtiennent des identifiants distincts.
      expect(first.providerTransactionId).not.toEqual(second.providerTransactionId);
    });
  });

  describe('verifyWebhookSignature', () => {
    it('accepte toujours (aucune sécurité réelle en mode mock)', () => {
      expect(provider.verifyWebhookSignature('anything', {})).toBe(true);
      expect(provider.verifyWebhookSignature('', {})).toBe(true);
    });
  });
});
