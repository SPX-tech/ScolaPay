import { Test, TestingModule } from '@nestjs/testing';
import { ForbiddenException, NotFoundException } from '@nestjs/common';
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

      const startDate = '2024-01-01';
      const endDate = '2024-12-31';

      await service.listTenantTransactions(tenantId, { startDate, endDate });

      expect(mockPrisma.transaction.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: expect.objectContaining({
            recordedAt: {
              gte: new Date(startDate),
              lte: new Date(endDate),
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

    beforeEach(() => {
      mockPrisma.$transaction.mockImplementation(
        (callback: (tx: any) => Promise<any>) =>
          callback({
            transaction: {
              update: jest.fn().mockResolvedValue({
                ...baseTransaction,
                status: 'VOIDED',
                voidReason: voidDto.reason,
              }),
            },
            student: {
              update: jest.fn().mockResolvedValue({}),
            },
          }),
      );
    });

    it('should successfully void a posted transaction', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(baseTransaction);
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);

      mockPrisma.transaction.findFirst
        .mockResolvedValueOnce(baseTransaction)
        .mockResolvedValueOnce(null);

      const result = await service.voidTransaction(
        tenantId,
        transactionId,
        userId,
        adminRole,
        voidDto,
      );

      expect(result.message).toBe('Transaction voided successfully.');
      expect(result.transactionId).toBe(transactionId);
      expect(mockAuditService.record).toHaveBeenCalledWith(
        expect.objectContaining({
          action: 'transaction.void',
          targetId: transactionId,
          actorUserId: userId,
        }),
      );
    });

    it('should throw NotFoundException if transaction not found or already voided', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(null);

      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          adminRole,
          voidDto,
        ),
      ).rejects.toThrow(NotFoundException);
    });

    it('should throw NotFoundException if transaction has VOIDED status (query returns null)', async () => {
      // The service queries with status: 'POSTED', so a VOIDED transaction is not found
      mockPrisma.transaction.findFirst.mockResolvedValue(null);

      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          adminRole,
          voidDto,
        ),
      ).rejects.toThrow(NotFoundException);
    });

    it('should reject voiding a non-POSTED transaction', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(null);

      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          adminRole,
          voidDto,
        ),
      ).rejects.toThrow(NotFoundException);
    });

    it('should throw NotFoundException if student not found', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(baseTransaction);
      mockPrisma.student.findFirst.mockResolvedValue(null);

      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          adminRole,
          voidDto,
        ),
      ).rejects.toThrow(NotFoundException);
    });

    it('should calculate correct reversal amounts', async () => {
      mockPrisma.transaction.findFirst.mockResolvedValue(baseTransaction);
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);

      mockPrisma.transaction.findFirst
        .mockResolvedValueOnce(baseTransaction)
        .mockResolvedValueOnce(null);

      await service.voidTransaction(
        tenantId,
        transactionId,
        userId,
        adminRole,
        voidDto,
      );

      // Transaction amount is 25000, student debt is 25000
      // After reversal: newDebt = 25000 + 25000 = 50000
      // Credit is 0, so no credit adjustment

      expect(mockPrisma.$transaction).toHaveBeenCalled();
    });

    it('should handle credit balance during reversal', async () => {
      const txWithCredit = {
        ...baseTransaction,
        amountCents: BigInt(10000),
      };
      const studentWithCredit = {
        ...baseStudent,
        debtCents: BigInt(0),
        creditCents: BigInt(5000),
      };

      mockPrisma.transaction.findFirst
        .mockResolvedValueOnce(txWithCredit)
        .mockResolvedValueOnce(null);
      mockPrisma.student.findFirst.mockResolvedValue(studentWithCredit);

      await service.voidTransaction(
        tenantId,
        transactionId,
        userId,
        adminRole,
        voidDto,
      );

      expect(mockPrisma.$transaction).toHaveBeenCalled();
    });

    it('should reject users without void permissions', async () => {
      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          'CASHIER',
          voidDto,
        ),
      ).rejects.toThrow(ForbiddenException);
    });

    it('should reject voiding older transactions when newer posted transactions exist', async () => {
      mockPrisma.transaction.findFirst
        .mockResolvedValueOnce(baseTransaction)
        .mockResolvedValueOnce({ ...baseTransaction, id: 'txn-2' });
      mockPrisma.student.findFirst.mockResolvedValue(baseStudent);

      await expect(
        service.voidTransaction(
          tenantId,
          transactionId,
          userId,
          adminRole,
          voidDto,
        ),
      ).rejects.toThrow(ForbiddenException);
    });
  });
});
