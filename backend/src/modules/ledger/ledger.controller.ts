import { Body, Controller, Get, NotFoundException, Param, Post } from '@nestjs/common';
import { ReqContext } from '../../common/decorators/request-context.decorator';
import type { RequestContext } from '../../common/types/request-context.type';
import { Roles } from '../../common/decorators/roles.decorator';
import { LedgerService } from './ledger.service';
import { ReceiptService } from './receipt.service';
import { RecordCashPaymentDto } from './dto/record-cash-payment.dto';
import { InitializeMobilePaymentDto } from './dto/initialize-mobile-payment.dto';
import { PrismaService } from '../../prisma/prisma.service';

@Controller('ledger')
export class LedgerController {
  constructor(
    private readonly ledgerService: LedgerService,
    private readonly receiptService: ReceiptService,
    private readonly prisma: PrismaService,
  ) {}

  @Roles('TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT')
  @Post('record-cash-payment')
  recordCashPayment(
    @ReqContext() context: RequestContext,
    @Body() dto: RecordCashPaymentDto,
  ) {
    return this.ledgerService.recordCashPayment(
      context.tenantId,
      context.userId,
      context.userRole,
      dto,
    );
  }

  /** Initie un encaissement numérique (push USSD) : la transaction reste PENDING jusqu'au webhook. */
  @Roles('TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT')
  @Post('initialize-mobile-payment')
  initializeMobilePayment(
    @ReqContext() context: RequestContext,
    @Body() dto: InitializeMobilePaymentDto,
  ) {
    return this.ledgerService.initializeMobilePayment(
      context.tenantId,
      context.userId,
      context.userRole,
      dto,
    );
  }

  @Get('receipts/:transactionId')
  async getReceipt(
    @ReqContext() context: RequestContext,
    @Param('transactionId') transactionId: string,
  ) {
    const transaction = await this.prisma.transaction.findFirst({
      where: { id: transactionId, tenantId: context.tenantId },
      include: {
        student: { select: { fullName: true, studentNumber: true } },
        recordedBy: { select: { fullName: true } },
      },
    });

    if (!transaction) {
      throw new NotFoundException('Transaction not found.');
    }

    if (!transaction.receiptNumber || !transaction.receiptHash) {
      throw new NotFoundException('Receipt not available: payment not yet confirmed.');
    }

    // Generate QR code image (async - on-the-fly)
    const qrPayload = this.receiptService.generateReceiptQRPayload({
      ...transaction,
      receiptNumber: transaction.receiptNumber,
      receiptHash: transaction.receiptHash,
    });
    const qrImage = await this.receiptService.generateQRCodeImage(qrPayload);

    return {
      receiptNumber: transaction.receiptNumber,
      receiptHash: transaction.receiptHash,
      qrCode: qrImage,
      transaction: {
        id: transaction.id,
        amountCents: transaction.amountCents,
        currency: transaction.currency,
        recordedAt: transaction.recordedAt,
        type: transaction.type,
        status: transaction.status,
        student: transaction.student,
        recordedBy: transaction.recordedBy,
      },
    };
  }
}
