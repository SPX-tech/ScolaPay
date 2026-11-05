import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException, UnauthorizedException } from '@nestjs/common';
import { PaymentsWebhookController } from './payments-webhook.controller';
import { LedgerService } from './ledger.service';
import { WithdrawalsService } from '../finance/withdrawals.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';

describe('PaymentsWebhookController', () => {
  let controller: PaymentsWebhookController;
  let mockLedgerService: { confirmMobilePayment: jest.Mock };
  let mockWithdrawalsService: { confirmPayout: jest.Mock };
  let mockPaymentProvider: { verifyWebhookSignature: jest.Mock };

  beforeEach(async () => {
    mockLedgerService = { confirmMobilePayment: jest.fn() };
    mockWithdrawalsService = { confirmPayout: jest.fn() };
    mockPaymentProvider = { verifyWebhookSignature: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      controllers: [PaymentsWebhookController],
      providers: [
        { provide: LedgerService, useValue: mockLedgerService },
        { provide: WithdrawalsService, useValue: mockWithdrawalsService },
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
    const request = buildRequest(
      { type: 'PAYMENT', reference: 'MP-1', status: 'SUCCESS' },
      Buffer.from('{}'),
    );

    await expect(
      controller.handleKadryzaWebhook(request),
    ).rejects.toBeInstanceOf(UnauthorizedException);
    expect(mockLedgerService.confirmMobilePayment).not.toHaveBeenCalled();
    expect(mockWithdrawalsService.confirmPayout).not.toHaveBeenCalled();
  });

  it('délègue à LedgerService.confirmMobilePayment avec les champs mappés, signature valide', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({
      outcome: 'credited',
    });
    const request = buildRequest(
      { type: 'PAYMENT', reference: 'MP-1', status: 'SUCCESS' },
      Buffer.from('{"type":"PAYMENT","reference":"MP-1","status":"SUCCESS"}'),
    );

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockLedgerService.confirmMobilePayment).toHaveBeenCalledWith({
      providerReference: 'MP-1',
      status: 'SUCCESS',
      failureReason: undefined,
    });
    expect(mockWithdrawalsService.confirmPayout).not.toHaveBeenCalled();
    expect(result).toEqual({ received: true, outcome: 'credited' });
  });

  it('vérifie la signature sur les octets bruts (rawBody), pas sur le JSON reparsé', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({
      outcome: 'not_found',
    });
    const rawBody = Buffer.from(
      '{"type":"PAYMENT","reference":"MP-2","status":"FAILED","reason":"x"}',
    );
    const request = buildRequest(
      { type: 'PAYMENT', reference: 'MP-2', status: 'FAILED', reason: 'x' },
      rawBody,
    );

    await controller.handleKadryzaWebhook(request);

    expect(mockPaymentProvider.verifyWebhookSignature).toHaveBeenCalledWith(
      rawBody,
      request.headers,
    );
  });

  it('reconstruit un rawBody de secours si absent (réponse toujours 200 implicite)', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockLedgerService.confirmMobilePayment.mockResolvedValue({
      outcome: 'already_processed',
    });
    const request = buildRequest(
      { type: 'PAYMENT', reference: 'MP-3', status: 'SUCCESS' },
      undefined,
    );

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockPaymentProvider.verifyWebhookSignature).toHaveBeenCalledWith(
      expect.any(Buffer),
      request.headers,
    );
    expect(result).toEqual({ received: true, outcome: 'already_processed' });
  });

  it('délègue à WithdrawalsService.confirmPayout pour un événement PAYOUT, avec les champs mappés', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockWithdrawalsService.confirmPayout.mockResolvedValue({
      outcome: 'completed',
    });
    const request = buildRequest(
      {
        type: 'PAYOUT',
        reference: 'WD-1',
        status: 'SUCCESS',
        providerTransactionId: 'KZ-PAYOUT-1',
      },
      Buffer.from('{}'),
    );

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockWithdrawalsService.confirmPayout).toHaveBeenCalledWith({
      reference: 'WD-1',
      status: 'SUCCESS',
      providerTransactionId: 'KZ-PAYOUT-1',
      failureReason: undefined,
    });
    expect(mockLedgerService.confirmMobilePayment).not.toHaveBeenCalled();
    expect(result).toEqual({ received: true, outcome: 'completed' });
  });

  it('délègue un événement PAYOUT en échec avec le motif mappé', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);
    mockWithdrawalsService.confirmPayout.mockResolvedValue({
      outcome: 'failed',
    });
    const request = buildRequest(
      {
        type: 'PAYOUT',
        reference: 'WD-2',
        status: 'FAILED',
        reason: 'RIB invalide',
      },
      Buffer.from('{}'),
    );

    const result = await controller.handleKadryzaWebhook(request);

    expect(mockWithdrawalsService.confirmPayout).toHaveBeenCalledWith({
      reference: 'WD-2',
      status: 'FAILED',
      providerTransactionId: undefined,
      failureReason: 'RIB invalide',
    });
    expect(result).toEqual({ received: true, outcome: 'failed' });
  });

  it.each([
    [
      'un statut inconnu',
      { type: 'PAYMENT', reference: 'MP-1', status: 'PENDING' },
    ],
    ['un statut absent', { type: 'PAYMENT', reference: 'MP-1' }],
    [
      'un statut en minuscules',
      { type: 'PAYMENT', reference: 'MP-1', status: 'success' },
    ],
    ['un type absent', { reference: 'MP-1', status: 'SUCCESS' }],
    [
      'un type inconnu',
      { type: 'REFUND', reference: 'MP-1', status: 'SUCCESS' },
    ],
    ['une référence absente', { type: 'PAYMENT', status: 'SUCCESS' }],
    [
      'une référence non textuelle',
      { type: 'PAYMENT', reference: { $ne: '' }, status: 'SUCCESS' },
    ],
    [
      'une référence surdimensionnée',
      { type: 'PAYMENT', reference: 'x'.repeat(129), status: 'SUCCESS' },
    ],
    [
      'un motif non textuel',
      { type: 'PAYMENT', reference: 'MP-1', status: 'FAILED', reason: 42 },
    ],
    ['un corps vide', undefined],
  ])('rejette %s avant toute mutation', async (_label, body) => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(true);

    await expect(
      controller.handleKadryzaWebhook(buildRequest(body, Buffer.from('{}'))),
    ).rejects.toBeInstanceOf(BadRequestException);

    expect(mockLedgerService.confirmMobilePayment).not.toHaveBeenCalled();
    expect(mockWithdrawalsService.confirmPayout).not.toHaveBeenCalled();
  });

  it('vérifie la signature avant de valider le contenu', async () => {
    mockPaymentProvider.verifyWebhookSignature.mockReturnValue(false);

    await expect(
      controller.handleKadryzaWebhook(
        buildRequest({ status: 'PENDING' }, Buffer.from('{}')),
      ),
    ).rejects.toBeInstanceOf(UnauthorizedException);
  });
});
