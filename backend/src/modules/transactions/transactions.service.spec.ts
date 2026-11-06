import { Test, TestingModule } from '@nestjs/testing';
import {
  ConflictException,
  ForbiddenException,
  NotFoundException,
} from '@nestjs/common';
import { TransactionsService } from './transactions.service';
import { PrismaService } from '../../prisma/prisma.service';
import { AuditService } from '../audit/audit.service';

describe('TransactionsService', () => {
  let service: TransactionsService;

  const mockPrisma = {
    transaction: {
      findMany: jest.fn(),
      findFirst: jest.fn(),
      update: jest.fn(),
      count: jest.fn(),
    },
    student: {
      findFirst: jest.fn(),
      update: jest.fn(),
    },
    tenant: {
      findUniqueOrThrow: jest.fn(),
    },
    $transaction: jest.fn(),
  };
  const mockAuditService = {
    record: jest.fn(),
  };

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TransactionsService,
        { provide: PrismaService, useValue: mockPrisma },
        { provide: AuditService, useValue: mockAuditService },
      ],
    }).compile();

    service = module.get<TransactionsService>(TransactionsService);
    jest.clearAllMocks();
  });

  const tenantId = 'tenant-1';
  const transactionId = 'txn-1';
  const userId = 'user-1';
  const adminRole = 'TENANT_ADMIN';
  const studentId = 'student-1';

  const baseTransaction = {
    id: transactionId,
    tenantId,
    studentId,
    recordedByUserId: userId,
    type: 'CASH_PAYMENT',
    status: 'POSTED',
    amountCents: BigInt(25000),
    previousDebtCents: BigInt(50000),
    newDebtCents: BigInt(25000),
    currency: 'XAF',
    receiptNumber: 'RCP-TEST-001',
    receiptHash: 'abc123',
    note: null,
    recordedAt: new Date(),
    voidedAt: null,
    voidReason: null,
  };

  const baseStudent = {
    id: studentId,
    tenantId,
    fullName: 'Jean Dupont',
    studentNumber: 'ETU001',
    debtCents: BigInt(25000),
    creditCents: BigInt(0),
    status: 'ACTIVE',
  };

  describe('listTenantTransactions', () => {
    it('should return paginated transactions', async () => {
      const transactions = [
        {
          ...baseTransaction,
          student: {
            id: studentId,
            studentNumber: 'ETU001',
            fullName: 'Jean Dupont',
          },
          recordedBy: {
            id: userId,
            fullName: 'Admin',
            email: 'admin@test.com',
          },
        },
      ];

      mockPrisma.transaction.findMany.mockResolvedValue(transactions);
      mockPrisma.transaction.count.mockResolvedValue(1);

      const result = await service.listTenantTransactions(tenantId, {
        page: 1,
        limit: 20,
      });

      expect(result.data).toHaveLength(1);
      expect(result.meta.total).toBe(1);
      expect(result.meta.page).toBe(1);
    });

    it('should filter by transaction type', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([]);
      mockPrisma.transaction.count.mockResolvedValue(0);

      await service.listTenantTransactions(tenantId, { type: 'CASH_PAYMENT' });

      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ type: 'CASH_PAYMENT' }),
        }),
      );
    });

    it('should filter by date range', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([]);
      mockPrisma.transaction.count.mockResolvedValue(0);

      mockPrisma.tenant.findUniqueOrThrow.mockResolvedValue({
        timezone: 'Africa/Ndjamena',
      });

      await service.listTenantTransactions(tenantId, {
        startDate: '2024-01-01',
        endDate: '2024-12-31',
      });

      // Journées entières dans le fuseau de l'établissement (UTC+1).
      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            recordedAt: {
              gte: new Date('2023-12-31T23:00:00.000Z'),
              lte: new Date('2024-12-31T22:59:59.999Z'),
            },
          }),
        }),
      );
    });

    it('should filter by student ID', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([]);
      mockPrisma.transaction.count.mockResolvedValue(0);

      await service.listTenantTransactions(tenantId, { studentId });

      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ studentId }),
        }),
      );
    });

    it('should sort by recordedAt descending', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([]);
      mockPrisma.transaction.count.mockResolvedValue(0);

      await service.listTenantTransactions(tenantId, {});

      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          orderBy: { recordedAt: 'desc' },
        }),
      );
    });
  });

  describe('getTenantTransactionById', () => {
    it('should return a transaction by id', async () => {
      const transaction = {
        ...baseTransaction,
        student: {
          id: studentId,
          studentNumber: 'ETU001',
          fullName: 'Jean Dupont',
        },
        recordedBy: { id: userId, fullName: 'Admin', email: 'admin@test.com' },
      };
      mockPrisma.transaction.findFirst.mockResolvedValue(transaction);

      const result = await service.getTenantTransactionById(
        tenantId,
        transactionId,
      );

      expect(result).toEqual(transaction);
    });

    it('should throw NotFoundException if transaction not found', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(null);

      await expect(
        service.getTenantTransactionById(tenantId, 'nonexistent'),
      ).rejects.toThrow(NotFoundException);
    });

    it('should scope query to tenant', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(null);

      await service
        .getTenantTransactionById(tenantId, transactionId)
        .catch(() => {});

      expect(mockPrisma.transaction.findFirst).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ tenantId }),
        }),
      );
    });
  });

  describe('listStudentTransactions', () => {
    it('should return transactions for a specific student', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([baseTransaction]);
      mockPrisma.transaction.count.mockResolvedValue(1);

      const result = await service.listStudentTransactions(
        tenantId,
        studentId,
        { page: 1, limit: 10 },
      );

      expect(result.data).toHaveLength(1);
      expect(result.meta.total).toBe(1);
    });

    it('should scope queries to both tenant and student', async () => {
      mockPrisma.transaction.findMany.mockResolvedValue([]);
      mockPrisma.transaction.count.mockResolvedValue(0);

      await service.listStudentTransactions(tenantId, studentId, {});

      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({ tenantId, studentId }),
        }),
      );
    });
  });

  describe('voidTransaction', () => {
    const voidDto = { reason: 'Paiement en double' };
    let mockTx: {
      transaction: { findFirst: jest.Mock; update: jest.Mock };
      student: { update: jest.Mock };
      $executeRaw: jest.Mock;
      $queryRaw: jest.Mock;
    };

    /** Enchaîne les lectures : cible, relecture sous verrou, mouvement postérieur. */
    const arrange = (
      transaction: Record<string, unknown> | null,
      student: Record<string, unknown> | null = baseStudent,
      later: Record<string, unknown> | null = null,
    ) => {
      mockTx.transaction.findFirst
        .mockResolvedValueOnce(transaction && { studentId })
        .mockResolvedValueOnce(transaction)
        .mockResolvedValueOnce(later);
      mockTx.$queryRaw.mockResolvedValue(student ? [student] : []);
    };

    const runVoid = (role = adminRole) =>
      service.voidTransaction(tenantId, transactionId, userId, role, voidDto);

    beforeEach(() => {
      mockTx = {
        transaction: {
          findFirst: jest.fn(),
          update: jest.fn().mockResolvedValue({
            ...baseTransaction,
            status: 'VOIDED',
            voidReason: voidDto.reason,
          }),
        },
        student: { update: jest.fn().mockResolvedValue({}) },
        $executeRaw: jest.fn().mockResolvedValue(0),
        $queryRaw: jest.fn(),
      };
      mockPrisma.$transaction.mockImplementation(
        (callback: (tx: unknown) => Promise<unknown>) => callback(mockTx),
      );
    });

    it('should successfully void a posted cash transaction', async () => {
      arrange(baseTransaction);

      const result = await runVoid();

      expect(result.message).toBe('Transaction voided successfully.');
      expect(result.transactionId).toBe(transactionId);
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'transaction.void',
          targetId: transactionId,
          actorUserId: userId,
        }),
        mockTx,
      );
    });

    it('should lock the student before re-reading the transaction status', async () => {
      arrange(baseTransaction);

      await runVoid();

      expect(mockTx.$queryRaw.mock.invocationCallOrder[0]).toBeLessThan(
        mockTx.transaction.findFirst.mock.invocationCallOrder[1],
      );
      expect(mockTx.transaction.findFirst.mock.calls[1][0].where.status).toBe(
        'POSTED',
      );
    });

    it('should throw NotFoundException if the transaction does not exist', async () => {
      arrange(null);

      await expect(runVoid()).rejects.toThrow(NotFoundException);
      expect(mockTx.$queryRaw).not.toHaveBeenCalled();
    });

    it('should throw NotFoundException if it was voided before the lock was acquired', async () => {
      mockTx.transaction.findFirst
        .mockResolvedValueOnce({ studentId })
        .mockResolvedValueOnce(null);
      mockTx.$queryRaw.mockResolvedValue([baseStudent]);

      await expect(runVoid()).rejects.toThrow(NotFoundException);
      expect(mockTx.transaction.update).not.toHaveBeenCalled();
    });

    it('should throw NotFoundException if student not found', async () => {
      arrange(baseTransaction, null);

      await expect(runVoid()).rejects.toThrow(NotFoundException);
    });

    it('should restore the debt recorded before the payment', async () => {
      arrange(baseTransaction);

      await runVoid();

      expect(mockTx.student.update).toHaveBeenCalledWith({
        where: { id: studentId },
        data: { debtCents: BigInt(50000), creditCents: BigInt(0) },
      });
    });

    it('should remove only the overpayment from the credit', async () => {
      arrange(
        {
          ...baseTransaction,
          amountCents: BigInt(30000),
          previousDebtCents: BigInt(25000),
          newDebtCents: BigInt(0),
        },
        {
          ...baseStudent,
          debtCents: BigInt(0),
          creditCents: BigInt(7000),
        },
      );

      await runVoid();

      expect(mockTx.student.update).toHaveBeenCalledWith({
        where: { id: studentId },
        data: { debtCents: BigInt(25000), creditCents: BigInt(2000) },
      });
    });

    it('should refuse digital payments, which require a refund workflow', async () => {
      arrange({ ...baseTransaction, type: 'MOBILE_PAYMENT' });

      await expect(runVoid()).rejects.toThrow(ConflictException);
      expect(mockTx.transaction.update).not.toHaveBeenCalled();
      expect(mockTx.student.update).not.toHaveBeenCalled();
    });

    it('should refuse when the balance changed since the payment', async () => {
      arrange(baseTransaction, { ...baseStudent, debtCents: BigInt(99000) });

      await expect(runVoid()).rejects.toThrow(ConflictException);
      expect(mockTx.student.update).not.toHaveBeenCalled();
    });

    it('should refuse when the overpayment credit is no longer available', async () => {
      arrange(
        {
          ...baseTransaction,
          amountCents: BigInt(30000),
          previousDebtCents: BigInt(25000),
          newDebtCents: BigInt(0),
        },
        { ...baseStudent, debtCents: BigInt(0), creditCents: BigInt(1000) },
      );

      await expect(runVoid()).rejects.toThrow(ConflictException);
    });

    it('should reject users without void permissions', async () => {
      await expect(runVoid('CASHIER')).rejects.toThrow(ForbiddenException);
    });

    it('should reject voiding older transactions when newer posted transactions exist', async () => {
      arrange(baseTransaction, baseStudent, {
        ...baseTransaction,
        id: 'txn-2',
      });

      await expect(runVoid()).rejects.toThrow(ForbiddenException);
    });
  });
});
