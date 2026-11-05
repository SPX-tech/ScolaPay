import { Injectable, Logger } from '@nestjs/common';
import { createHash, randomBytes } from 'node:crypto';

type ReceiptHashInput = {
  tenantId: string;
  studentId: string;
  recordedByUserId: string;
  amountCents: bigint;
  previousDebtCents: bigint;
  newDebtCents: bigint;
  currency: string;
  receiptNumber: string;
  recordedAt: Date;
};

type ReceiptTransactionData = {
  id: string;
  receiptNumber: string;
  receiptHash: string;
  amountCents: bigint;
  previousDebtCents: bigint;
  newDebtCents: bigint;
  currency: string;
  recordedAt: Date;
  student: { fullName: string; studentNumber: string };
  recordedBy: { fullName: string };
};

@Injectable()
export class ReceiptService {
  private readonly logger = new Logger(ReceiptService.name);

  generateReceiptNumber(tenantCode: string): string {
    const now = new Date();
    const yyyymmdd = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}`;
    const code =
      tenantCode.replace(/[^a-zA-Z0-9]/g, '').toUpperCase() || 'TENANT';
    const randomSuffix = randomBytes(4).toString('hex').toUpperCase();

    return `RCP-${code}-${yyyymmdd}-${randomSuffix}`;
  }

  generateReceiptHash(input: ReceiptHashInput): string {
    const canonicalData = [
      input.tenantId,
      input.studentId,
      input.recordedByUserId,
      input.amountCents.toString(),
      input.previousDebtCents.toString(),
      input.newDebtCents.toString(),
      input.currency,
      input.receiptNumber,
      input.recordedAt.toISOString(),
    ].join('|');

    return createHash('sha256').update(canonicalData).digest('hex');
  }

  generateReceiptQRPayload(transaction: ReceiptTransactionData): string {
    return JSON.stringify({
      v: '1',
      rn: transaction.receiptNumber,
      rh: transaction.receiptHash,
      ti: transaction.id,
      sn: transaction.student.studentNumber,
      am: transaction.amountCents.toString(),
      pd: transaction.previousDebtCents.toString(),
      nd: transaction.newDebtCents.toString(),
      cur: transaction.currency,
      at: transaction.recordedAt.toISOString(),
    });
  }

  async generateQRCodeImage(data: string): Promise<string> {
    try {
      const qrcode = await import('qrcode');
      const dataUrl = await qrcode.toDataURL(data, {
        width: 300,
        margin: 2,
        color: {
          dark: '#1a1a2e',
          light: '#ffffff',
        },
      });
      return dataUrl;
    } catch (error) {
      this.logger.warn(
        `QR code generation failed, falling back to plain data: ${(error as Error).message}`,
      );
      return data;
    }
  }

  generateReceiptData(transaction: ReceiptTransactionData): string {
    return JSON.stringify({
      version: '1.0',
      receiptNumber: transaction.receiptNumber,
      hash: transaction.receiptHash,
      transactionId: transaction.id,
      studentName: transaction.student.fullName,
      studentNumber: transaction.student.studentNumber,
      amount: transaction.amountCents.toString(),
      previousDebt: transaction.previousDebtCents.toString(),
      newDebt: transaction.newDebtCents.toString(),
      currency: transaction.currency,
      recordedAt: transaction.recordedAt.toISOString(),
      recordedBy: transaction.recordedBy.fullName,
    });
  }
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : `${value}`;
}
