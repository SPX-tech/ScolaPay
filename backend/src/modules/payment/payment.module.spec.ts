import { Test, TestingModule } from '@nestjs/testing';
import { PaymentModule } from './payment.module';
import { PAYMENT_PROVIDER } from './payment-provider.interface';
import { MockPaymentProvider } from './mock-payment.provider';
import { KadryzaPaymentProvider } from './kadryza-payment.provider';

describe('PaymentModule (sélection du provider)', () => {
  const ORIGINAL_ENV = process.env;

  beforeEach(() => {
    process.env = { ...ORIGINAL_ENV };
  });

  afterAll(() => {
    process.env = ORIGINAL_ENV;
  });

  it('refuse les paiements simulés en production', async () => {
    process.env.NODE_ENV = 'production';
    process.env.PAYMENT_PROVIDER = 'mock';
    await expect(Test.createTestingModule({ imports: [PaymentModule] }).compile()).rejects.toThrow();
  });

  it('refuse une configuration de fournisseur inconnue', async () => {
    process.env.PAYMENT_PROVIDER = 'typo';
    await expect(Test.createTestingModule({ imports: [PaymentModule] }).compile()).rejects.toThrow();
  });

  it('sélectionne MockPaymentProvider par défaut (PAYMENT_PROVIDER non défini)', async () => {
    delete process.env.PAYMENT_PROVIDER;

    const module: TestingModule = await Test.createTestingModule({
      imports: [PaymentModule],
    }).compile();

    expect(module.get(PAYMENT_PROVIDER)).toBeInstanceOf(MockPaymentProvider);
  });

  it("sélectionne MockPaymentProvider pour toute valeur autre que 'kadryza'", async () => {
    process.env.PAYMENT_PROVIDER = 'mock';

    const module: TestingModule = await Test.createTestingModule({
      imports: [PaymentModule],
    }).compile();

    expect(module.get(PAYMENT_PROVIDER)).toBeInstanceOf(MockPaymentProvider);
  });

  it('sélectionne KadryzaPaymentProvider quand PAYMENT_PROVIDER=kadryza', async () => {
    process.env.PAYMENT_PROVIDER = 'kadryza';

    const module: TestingModule = await Test.createTestingModule({
      imports: [PaymentModule],
    }).compile();

    expect(module.get(PAYMENT_PROVIDER)).toBeInstanceOf(KadryzaPaymentProvider);
  });
});
