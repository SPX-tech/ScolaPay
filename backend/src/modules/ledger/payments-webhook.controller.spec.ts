import { Test, TestingModule } from '@nestjs/testing';
import { UnauthorizedException } from '@nestjs/common';
import { PaymentsWebhookController } from './payments-webhook.controller';
import { LedgerService } from './ledger.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';

describe('PaymentsWebhookController', () => {
  let controller: PaymentsWebhookController;
  let mockLedgerService: { confirmMobilePayment: jest.Mock };
  let mockPaymentProvider: { verifyWebhookSignature: jest.Mock };

  beforeEach(async () => {
    mockLedgerService = { confirmMobilePayment: jest.fn() };
    mockPaymentProvider = { verifyWebhookSignature: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      controllers: [PaymentsWebhookController],
      providers: [
        { provide: LedgerService, useValue: mockLedgerService },
        { provide: PAYMENT_PROVIDER, useValue: mockPaymentProvider },
      ],
    }).compile();

    controller = module.get(PaymentsWebhookController);
  });

  const buildRequest = (body: unknown, rawBody?: Buffer) =>
    ({
      body,
      rawBody,
      headers: { 'x-signature': 'sig-value' },
    }) as any;

  it('rejette un webhook dont la signature est invalide, sans jamais appeler confirmMobilePayment', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(false);
    const request = buildRequest({ reference: 'MP-1', status: 'SUCCESS' }, Buffer.from('{}'));

    await expect(controller.handleKadryzaWebhook(request)).rejects.toBeInstanceOf(
      UnauthorizedException,
    );
    expect(mockLedgerService.confirmMobilePayment).not.toHaveBeenCalled();
  });

  it('délègue à LedgerService.confirmMobilePayment avec les champs mappés, signature valide', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({ outcome: 'credited' });
    const request = buildRequest(
      { reference: 'MP-1', status: 'SUCCESS' },
      Buffer.from('{"reference":"MP-1","status":"SUCCESS"}'),
    );

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockLedgerService.confirmMobilePayment).toHaveBeenCalledWith({
      providerReference: 'MP-1',
      status: 'SUCCESS',
      failureReason: undefined,
    });
    expect(result).toEqual({ received: true, outcome: 'credited' });
  });

  it('vérifie la signature sur les octets bruts (rawBody), pas sur le JSON reparsé', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({ outcome: 'not_found' });
    const rawBody = Buffer.from('{"reference":"MP-2","status":"FAILED","reason":"x"}');
    const request = buildRequest({ reference: 'MP-2', status: 'FAILED', reason: 'x' }, rawBody);

    await controller.handleKadryzaWebhook(request);

    expect(mockPaymentProvider.verifyWebhookSignature).toHaveBeenCalledWith(
      rawBody,
      request.headers,
    );
  });

  it('reconstruit un rawBody de secours si absent (réponse toujours 200 implicite)', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({ outcome: 'already_processed' });
    const request = buildRequest({ reference: 'MP-3', status: 'SUCCESS' }, undefined);

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockPaymentProvider.verifyWebhookSignature).toHaveBeenCalledWith(
      expect.any(Buffer),
      request.headers,
    );
    expect(result).toEqual({ received: true, outcome: 'already_processed' });
  });
});
