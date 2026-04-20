import { Injectable } from '@nestjs/common';
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

@Injectable()
export class ReceiptService {
  generateReceiptNumber(tenantCode: string): string {
    const now = new Date();
    const yyyymmdd = `${now.getUTCFullYear()}${pad(now.getUTCMonth() + 1)}${pad(now.getUTCDate())}`;
    const code = tenantCode.replace(/[^a-zA-Z0-9]/g, '').toUpperCase() || 'TENANT';
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
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : `${value}`;
}
