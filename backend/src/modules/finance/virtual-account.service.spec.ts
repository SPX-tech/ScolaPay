import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException } from '@nestjs/common';
import { VirtualAccountService } from './virtual-account.service';
import { PrismaService } from '../../prisma/prisma.service';

describe('VirtualAccountService', () => {
  let service: VirtualAccountService;
  let prisma: {
    virtualAccount: {
      upsert: jest.Mock;
      update: jest.Mock;
    };
  };

  const TENANT = 'tenant_1';

  const account = (balance: bigint, pending: bigint) => ({
    id: 'va_1',
    tenantId: TENANT,
    balanceCents: balance,
    pendingWithdrawalCents: pending,
    currency: 'XAF',
  });

  beforeEach(async () => {
    const mockPrisma = {
      virtualAccount: {
        upsert: jest.fn(),
        update: jest.fn(),
      },
    };

    const module: TestingModule = await Test.createTestingModule({
      providers: [
        VirtualAccountService,
        { provide: PrismaService, useValue: mockPrisma },
      ],
    }).compile();

    service = module.get(VirtualAccountService);
    prisma = module.get(PrismaService);
    jest.clearAllMocks();
  });

  describe('getBalance', () => {
    it('calcule le disponible = balance - pending', async () => {
      prisma.virtualAccount.upsert.mockResolvedValue(account(10_000n, 3_000n));

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
    it('incrémente la balance', async () => {
      prisma.virtualAccount.upsert.mockResolvedValue(account(0n, 0n));
      prisma.virtualAccount.update.mockResolvedValue(account(5_000n, 0n));

      await service.creditFromPayment(TENANT, 5_000n);

      expect(prisma.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { balanceCents: { increment: 5_000n } },
      });
    });

    it('rejette un montant non positif', async () => {
      await expect(
        service.creditFromPayment(TENANT, 0n),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(prisma.virtualAccount.update).not.toHaveBeenCalled();
    });
  });

  describe('reserveForWithdrawal', () => {
    it('bloque les fonds quand le disponible est suffisant', async () => {
      prisma.virtualAccount.upsert.mockResolvedValue(account(10_000n, 2_000n));
      prisma.virtualAccount.update.mockResolvedValue(account(10_000n, 6_000n));

      // disponible = 8 000, retrait 4 000 => OK
      await service.reserveForWithdrawal(TENANT, 4_000n);

      expect(prisma.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { pendingWithdrawalCents: { increment: 4_000n } },
      });
    });

    it('rejette quand le disponible est insuffisant (invariant protégé)', async () => {
      prisma.virtualAccount.upsert.mockResolvedValue(account(10_000n, 8_000n));

      // disponible = 2 000, retrait 3 000 => refus
      await expect(
        service.reserveForWithdrawal(TENANT, 3_000n),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(prisma.virtualAccount.update).not.toHaveBeenCalled();
    });

    it('rejette un montant non positif', async () => {
      await expect(
        service.reserveForWithdrawal(TENANT, -1n),
      ).rejects.toBeInstanceOf(BadRequestException);
    });
  });

  describe('completeWithdrawal', () => {
    it('débite la balance ET libère le blocage', async () => {
      prisma.virtualAccount.update.mockResolvedValue(account(6_000n, 0n));

      await service.completeWithdrawal(TENANT, 4_000n);

      expect(prisma.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: {
          balanceCents: { decrement: 4_000n },
          pendingWithdrawalCents: { decrement: 4_000n },
        },
      });
    });
  });

  describe('releaseWithdrawal', () => {
    it('libère le blocage sans toucher à la balance', async () => {
      prisma.virtualAccount.update.mockResolvedValue(account(10_000n, 0n));

      await service.releaseWithdrawal(TENANT, 4_000n);

      expect(prisma.virtualAccount.update).toHaveBeenCalledWith({
        where: { tenantId: TENANT },
        data: { pendingWithdrawalCents: { decrement: 4_000n } },
      });
    });
  });
});
