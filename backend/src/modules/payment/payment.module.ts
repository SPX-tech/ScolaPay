import { Module } from '@nestjs/common';
import { PAYMENT_PROVIDER } from './payment-provider.interface';
import { MockPaymentProvider } from './mock-payment.provider';
import { KadryzaPaymentProvider } from './kadryza-payment.provider';
import { DisabledPaymentProvider } from './disabled-payment.provider';

/**
 * Liste blanche plutôt que `!== 'production'` : un NODE_ENV absent ou mal saisi
 * sur un serveur ne doit pas ouvrir la simulation, qui accepte tout webhook.
 */
const NON_PRODUCTION_ENVIRONMENTS = ['development', 'test'];

function assertNonProductionOnly(provider: string): void {
  const environment = process.env.NODE_ENV ?? '';
  if (!NON_PRODUCTION_ENVIRONMENTS.includes(environment)) {
    throw new Error(
      `PAYMENT_PROVIDER=${provider} est interdit lorsque NODE_ENV vaut ` +
        `"${environment}" : utilisez PAYMENT_PROVIDER=disabled.`,
    );
  }
}

@Module({
  providers: [
    MockPaymentProvider,
    KadryzaPaymentProvider,
    DisabledPaymentProvider,
    {
      provide: PAYMENT_PROVIDER,
      useFactory: (
        mock: MockPaymentProvider,
        kadryza: KadryzaPaymentProvider,
        disabled: DisabledPaymentProvider,
      ) => {
        const configured = process.env.PAYMENT_PROVIDER ?? 'disabled';
        switch (configured) {
          case 'disabled':
            return disabled;
          case 'mock':
            assertNonProductionOnly(configured);
            return mock;
          case 'kadryza':
            // Connecteur encore squelettique : son contrat n'est pas documenté.
            assertNonProductionOnly(configured);
            return kadryza;
          default:
            throw new Error(`PAYMENT_PROVIDER inconnu : "${configured}".`);
        }
      },
      inject: [
        MockPaymentProvider,
        KadryzaPaymentProvider,
        DisabledPaymentProvider,
      ],
    },
  ],
  exports: [PAYMENT_PROVIDER],
})
export class PaymentModule {}
