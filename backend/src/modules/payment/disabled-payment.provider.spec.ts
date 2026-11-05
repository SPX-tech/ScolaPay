import { DisabledPaymentProvider } from './disabled-payment.provider';

describe('DisabledPaymentProvider', () => {
  const provider = new DisabledPaymentProvider();

  it('refuse toute collecte sans lever d’exception', async () => {
    await expect(provider.collect()).resolves.toEqual({
      status: 'FAILED',
      failureReason: expect.stringContaining('pas activés'),
    });
  });

  it('refuse tout virement sortant', async () => {
    await expect(provider.payout()).resolves.toEqual(
      expect.objectContaining({ status: 'FAILED' }),
    );
  });

  it('ne fait confiance à aucun webhook', () => {
    expect(provider.verifyWebhookSignature()).toBe(false);
  });
});
