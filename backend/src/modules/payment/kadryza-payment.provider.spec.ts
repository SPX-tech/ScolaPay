import { KadryzaPaymentProvider } from './kadryza-payment.provider';

describe('KadryzaPaymentProvider (stub)', () => {
  let provider: KadryzaPaymentProvider;

  beforeEach(() => {
    provider = new KadryzaPaymentProvider();
  });

  it("échoue bruyamment sur collect() tant que le contrat API réel n'est pas fourni", async () => {
    await expect(
      provider.collect({
        reference: 'MP-1',
        amountCents: 1_000n,
        currency: 'XAF',
        payerPhone: '+23566000000',
        tenantId: 'tenant_1',
        studentName: 'Jean Dupont',
      }),
    ).rejects.toThrow(/pas implémenté/);
  });

  it("échoue bruyamment sur verifyWebhookSignature() tant que le schéma de signature n'est pas fourni", () => {
    expect(() => provider.verifyWebhookSignature('body', {})).toThrow(
      /pas implémenté/,
    );
  });
});
