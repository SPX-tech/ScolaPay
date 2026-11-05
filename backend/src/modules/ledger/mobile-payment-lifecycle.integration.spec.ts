import { Test, TestingModule } from '@nestjs/testing';
import { LedgerService } from './ledger.service';
import { ReceiptService } from './receipt.service';
import { VirtualAccountService } from '../finance/virtual-account.service';
import { MockPaymentProvider } from '../payment/mock-payment.provider';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import { PrismaService } from '../../prisma/prisma.service';
import { OutboxService } from '../outbox/outbox.service';
import { AuditService } from '../audit/audit.service';

/**
 * Test d'intégration inter-services : LedgerService, VirtualAccountService
 * et MockPaymentProvider collaborent réellement (aucun n'est mocké) à
 * travers le cycle de vie complet initiation -> webhook -> solde élève ->
 * Ledger -> notification. Seule la frontière Prisma est simulée en
 * mémoire : aucune base de données réelle n'est disponible dans cet
 * environnement (voir les autres specs unitaires pour la même raison).
 */
describe('Cycle de vie du paiement mobile (intégration inter-services)', () => {
  let ledgerService: LedgerService;
  let mockOutboxService: { createEvent: jest.Mock };
  let mockAuditService: { record: jest.Mock };

  const TENANT = 'tenant_1';
  const USER = 'user_1';
  const STUDENT_ID = 'student_1_00000000000000';
  const tenant = { id: TENANT, code: 'ECOLE1', currency: 'XAF' };

  let studentRow: any;
  let transactionRow: any;
  let virtualAccountRow: any;
  let ledgerEntries: any[];

  let mockTx: any;
  let mockPrisma: any;

  beforeEach(async () => {
    studentRow = {
      id: STUDENT_ID,
      tenantId: TENANT,
      fullName: 'Jean Dupont',
      studentNumber: 'ETU001',
      guardianPhone: '+23599110001',
      guardianName: 'Pierre Dupont',
      debtCents: 10_000n,
      creditCents: 0n,
      status: 'ACTIVE',
    };
    virtualAccountRow = {
      id: 'va_1',
      tenantId: TENANT,
      balanceCents: 0n,
      pendingWithdrawalCents: 0n,
      currency: 'XAF',
    };
    transactionRow = null;
    ledgerEntries = [];

    const applyMoneyOp = (row: any, field: string, data: any) => {
      if (data?.[field]?.increment !== undefined)
        row[field] += data[field].increment;
      if (data?.[field]?.decrement !== undefined)
        row[field] -= data[field].decrement;
    };

    mockTx = {
      transaction: {
        updateMany: jest.fn((args: any) => {
          if (
            transactionRow &&
            transactionRow.id === args.where.id &&
            transactionRow.status === args.where.status
          ) {
            Object.assign(transactionRow, args.data);
            return Promise.resolve({ count: 1 });
          }
          return Promise.resolve({ count: 0 });
        }),
      },
      student: {
        findUniqueOrThrow: jest.fn(() => Promise.resolve({ ...studentRow })),
        update: jest.fn((args: any) => {
          Object.assign(studentRow, args.data);
          return Promise.resolve({ ...studentRow });
        }),
      },
      virtualAccount: {
        upsert: jest.fn(() => Promise.resolve({ ...virtualAccountRow })),
        update: jest.fn((args: any) => {
          applyMoneyOp(virtualAccountRow, 'balanceCents', args.data);
          applyMoneyOp(virtualAccountRow, 'pendingWithdrawalCents', args.data);
          return Promise.resolve({ ...virtualAccountRow });
        }),
      },
      ledgerEntry: {
        create: jest.fn((args: any) => {
          const entry = { id: `le_${ledgerEntries.length + 1}`, ...args.data };
          ledgerEntries.push(entry);
          return Promise.resolve(entry);
        }),
      },
      $queryRaw: jest.fn(() =>
        Promise.resolve([
          {
            id: virtualAccountRow.id,
            balanceCents: virtualAccountRow.balanceCents,
            pendingWithdrawalCents: virtualAccountRow.pendingWithdrawalCents,
            currency: virtualAccountRow.currency,
          },
        ]),
      ),
      $executeRaw: jest.fn(() => Promise.resolve(0)),
    };

    mockPrisma = {
      tenant: {
        findUnique: jest.fn(() => Promise.resolve(tenant)),
        findUniqueOrThrow: jest.fn(() => Promise.resolve(tenant)),
      },
      student: {
        findFirst: jest.fn(() => Promise.resolve({ ...studentRow })),
      },
      transaction: {
        create: jest.fn((args: any) => {
          transactionRow = {
            id: 'txn_1',
            recordedAt: new Date(),
            ...args.data,
          };
          return Promise.resolve({ ...transactionRow });
        }),
        findUnique: jest.fn((args: any) => {
          if (
            transactionRow &&
            transactionRow.providerReference === args.where.providerReference
          ) {
            return Promise.resolve({ ...transactionRow });
          }
          return Promise.resolve(null);
        }),
        updateMany: jest.fn((args: any) => {
          if (
            transactionRow &&
            transactionRow.id === args.where.id &&
            transactionRow.status === args.where.status
          ) {
            Object.assign(transactionRow, args.data);
            return Promise.resolve({ count: 1 });
          }
          return Promise.resolve({ count: 0 });
        }),
      },
      $transaction: jest.fn((fn: (tx: unknown) => unknown) => fn(mockTx)),
    };

    mockOutboxService = { createEvent: jest.fn() };
    mockAuditService = { record: jest.fn() };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        LedgerService,
        VirtualAccountService,
        ReceiptService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: OutboxService, useValue: mockOutboxService },
        { provide: AuditService, useValue: mockAuditService },
        { provide: PAYMENT_PROVIDER, useClass: MockPaymentProvider },
      ],
    }).compile();

    ledgerService = module.get(LedgerService);
  });

  it('encaisse un paiement mobile de bout en bout : initiation -> webhook -> solde -> Ledger -> notification', async () => {
    const initResult = await ledgerService.initializeMobilePayment(
      TENANT,
      USER,
      'CASHIER',
      {
        studentId: STUDENT_ID,
        amountCents: '4000',
        payerPhone: '+23566000000',
      },
    );

    expect(initResult.status).toBe('PENDING');
    expect(transactionRow.status).toBe('PENDING');
    expect(virtualAccountRow.balanceCents).toBe(0n); // rien n'est crédité avant confirmation

    const confirmResult = await ledgerService.confirmMobilePayment({
      providerReference: initResult.reference,
      status: 'SUCCESS',
    });

    expect(confirmResult).toEqual({ outcome: 'credited' });
    expect(transactionRow.status).toBe('POSTED');
    expect(studentRow.debtCents).toBe(6_000n); // 10 000 - 4 000
    expect(virtualAccountRow.balanceCents).toBe(4_000n); // crédité par VirtualAccountService
    expect(ledgerEntries).toHaveLength(1);
    expect(ledgerEntries[0]).toEqual(
      expect.objectContaining({
        type: 'PAYMENT',
        amountCents: 4_000n,
        reference: transactionRow.receiptNumber,
        relatedTransactionId: 'txn_1',
      }),
    );
    expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'mobile-payment.confirmed' }),
    );
    expect(mockAuditService.record).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'transaction.mobile_payment.confirm' }),
      mockTx,
    );

    // Rejeu du webhook (retry réseau côté prestataire) : ne crédite jamais deux fois.
    const replayResult = await ledgerService.confirmMobilePayment({
      providerReference: initResult.reference,
      status: 'SUCCESS',
    });

    expect(replayResult).toEqual({ outcome: 'already_processed' });
    expect(virtualAccountRow.balanceCents).toBe(4_000n);
    expect(studentRow.debtCents).toBe(6_000n);
    expect(ledgerEntries).toHaveLength(1);
    expect(mockOutboxService.createEvent).toHaveBeenCalledTimes(1);
  });

  it('gère un paiement refusé de bout en bout : aucun crédit, notification d’échec', async () => {
    const initResult = await ledgerService.initializeMobilePayment(
      TENANT,
      USER,
      'CASHIER',
      {
        studentId: STUDENT_ID,
        amountCents: '2000',
        payerPhone: '+23566000000',
      },
    );

    const confirmResult = await ledgerService.confirmMobilePayment({
      providerReference: initResult.reference,
      status: 'FAILED',
      failureReason: 'Solde mobile money insuffisant',
    });

    expect(confirmResult).toEqual({ outcome: 'failed' });
    expect(transactionRow.status).toBe('FAILED');
    expect(studentRow.debtCents).toBe(10_000n); // inchangé
    expect(virtualAccountRow.balanceCents).toBe(0n); // inchangé
    expect(ledgerEntries).toHaveLength(0);
    expect(mockOutboxService.createEvent).toHaveBeenCalledWith(
      expect.objectContaining({ eventType: 'mobile-payment.failed' }),
    );
  });
});
