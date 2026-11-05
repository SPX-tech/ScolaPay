import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException, ConflictException, NotFoundException } from '@nestjs/common';
import { WithdrawalStatus } from '../../generated/prisma/client';
import { WithdrawalsService } from './withdrawals.service';
import { PrismaService } from '../../prisma/prisma.service';
import { VirtualAccountService } from './virtual-account.service';
import { AuditService } from '../audit/audit.service';
import { OutboxService } from '../outbox/outbox.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import { CreateWithdrawalDto } from './dto/create-withdrawal.dto';

describe('WithdrawalsService', () => {
  let service: WithdrawalsService;

  const TENANT = 'tenant_1';
  const OTHER_TENANT = 'tenant_2';
  const USER = 'user_1';
  const WITHDRAWAL_ID = 'wd_1';

  let mockTx: {
    withdrawalRequest: {
      create: jest.Mock;
      updateMany: jest.Mock;
      findFirst: jest.Mock;
      findFirstOrThrow: jest.Mock;
    };
  };
  let mockPrisma: {
    $transaction: jest.Mock;
    withdrawalRequest: { findUnique: jest.Mock; findMany: jest.Mock; count: jest.Mock };
  };
  let mockVirtualAccount: {
    reserveForWithdrawal: jest.Mock;
    completeWithdrawal: jest.Mock;
    releaseWithdrawal: jest.Mock;
  };
  let mockAuditService: { record: jest.Mock };
  let mockOutboxService: { createEvent: jest.Mock };
  let mockPaymentProvider: {
    collect: jest.Mock;
    payout: jest.Mock;
    verifyWebhookSignature: jest.Mock;
  };

  const requester = { id: USER, fullName: 'Jean Testeur', phone: '+23566000000' };

  const withdrawalRow = (overrides: Record<string, unknown> = {}) => ({
    id: WITHDRAWAL_ID,
    tenantId: TENANT,
    amountCents: 5_000n,
    method: 'MOBILE_MONEY',
    destination: '+23566000000',
    status: WithdrawalStatus.PENDING,
    reference: 'WD-abc',
    externalReference: null,
    failureReason: null,
    requestedById: USER,
    requestedBy: requester,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  });

  beforeEach(async () => {
    mockTx = {
      withdrawalRequest: {
        create: jest.fn(),
        updateMany: jest.fn(),
        findFirst: jest.fn(),
        findFirstOrThrow: jest.fn(),
      },
    };
    mockPrisma = {
      $transaction: jest.fn(),
      withdrawalRequest: { findUnique: jest.fn(), findMany: jest.fn(), count: jest.fn() },
    };
    mockVirtualAccount = {
      reserveForWithdrawal: jest.fn(),
      completeWithdrawal: jest.fn(),
      releaseWithdrawal: jest.fn(),
    };
    mockAuditService = { record: jest.fn() };
    mockOutboxService = { createEvent: jest.fn() };
    mockPaymentProvider = { collect: jest.fn(), payout: jest.fn(), verifyWebhookSignature: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        WithdrawalsService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: VirtualAccountService, useValue: mockVirtualAccount },
        { provide: AuditService, useValue: mockAuditService },
        { provide: OutboxService, useValue: mockOutboxService },
        { provide: PAYMENT_PROVIDER, useValue: mockPaymentProvider },
      ],
    }).compile();

    service = module.get(WithdrawalsService);
    jest.clearAllMocks();
    // Simule prisma.$transaction(async (tx) => ...) en rejouant tout de
    // suite le callback avec le même mockTx (pas de vraie base en jeu).
    mockPrisma.$transaction.mockImplementation((fn: (tx: unknown) => unknown) => fn(mockTx));
  });

  describe('initiate', () => {
    const dto: CreateWithdrawalDto = {
      amountCents: 5_000,
      method: 'MOBILE_MONEY' as CreateWithdrawalDto['method'],
      destination: '+23566000000',
    };

    it('bloque les fonds puis crée la demande en PENDING, dans la même transaction', async () => {
      mockVirtualAccount.reserveForWithdrawal.mockResolvedValue({});
      const created = withdrawalRow();
      mockTx.withdrawalRequest.create.mockResolvedValue(created);

      const result = await service.initiate(TENANT, USER, dto);

      expect(mockVirtualAccount.reserveForWithdrawal).toHaveBeenCalledWith(TENANT, 5_000n, mockTx);
      expect(mockTx.withdrawalRequest.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          tenantId: TENANT,
          amountCents: 5_000n,
          method: dto.method,
          destination: dto.destination,
          requestedById: USER,
        }),
      });
      expect(result).toEqual(created);
    });

    it("ne crée pas la demande si le blocage des fonds échoue (pas de demande sans blocage)", async () => {
      mockVirtualAccount.reserveForWithdrawal.mockRejectedValue(new Error('Solde insuffisant'));

      await expect(service.initiate(TENANT, USER, dto)).rejects.toThrow('Solde insuffisant');
      expect(mockTx.withdrawalRequest.create).not.toHaveBeenCalled();
    });
  });

  describe('list', () => {
    it('retourne les demandes paginées, plus récentes en premier, scopées au tenant', async () => {
      const row = withdrawalRow();
      mockPrisma.withdrawalRequest.findMany.mockResolvedValue([row]);
      mockPrisma.withdrawalRequest.count.mockResolvedValue(1);

      const result = await service.list(TENANT, { page: 1, limit: 20 });

      expect(mockPrisma.withdrawalRequest.findMany).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        orderBy: { createdAt: 'desc' },
        skip: 0,
        take: 20,
        include: { requestedBy: { select: { id: true, fullName: true, phone: true } } },
      });
      expect(mockPrisma.withdrawalRequest.count).toHaveBeenCalledWith({ where: { tenantId: TENANT } });
      expect(result).toEqual({
        data: [row],
        meta: { total: 1, page: 1, limit: 20, totalPages: 1 },
      });
    });

    it('calcule skip/totalPages pour une page suivante', async () => {
      mockPrisma.withdrawalRequest.findMany.mockResolvedValue([]);
      mockPrisma.withdrawalRequest.count.mockResolvedValue(45);

      const result = await service.list(TENANT, { page: 3, limit: 20 });

      expect(mockPrisma.withdrawalRequest.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ skip: 40, take: 20 }),
      );
      expect(result.meta).toEqual({ total: 45, page: 3, limit: 20, totalPages: 3 });
    });

    it('applique les valeurs par défaut page=1/limit=50 si omises', async () => {
      mockPrisma.withdrawalRequest.findMany.mockResolvedValue([]);
      mockPrisma.withdrawalRequest.count.mockResolvedValue(0);

      await service.list(TENANT, {});

      expect(mockPrisma.withdrawalRequest.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ skip: 0, take: 50 }),
      );
    });
  });

  describe('process (PENDING -> PROCESSING)', () => {
    it('transitionne, journalise l’audit, puis déclenche le virement hors transaction', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      mockPaymentProvider.payout.mockResolvedValue({
        status: 'PROCESSING',
        providerTransactionId: 'MOCK-PAYOUT-1',
      });

      const result = await service.process(TENANT, USER, WITHDRAWAL_ID);

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PENDING },
        data: { status: WithdrawalStatus.PROCESSING },
      });
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          tenantId: TENANT,
          actorUserId: USER,
          action: 'withdrawal.process',
          targetType: 'withdrawal',
          targetId: WITHDRAWAL_ID,
        }),
        mockTx,
      );
      expect(mockPaymentProvider.payout).toHaveBeenCalledWith({
        amountCents: 5_000n,
        destination: '+23566000000',
        method: 'MOBILE_MONEY',
        reference: 'WD-abc',
      });
      // Le virement est déclenché après que la transition PROCESSING a commité.
      const updateManyOrder = mockTx.withdrawalRequest.updateMany.mock.invocationCallOrder[0];
      const payoutOrder = mockPaymentProvider.payout.mock.invocationCallOrder[0];
      expect(updateManyOrder).toBeLessThan(payoutOrder);

      expect(mockVirtualAccount.reserveForWithdrawal).not.toHaveBeenCalled();
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
      expect(mockVirtualAccount.releaseWithdrawal).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
      expect(result.status).toBe(WithdrawalStatus.PROCESSING);
    });

    it('rejette (Conflict) et ne tente jamais le virement si la demande n’est plus PENDING (idempotence)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.PROCESSING });

      await expect(service.process(TENANT, USER, WITHDRAWAL_ID)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(mockAuditService.record).not.toHaveBeenCalled();
      expect(mockPaymentProvider.payout).not.toHaveBeenCalled();
    });

    it('rejette (NotFound) si la demande n’existe pas', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue(null);

      await expect(service.process(TENANT, USER, WITHDRAWAL_ID)).rejects.toBeInstanceOf(
        NotFoundException,
      );
      expect(mockPaymentProvider.payout).not.toHaveBeenCalled();
    });

    it("rejette (NotFound) si la demande appartient à un autre tenant (isolation multi-tenant)", async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue(null);

      await expect(
        service.process(OTHER_TENANT, USER, WITHDRAWAL_ID),
      ).rejects.toBeInstanceOf(NotFoundException);
      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: OTHER_TENANT, status: WithdrawalStatus.PENDING },
        data: { status: WithdrawalStatus.PROCESSING },
      });
    });

    it('bascule en FAILED et restitue les fonds si le prestataire lève une exception (rollback synchrone)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      mockPaymentProvider.payout.mockRejectedValue(new Error('network down'));
      mockVirtualAccount.releaseWithdrawal.mockResolvedValue({});

      await expect(service.process(TENANT, USER, WITHDRAWAL_ID)).rejects.toBeInstanceOf(
        BadRequestException,
      );

      // Le rollback réutilise fail() : même garde de transition, mêmes effets.
      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.FAILED, failureReason: expect.stringContaining('network down') },
      });
      expect(mockVirtualAccount.releaseWithdrawal).toHaveBeenCalledWith(TENANT, 5_000n, mockTx);
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: 'withdrawal.failed' }),
      );
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'withdrawal.fail' }),
        mockTx,
      );
    });

    it('bascule en FAILED et restitue les fonds si le prestataire refuse le virement (status FAILED)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      mockPaymentProvider.payout.mockResolvedValue({
        status: 'FAILED',
        failureReason: 'RIB invalide',
      });
      mockVirtualAccount.releaseWithdrawal.mockResolvedValue({});

      await expect(service.process(TENANT, USER, WITHDRAWAL_ID)).rejects.toBeInstanceOf(
        BadRequestException,
      );

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.FAILED, failureReason: 'RIB invalide' },
      });
      expect(mockVirtualAccount.releaseWithdrawal).toHaveBeenCalledWith(TENANT, 5_000n, mockTx);
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: 'withdrawal.failed' }),
      );
    });
  });

  describe('complete (PROCESSING -> COMPLETED)', () => {
    const dto = { externalReference: 'MM-EXT-123' };

    it('débite le compte virtuel, journalise l’audit et planifie la notification, dans une seule transaction', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.COMPLETED, externalReference: dto.externalReference }),
      );
      mockVirtualAccount.completeWithdrawal.mockResolvedValue({});

      const result = await service.complete(TENANT, USER, WITHDRAWAL_ID, dto);

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.COMPLETED, externalReference: dto.externalReference },
      });
      expect(mockVirtualAccount.completeWithdrawal).toHaveBeenCalledWith(
        TENANT,
        5_000n,
        'WD-abc',
        mockTx,
        { relatedWithdrawalId: WITHDRAWAL_ID },
      );
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'withdrawal.complete',
          targetType: 'withdrawal',
          targetId: WITHDRAWAL_ID,
        }),
        mockTx,
      );
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith({
        tx: mockTx,
        tenantId: TENANT,
        aggregateType: 'withdrawal',
        aggregateId: WITHDRAWAL_ID,
        eventType: 'withdrawal.completed',
        payload: expect.objectContaining({
          withdrawalId: WITHDRAWAL_ID,
          amountCents: '5000',
          externalReference: dto.externalReference,
          requesterPhone: requester.phone,
        }),
      });
      expect(result.status).toBe(WithdrawalStatus.COMPLETED);
    });

    it('acquiert le verrou de la demande avant celui du compte virtuel (ordre anti-deadlock)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.COMPLETED }),
      );
      mockVirtualAccount.completeWithdrawal.mockResolvedValue({});

      await service.complete(TENANT, USER, WITHDRAWAL_ID, dto);

      const updateManyOrder = mockTx.withdrawalRequest.updateMany.mock.invocationCallOrder[0];
      const completeOrder = mockVirtualAccount.completeWithdrawal.mock.invocationCallOrder[0];
      expect(updateManyOrder).toBeLessThan(completeOrder);
    });

    it('ne débite jamais deux fois : un second appel sur une demande déjà COMPLETED échoue (idempotence)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.COMPLETED });

      await expect(service.complete(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
      expect(mockAuditService.record).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
    });

    it('rejette (Conflict) une demande encore PENDING (jamais passée par process)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.PENDING });

      await expect(service.complete(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
    });

    it('rejette (NotFound) si la demande n’existe pas', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue(null);

      await expect(service.complete(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });

  describe('fail (PROCESSING -> FAILED)', () => {
    const dto = { reason: 'Numéro mobile money invalide' };

    it('libère le blocage, journalise l’audit et planifie la notification, dans une seule transaction', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.FAILED, failureReason: dto.reason }),
      );
      mockVirtualAccount.releaseWithdrawal.mockResolvedValue({});

      const result = await service.fail(TENANT, USER, WITHDRAWAL_ID, dto);

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.FAILED, failureReason: dto.reason },
      });
      expect(mockVirtualAccount.releaseWithdrawal).toHaveBeenCalledWith(TENANT, 5_000n, mockTx);
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'withdrawal.fail',
          targetType: 'withdrawal',
          targetId: WITHDRAWAL_ID,
        }),
        mockTx,
      );
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith({
        tx: mockTx,
        tenantId: TENANT,
        aggregateType: 'withdrawal',
        aggregateId: WITHDRAWAL_ID,
        eventType: 'withdrawal.failed',
        payload: expect.objectContaining({
          withdrawalId: WITHDRAWAL_ID,
          amountCents: '5000',
          reason: dto.reason,
          requesterPhone: requester.phone,
        }),
      });
      expect(result.status).toBe(WithdrawalStatus.FAILED);
    });

    it('ne libère jamais deux fois : un second appel sur une demande déjà FAILED échoue (idempotence)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.FAILED });

      await expect(service.fail(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(mockVirtualAccount.releaseWithdrawal).not.toHaveBeenCalled();
      expect(mockAuditService.record).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
    });

    it('rejette (Conflict) une demande déjà COMPLETED (transition terminale)', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.COMPLETED });

      await expect(service.fail(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        ConflictException,
      );
      expect(mockVirtualAccount.releaseWithdrawal).not.toHaveBeenCalled();
    });

    it('rejette (NotFound) si la demande n’existe pas', async () => {
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue(null);

      await expect(service.fail(TENANT, USER, WITHDRAWAL_ID, dto)).rejects.toBeInstanceOf(
        NotFoundException,
      );
    });
  });

  describe('confirmPayout (webhook du prestataire)', () => {
    it('retourne not_found si aucune demande ne correspond à la référence', async () => {
      mockPrisma.withdrawalRequest.findUnique.mockResolvedValue(null);

      const result = await service.confirmPayout({ reference: 'WD-unknown', status: 'SUCCESS' });

      expect(result).toEqual({ outcome: 'not_found' });
      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
    });

    it('est idempotent : ne retente rien si la demande n’est déjà plus PROCESSING', async () => {
      mockPrisma.withdrawalRequest.findUnique.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.COMPLETED }),
      );

      const result = await service.confirmPayout({ reference: 'WD-abc', status: 'SUCCESS' });

      expect(result).toEqual({ outcome: 'already_processed' });
      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
    });

    it('confirme un virement réussi : délègue à complete(), crédite et notifie', async () => {
      mockPrisma.withdrawalRequest.findUnique.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.COMPLETED, externalReference: 'KZ-PAYOUT-1' }),
      );
      mockVirtualAccount.completeWithdrawal.mockResolvedValue({});

      const result = await service.confirmPayout({
        reference: 'WD-abc',
        status: 'SUCCESS',
        providerTransactionId: 'KZ-PAYOUT-1',
      });

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.COMPLETED, externalReference: 'KZ-PAYOUT-1' },
      });
      expect(mockVirtualAccount.completeWithdrawal).toHaveBeenCalledWith(
        TENANT,
        5_000n,
        'WD-abc',
        mockTx,
        { relatedWithdrawalId: WITHDRAWAL_ID },
      );
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: 'withdrawal.completed' }),
      );
      expect(result).toEqual({ outcome: 'completed' });
    });

    it('confirme un virement refusé : délègue à fail(), libère les fonds et notifie', async () => {
      mockPrisma.withdrawalRequest.findUnique.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 1 });
      mockTx.withdrawalRequest.findFirstOrThrow.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.FAILED, failureReason: 'Compte bancaire clôturé' }),
      );
      mockVirtualAccount.releaseWithdrawal.mockResolvedValue({});

      const result = await service.confirmPayout({
        reference: 'WD-abc',
        status: 'FAILED',
        failureReason: 'Compte bancaire clôturé',
      });

      expect(mockTx.withdrawalRequest.updateMany).toHaveBeenCalledWith({
        where: { id: WITHDRAWAL_ID, tenantId: TENANT, status: WithdrawalStatus.PROCESSING },
        data: { status: WithdrawalStatus.FAILED, failureReason: 'Compte bancaire clôturé' },
      });
      expect(mockVirtualAccount.releaseWithdrawal).toHaveBeenCalledWith(TENANT, 5_000n, mockTx);
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: 'withdrawal.failed' }),
      );
      expect(result).toEqual({ outcome: 'failed' });
    });

    it('absorbe un rejeu concurrent (ConflictException de complete()) en already_processed', async () => {
      mockPrisma.withdrawalRequest.findUnique.mockResolvedValue(
        withdrawalRow({ status: WithdrawalStatus.PROCESSING }),
      );
      // Un autre appel a déjà gagné la course entre la pré-vérification et
      // la transition atomique : le updateMany gardé par statut ne matche
      // plus rien, et le diagnostic interne de transitionOrThrow constate
      // que la demande est déjà COMPLETED.
      mockTx.withdrawalRequest.updateMany.mockResolvedValue({ count: 0 });
      mockTx.withdrawalRequest.findFirst.mockResolvedValue({ status: WithdrawalStatus.COMPLETED });

      const result = await service.confirmPayout({ reference: 'WD-abc', status: 'SUCCESS' });

      expect(result).toEqual({ outcome: 'already_processed' });
      expect(mockVirtualAccount.completeWithdrawal).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
    });
  });
});
