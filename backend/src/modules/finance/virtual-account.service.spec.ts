import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException } from '@nestjs/common';
import { LedgerEntryType } from '@prisma/client';
import { VirtualAccountService } from './virtual-account.service';
import { PrismaService } from '../../prisma/prisma.service';

describe('VirtualAccountService', () => {
  let service: VirtualAccountService;
  let mockTx: {
    virtualAccount: { upsert: jest.Mock; update: jest.Mock };
    ledgerEntry: { create: jest.Mock };
    $queryRaw: jest.Mock;
    $executeRaw: jest.Mock;
  };
  let mockPrisma: typeof mockTx & { $transaction: jest.Mock };

  const TENANT = 'tenant_1';

  const account = (balance: bigint, pending: bigint) => ({
    id: 'va_1',
    tenantId: TENANT,
    balanceCents: balance,
    pendingWithdrawalCents: pending,
    currency: 'XAF',
  });

  /** Simule la ligne verrouillée retournée par `SELECT ... FOR UPDATE`. */
  const lockedRow = (balance: bigint, pending: bigint) => [
    { id: 'va_1', balanceCents: balance, pendingWithdrawalCents: pending, currency: 'XAF' },
  ];

  beforeEach(async () => {
    mockTx = {
      virtualAccount: {
        upsert: jest.fn(),
        update: jest.fn(),
      },
      ledgerEntry: {
        create: jest.fn(),
      },
      $queryRaw: jest.fn(),
      $executeRaw: jest.fn(),
    };
    mockPrisma = {
      ...mockTx,
      $transaction: jest.fn(),
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        VirtualAccountService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get(VirtualAccountService);
    jest.clearAllMocks();
    // Simule prisma.$transaction(async (tx) => ...) en rejouant tout de
    // suite le callback avec le même mockTx (pas de vraie base en jeu).
    mockPrisma.$transaction.mockImplementation((fn: (tx: unknown) => unknown) =>
      fn(mockTx),
    );
  });

  describe('getBalance', () => {
    it('calcule le disponible = balance - pending', async () => {
      mockPrisma.virtualAccount.upsert.mockResolvedValue(account(10_000n, 3_000n));

      const balance = await service.getBalance(TENANT);

      expect(balance).toEqual({
        balanceCents: 10_000n,
        pendingWithdrawalCents: 3_000n,
        availableCents: 7_000n,
        currency: 'XAF',
      });
    });
  });

  describe('creditFromPayment', () => {
    it('incrémente la balance et enregistre une ligne PAYMENT au Ledger', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(0n, 0n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(0n, 0n));
      mockTx.virtualAccount.update.mockResolvedValue(account(5_000n, 0n));

      await service.creditFromPayment(TENANT, 5_000n, 'PAY-1');

      expect(mockTx.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { balanceCents: { increment: 5_000n } },
      });
      expect(mockTx.ledgerEntry.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          tenantId: TENANT,
          virtualAccountId: 'va_1',
          amountCents: 5_000n,
          balanceAfterCents: 5_000n,
          type: LedgerEntryType.PAYMENT,
          reference: 'PAY-1',
        }),
      });
    });

    it('rejette un montant non positif sans ouvrir de transaction', async () => {
      await expect(
        service.creditFromPayment(TENANT, 0n, 'PAY-2'),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
    });
  });

  describe('reserveForWithdrawal', () => {
    it('verrouille la ligne du compte avant de bloquer les fonds', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 2_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 2_000n));
      mockTx.virtualAccount.update.mockResolvedValue(account(10_000n, 6_000n));

      // disponible = 8 000, retrait 4 000 => OK
      await service.reserveForWithdrawal(TENANT, 4_000n);

      expect(mockTx.$executeRaw).toHaveBeenCalled();
      expect(mockTx.$queryRaw).toHaveBeenCalled();
      expect(mockTx.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { pendingWithdrawalCents: { increment: 4_000n } },
      });
      // Une réservation est un blocage, pas un mouvement définitif : pas de Ledger.
      expect(mockTx.ledgerEntry.create).not.toHaveBeenCalled();
    });

    it('rejette quand le disponible est insuffisant (invariant protégé)', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 8_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 8_000n));

      // disponible = 2 000, retrait 3 000 => refus
      await expect(
        service.reserveForWithdrawal(TENANT, 3_000n),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(mockTx.virtualAccount.update).not.toHaveBeenCalled();
    });

    it('rejette un montant non positif', async () => {
      await expect(
        service.reserveForWithdrawal(TENANT, -1n),
      ).rejects.toBeInstanceOf(BadRequestException);
    });

    it('participe à une transaction externe sans en ouvrir une nouvelle', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 2_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 2_000n));
      mockTx.virtualAccount.update.mockResolvedValue(account(10_000n, 6_000n));

      await service.reserveForWithdrawal(TENANT, 4_000n, mockTx as never);

      expect(mockPrisma.$transaction).not.toHaveBeenCalled();
      expect(mockTx.virtualAccount.update).toHaveBeenCalled();
    });
  });

  describe('completeWithdrawal', () => {
    it('débite la balance, libère le blocage ET enregistre une ligne WITHDRAWAL au Ledger', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 4_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 4_000n));
      mockTx.virtualAccount.update.mockResolvedValue(account(6_000n, 0n));

      await service.completeWithdrawal(TENANT, 4_000n, 'WD-1');

      expect(mockTx.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: {
          balanceCents: { decrement: 4_000n },
          pendingWithdrawalCents: { decrement: 4_000n },
        },
      });
      expect(mockTx.ledgerEntry.create).toHaveBeenCalledWith({
        data: expect.objectContaining({
          tenantId: TENANT,
          virtualAccountId: 'va_1',
          amountCents: -4_000n,
          balanceAfterCents: 6_000n,
          type: LedgerEntryType.WITHDRAWAL,
          reference: 'WD-1',
        }),
      });
    });

    it('rejette si le blocage est insuffisant pour finaliser ce montant', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 1_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 1_000n));

      await expect(
        service.completeWithdrawal(TENANT, 4_000n, 'WD-2'),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(mockTx.virtualAccount.update).not.toHaveBeenCalled();
      expect(mockTx.ledgerEntry.create).not.toHaveBeenCalled();
    });
  });

  describe('releaseWithdrawal', () => {
    it('libère le blocage sans toucher à la balance ni au Ledger', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 4_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 4_000n));
      mockTx.virtualAccount.update.mockResolvedValue(account(10_000n, 0n));

      await service.releaseWithdrawal(TENANT, 4_000n);

      expect(mockTx.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { pendingWithdrawalCents: { decrement: 4_000n } },
      });
      expect(mockTx.ledgerEntry.create).not.toHaveBeenCalled();
    });

    it('rejette si le blocage à libérer est insuffisant', async () => {
      mockTx.virtualAccount.upsert.mockResolvedValue(account(10_000n, 1_000n));
      mockTx.$queryRaw.mockResolvedValue(lockedRow(10_000n, 1_000n));

      await expect(
        service.releaseWithdrawal(TENANT, 4_000n),
      ).rejects.toBeInstanceOf(BadRequestException);
    });
  });
});
