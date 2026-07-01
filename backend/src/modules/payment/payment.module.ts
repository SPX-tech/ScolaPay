import { Module } from '@nestjs/common';
import { PAYMENT_PROVIDER } from './payment-provider.interface';
import { MockPaymentProvider } from './mock-payment.provider';
import { KadryzaPaymentProvider } from './kadryza-payment.provider';

@Module({
  providers: [
    MockPaymentProvider,
    KadryzaPaymentProvider,
    {
      provide: PAYMENT_PROVIDER,
      useFactory: (mock: MockPaymentProvider, kadryza: KadryzaPaymentProvider) =>
        process.env.PAYMENT_PROVIDER === 'kadryza' ? kadryza : mock,
      inject: [MockPaymentProvider, KadryzaPaymentProvider],
    },
  ],
  exports: [PAYMENT_PROVIDER],
})
export class PaymentModule {}
