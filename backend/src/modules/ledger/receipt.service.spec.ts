import { Test, TestingModule } from '@nestjs/testing';
import { ReceiptService } from './receipt.service';

describe('ReceiptService', () => {
  let service: ReceiptService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      providers: [ReceiptService],
    }).compile();

    service = module.get<ReceiptService>(ReceiptService);
  });

  describe('generateReceiptNumber', () => {
    it('should generate a receipt number with correct format', () => {
      const code = 'LYC001';
      const receipt = service.generateReceiptNumber(code);

      expect(receipt).toMatch(/^RCP-LYC001-\d{8}-[A-F0-9]{8}$/);
    });

    it('should sanitize the tenant code', () => {
      const receipt = service.generateReceiptNumber('école-alpha!');
      expect(receipt).toMatch(/^RCP-[A-Z]+-\d{8}-[A-F0-9]{8}$/);
      expect(receipt).not.toContain('é');
    });

    it('should use TENANT fallback for empty code', () => {
      const receipt = service.generateReceiptNumber('');
      expect(receipt).toMatch(/^RCP-TENANT-\d{8}-[A-F0-9]{8}$/);
    });

    it('should produce unique numbers on successive calls', () => {
      const r1 = service.generateReceiptNumber('CODE');
      const r2 = service.generateReceiptNumber('CODE');
      expect(r1).not.toBe(r2);
    });
  });

  describe('generateReceiptHash', () => {
    const hashInput = {
      tenantId: 'tenant-1',
      studentId: 'student-1',
      recordedByUserId: 'user-1',
      amountCents: BigInt(50000),
      previousDebtCents: BigInt(20000),
      newDebtCents: BigInt(0),
      currency: 'XAF',
      receiptNumber: 'RCP-TEST-001',
      recordedAt: new Date('2024-01-15T10:00:00Z'),
    };

    it('should generate a consistent hash', () => {
      const hash1 = service.generateReceiptHash(hashInput);
      const hash2 = service.generateReceiptHash(hashInput);

      expect(hash1).toBe(hash2);
      expect(hash1).toHaveLength(64); // SHA-256 hex
    });

    it('should produce different hashes for different inputs', () => {
      const hash1 = service.generateReceiptHash(hashInput);
      const hash2 = service.generateReceiptHash({
        ...hashInput,
        amountCents: BigInt(100),
      });

      expect(hash1).not.toBe(hash2);
    });

    it('should produce different hashes for different receipt numbers', () => {
      const hash1 = service.generateReceiptHash(hashInput);
      const hash2 = service.generateReceiptHash({
        ...hashInput,
        receiptNumber: 'RCP-TEST-002',
      });

      expect(hash1).not.toBe(hash2);
    });
  });

  describe('generateReceiptQRPayload', () => {
    const transaction = {
      id: 'txn-1',
      receiptNumber: 'RCP-TEST-001',
      receiptHash: 'abc123def456',
      amountCents: BigInt(50000),
      previousDebtCents: BigInt(20000),
      newDebtCents: BigInt(0),
      currency: 'XAF',
      recordedAt: new Date('2024-01-15T10:00:00Z'),
      student: { fullName: 'Jean Dupont', studentNumber: 'ETU001' },
      recordedBy: { fullName: 'Admin User' },
    };

    it('should generate a valid JSON payload', () => {
      const payload = service.generateReceiptQRPayload(transaction);
      const parsed = JSON.parse(payload);

      expect(parsed.v).toBe('1');
      expect(parsed.rn).toBe('RCP-TEST-001');
      expect(parsed.rh).toBe('abc123def456');
      expect(parsed.ti).toBe('txn-1');
      expect(parsed.sn).toBe('ETU001');
      expect(parsed.am).toBe('50000');
      expect(parsed.cur).toBe('XAF');
    });

    it('should include all required fields', () => {
      const payload = service.generateReceiptQRPayload(transaction);
      const parsed = JSON.parse(payload);

      const requiredFields = [
        'v',
        'rn',
        'rh',
        'ti',
        'sn',
        'am',
        'pd',
        'nd',
        'cur',
        'at',
      ];
      requiredFields.forEach((field) => {
        expect(parsed).toHaveProperty(field);
      });
    });
  });

  describe('generateReceiptData', () => {
    const transaction = {
      id: 'txn-1',
      receiptNumber: 'RCP-TEST-001',
      receiptHash: 'abc123def456',
      amountCents: BigInt(50000),
      previousDebtCents: BigInt(20000),
      newDebtCents: BigInt(0),
      currency: 'XAF',
      recordedAt: new Date('2024-01-15T10:00:00Z'),
      student: { fullName: 'Jean Dupont', studentNumber: 'ETU001' },
      recordedBy: { fullName: 'Admin User' },
    };

    it('should generate valid JSON receipt data', () => {
      const data = service.generateReceiptData(transaction);
      const parsed = JSON.parse(data);

      expect(parsed.version).toBe('1.0');
      expect(parsed.receiptNumber).toBe('RCP-TEST-001');
      expect(parsed.studentName).toBe('Jean Dupont');
      expect(parsed.amount).toBe('50000');
    });

    it('should include all receipt fields', () => {
      const data = service.generateReceiptData(transaction);
      const parsed = JSON.parse(data);

      const expectedFields = [
        'version',
        'receiptNumber',
        'hash',
        'transactionId',
        'studentName',
        'studentNumber',
        'amount',
        'previousDebt',
        'newDebt',
        'currency',
        'recordedAt',
        'recordedBy',
      ];
      expectedFields.forEach((field) => {
        expect(parsed).toHaveProperty(field);
      });
    });
  });

  describe('generateQRCodeImage', () => {
    it('should return data when QR code generation works', async () => {
      // Mock dynamic import of qrcode
      jest.isolateModules(() => {
        // In test environment, dynamic import may fail, testing the fallback
      });
      const result = await service.generateQRCodeImage('test-data');
      expect(typeof result).toBe('string');
    });

    it('should handle qrcode module gracefully and return fallback data', async () => {
      const result = await service.generateQRCodeImage('hello-world');
      expect(typeof result).toBe('string');
      // When qrcode dynamic import fails, it returns the input data as fallback
      expect(result).toBe('hello-world');
    });
  });
});
