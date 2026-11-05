import { Test, TestingModule } from '@nestjs/testing';
import {
  BadRequestException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { LedgerService } from './ledger.service';
import { PrismaService } from '../../prisma/prisma.service';
import { OutboxService } from '../outbox/outbox.service';
import { ReceiptService } from './receipt.service';
import { AuditService } from '../audit/audit.service';
import { VirtualAccountService } from '../finance/virtual-account.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import { InitializeMobilePaymentDto } from './dto/initialize-mobile-payment.dto';

describe('LedgerService (paiements mobiles)', () => {
  let service: LedgerService;

  const TENANT = 'tenant_1';
  const USER = 'user_1';
  const STUDENT_ID = 'student_1_00000000000000';

  let mockTx: {
    student: { findUniqueOrThrow: jest.Mock; update: jest.Mock };
    transaction: { updateMany: jest.Mock };
  };
  let mockPrisma: {
    tenant: { findUnique: jest.Mock; findUniqueOrThrow: jest.Mock };
    student: { findFirst: jest.Mock };
    transaction: {
      create: jest.Mock;
      findUnique: jest.Mock;
      updateMany: jest.Mock;
    };
    $transaction: jest.Mock;
  };
  let mockOutboxService: { createEvent: jest.Mock };
  let mockReceiptService: {
    generateReceiptNumber: jest.Mock;
    generateReceiptHash: jest.Mock;
    generateReceiptData: jest.Mock;
  };
  let mockAuditService: { record: jest.Mock };
  let mockVirtualAccountService: { creditFromPayment: jest.Mock };
  let mockPaymentProvider: {
    collect: jest.Mock;
    verifyWebhookSignature: jest.Mock;
  };

  const tenant = { id: TENANT, code: 'ECOLE1', currency: 'XAF' };
  const student = (overrides: Record<string, unknown> = {}) => ({
    id: STUDENT_ID,
    tenantId: TENANT,
    fullName: 'Jean Dupont',
    studentNumber: 'ETU001',
    guardianPhone: '+23599110001',
    guardianName: 'Pierre Dupont',
    debtCents: 10_000n,
    creditCents: 0n,
    status: 'ACTIVE',
    ...overrides,
  });

  const pendingTransaction = (overrides: Record<string, unknown> = {}) => ({
    id: 'txn_1',
    tenantId: TENANT,
    studentId: STUDENT_ID,
    recordedByUserId: USER,
    type: 'MOBILE_PAYMENT',
    status: 'PENDING',
    amountCents: 5_000n,
    previousDebtCents: 10_000n,
    newDebtCents: 10_000n,
    currency: 'XAF',
    providerReference: 'MP-abc',
    payerPhone: '+23566000000',
    receiptNumber: null,
    receiptHash: null,
    qrCodeData: null,
    recordedAt: new Date('2026-07-01T10:00:00.000Z'),
    ...overrides,
  });

  beforeEach(async () => {
    mockTx = {
      student: { findUniqueOrThrow: jest.fn(), update: jest.fn() },
      transaction: { updateMany: jest.fn() },
    };
    mockPrisma = {
      tenant: { findUnique: jest.fn(), findUniqueOrThrow: jest.fn() },
      student: { findFirst: jest.fn() },
      transaction: {
        create: jest.fn(),
        findUnique: jest.fn(),
        updateMany: jest.fn(),
      },
      $transaction: jest.fn(),
    };
    mockOutboxService = { createEvent: jest.fn() };
    mockReceiptService = {
      generateReceiptNumber: jest
        .fn()
        .mockReturnValue('RCP-ECOLE1-20260701-ABCD1234'),
      generateReceiptHash: jest.fn().mockReturnValue('hash123'),
      generateReceiptData: jest.fn().mockReturnValue('{"qr":"data"}'),
    };
    mockAuditService = { record: jest.fn() };
    mockVirtualAccountService = { creditFromPayment: jest.fn() };
    mockPaymentProvider = {
      collect: jest.fn(),
      verifyWebhookSignature: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        LedgerService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: OutboxService, useValue: mockOutboxService },
        { provide: ReceiptService, useValue: mockReceiptService },
        { provide: AuditService, useValue: mockAuditService },
        { provide: VirtualAccountService, useValue: mockVirtualAccountService },
        { provide: PAYMENT_PROVIDER, useValue: mockPaymentProvider },
      ],
    }).compile();

    service = module.get(LedgerService);
    jest.clearAllMocks();
    mockPrisma.$transaction.mockImplementation((fn: (tx: unknown) => unknown) =>
      fn(mockTx),
    );
  });

  describe('initializeMobilePayment', () => {
    const dto: InitializeMobilePaymentDto = {
      studentId: STUDENT_ID,
      amountCents: '5000',
      payerPhone: '+23566000000',
    };

    it('crée une transaction PENDING puis déclenche la collecte côté prestataire', async () => {
      mockPrisma.tenant.findUnique.mockResolvedValue(tenant);
      mockPrisma.student.findFirst.mockResolvedValue(student());
      mockPrisma.transaction.create.mockResolvedValue(pendingTransaction());
      mockPaymentProvider.collect.mockResolvedValue({
        status: 'PENDING',
        providerTransactionId: 'MOCK-1',
      });

      const result = await service.initializeMobilePayment(
        TENANT,
        USER,
        'CASHIER',
        dto,
      );

      expect(mockPrisma.transaction.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          tenantId: TENANT,
          studentId: STUDENT_ID,
          type: 'MOBILE_PAYMENT',
          status: 'PENDING',
          amountCents: 5_000n,
          payerPhone: dto.payerPhone,
        }),
      });
      expect(mockPaymentProvider.collect).toHaveBeenCalledWith(
        expect.objectContaining({
          amountCents: 5_000n,
          currency: 'XAF',
          payerPhone: dto.payerPhone,
          tenantId: TENANT,
          studentName: 'Jean Dupont',
        }),
      );
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'transaction.mobile_payment.initiate',
        }),
      );
      expect(result.status).toBe('PENDING');
      expect(result.reference).toEqual(expect.any(String));
    });

    it('rejette les rôles non autorisés', async () => {
      await expect(
        service.initializeMobilePayment(TENANT, USER, 'AUDITOR', dto),
      ).rejects.toBeInstanceOf(ForbiddenException);
      expect(mockPrisma.transaction.create).not.toHaveBeenCalled();
    });

    it('rejette si aucun élève actif ne correspond', async () => {
      mockPrisma.tenant.findUnique.mockResolvedValue(tenant);
      mockPrisma.student.findFirst.mockResolvedValue(null);

      await expect(
        service.initializeMobilePayment(TENANT, USER, 'CASHIER', dto),
      ).rejects.toBeInstanceOf(NotFoundException);
    });

    it('bascule la transaction en FAILED si le prestataire refuse la collecte', async () => {
      mockPrisma.tenant.findUnique.mockResolvedValue(tenant);
      mockPrisma.student.findFirst.mockResolvedValue(student());
      mockPrisma.transaction.create.mockResolvedValue(pendingTransaction());
      mockPaymentProvider.collect.mockResolvedValue({
        status: 'FAILED',
        failureReason: 'Numéro invalide',
      });

      await expect(
        service.initializeMobilePayment(TENANT, USER, 'CASHIER', dto),
      ).rejects.toBeInstanceOf(BadRequestException);

      expect(mockPrisma.transaction.updateMany).toHaveBeenCalledWith({
        where: {
          id: pendingTransaction().id,
          tenantId: TENANT,
          status: 'PENDING',
        },
        data: { status: 'FAILED', note: 'Numéro invalide' },
      });
      expect(mockAuditService.record).not.toHaveBeenCalled();
    });

    it('bascule la transaction en FAILED si l’appel au prestataire lève une exception', async () => {
      mockPrisma.tenant.findUnique.mockResolvedValue(tenant);
      mockPrisma.student.findFirst.mockResolvedValue(student());
      mockPrisma.transaction.create.mockResolvedValue(pendingTransaction());
      mockPaymentProvider.collect.mockRejectedValue(new Error('network down'));

      await expect(
        service.initializeMobilePayment(TENANT, USER, 'CASHIER', dto),
      ).rejects.toBeInstanceOf(BadRequestException);

      expect(mockPrisma.transaction.updateMany).toHaveBeenCalledWith({
        where: {
          id: pendingTransaction().id,
          tenantId: TENANT,
          status: 'PENDING',
        },
        data: { status: 'FAILED', note: 'network down' },
      });
    });
  });

  describe('confirmMobilePayment', () => {
    it('retourne not_found si aucune transaction ne correspond à la référence', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(null);

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-unknown',
        status: 'SUCCESS',
      });

      expect(result).toEqual({ outcome: 'not_found' });
      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
    });

    it('est idempotent : un rejeu sur une transaction déjà POSTED ne crédite pas deux fois', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(
        pendingTransaction({ status: 'POSTED' }),
      );

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'SUCCESS',
      });

      expect(result).toEqual({ outcome: 'already_processed' });
      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
      expect(
        mockVirtualAccountService.creditFromPayment,
      ).not.toHaveBeenCalled();
    });

    it('confirme un paiement réussi : crédite le compte virtuel, écrit le Ledger, notifie et audite', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(pendingTransaction());
      mockPrisma.tenant.findUniqueOrThrow.mockResolvedValue(tenant);
      mockTx.student.findUniqueOrThrow.mockResolvedValue(student());
      mockTx.transaction.updateMany.mockResolvedValue({ count: 1 });
      mockTx.student.update.mockResolvedValue({});
      mockVirtualAccountService.creditFromPayment.mockResolvedValue({});

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'SUCCESS',
      });

      expect(mockTx.transaction.updateMany).toHaveBeenCalledWith({
        where: { id: 'txn_1', tenantId: TENANT, status: 'PENDING' },
        data: expect.objectContaining({
          status: 'POSTED',
          previousDebtCents: 10_000n,
          newDebtCents: 5_000n,
          receiptNumber: 'RCP-ECOLE1-20260701-ABCD1234',
        }),
      });
      expect(mockTx.student.update).toHaveBeenCalledWith({
        where: { id: STUDENT_ID },
        data: expect.objectContaining({ debtCents: 5_000n, creditCents: 0n }),
      });
      expect(mockVirtualAccountService.creditFromPayment).toHaveBeenCalledWith(
        TENANT,
        5_000n,
        'RCP-ECOLE1-20260701-ABCD1234',
        mockTx,
        { relatedTransactionId: 'txn_1' },
      );
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          tx: mockTx,
          eventType: 'mobile-payment.confirmed',
          payload: expect.objectContaining({
            amountCents: '5000',
            receiptNumber: 'RCP-ECOLE1-20260701-ABCD1234',
          }),
        }),
      );
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'transaction.mobile_payment.confirm',
        }),
        mockTx,
      );
      expect(result).toEqual({ outcome: 'credited' });
    });

    it('applique le surplus au crédit élève en cas de trop-perçu', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(
        pendingTransaction({
          amountCents: 15_000n,
          previousDebtCents: 10_000n,
          newDebtCents: 10_000n,
        }),
      );
      mockPrisma.tenant.findUniqueOrThrow.mockResolvedValue(tenant);
      mockTx.student.findUniqueOrThrow.mockResolvedValue(
        student({ debtCents: 10_000n, creditCents: 0n }),
      );
      mockTx.transaction.updateMany.mockResolvedValue({ count: 1 });
      mockTx.student.update.mockResolvedValue({});
      mockVirtualAccountService.creditFromPayment.mockResolvedValue({});

      await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'SUCCESS',
      });

      // Paiement de 15 000 pour une dette de 10 000 => dette ramenée à 0, surplus de 5 000 en crédit.
      expect(mockTx.student.update).toHaveBeenCalledWith({
        where: { id: STUDENT_ID },
        data: expect.objectContaining({ debtCents: 0n, creditCents: 5_000n }),
      });
    });

    it('ne crédite jamais deux fois lors d’un rejeu concurrent (idempotence dans la transaction)', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(pendingTransaction());
      mockPrisma.tenant.findUniqueOrThrow.mockResolvedValue(tenant);
      mockTx.student.findUniqueOrThrow.mockResolvedValue(student());
      // Un autre appel a déjà gagné la course : le updateMany gardé par statut ne matche plus rien.
      mockTx.transaction.updateMany.mockResolvedValue({ count: 0 });

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'SUCCESS',
      });

      expect(mockTx.student.update).not.toHaveBeenCalled();
      expect(
        mockVirtualAccountService.creditFromPayment,
      ).not.toHaveBeenCalled();
      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
      expect(result).toEqual({ outcome: 'already_processed' });
    });

    it('gère un paiement refusé : bascule en FAILED, notifie, audite, ne crédite rien', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(pendingTransaction());
      mockTx.transaction.updateMany.mockResolvedValue({ count: 1 });

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'FAILED',
        failureReason: 'Solde insuffisant',
      });

      expect(mockTx.transaction.updateMany).toHaveBeenCalledWith({
        where: { id: 'txn_1', tenantId: TENANT, status: 'PENDING' },
        data: { status: 'FAILED', note: 'Solde insuffisant' },
      });
      expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
        expect.objectContaining({ eventType: 'mobile-payment.failed' }),
      );
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({ action: 'transaction.mobile_payment.fail' }),
        mockTx,
      );
      expect(
        mockVirtualAccountService.creditFromPayment,
      ).not.toHaveBeenCalled();
      expect(result).toEqual({ outcome: 'failed' });
    });

    it('rejeu concurrent sur le chemin échec : ne notifie pas deux fois', async () => {
      mockPrisma.transaction.findUnique.mockResolvedValue(pendingTransaction());
      mockTx.transaction.updateMany.mockResolvedValue({ count: 0 });

      const result = await service.confirmMobilePayment({
        providerReference: 'MP-abc',
        status: 'FAILED',
      });

      expect(mockOutboxService.createEvent).not.toHaveBeenCalled();
      expect(mockAuditService.record).not.toHaveBeenCalled();
      expect(result).toEqual({ outcome: 'already_processed' });
    });
  });
});
