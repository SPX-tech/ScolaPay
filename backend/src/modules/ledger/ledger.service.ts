import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { Prisma } from '../../generated/prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { OutboxService } from '../outbox/outbox.service';
import { ReceiptService } from './receipt.service';
import { RecordCashPaymentDto } from './dto/record-cash-payment.dto';
import { InitializeMobilePaymentDto } from './dto/initialize-mobile-payment.dto';
import { AuditService } from '../audit/audit.service';
import {
  BALANCE_TRANSACTION_OPTIONS,
  lockStudentBalance,
} from '../students/student-balance-lock';
import { VirtualAccountService } from '../finance/virtual-account.service';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import type { PaymentProvider } from '../payment/payment-provider.interface';

type RoleType = 'TENANT_ADMIN' | 'CASHIER' | 'ACCOUNTANT' | 'AUDITOR';

type RecordPaymentResult = {
  transactionId: string;
  studentId: string;
  amountCents: bigint;
  previousDebtCents: bigint;
  newDebtCents: bigint;
  creditCents: bigint;
  currency: string;
  receiptNumber: string;
  receiptHash: string;
  qrCodeData: string;
  recordedAt: Date;
};

type InitializeMobilePaymentResult = {
  transactionId: string;
  reference: string;
  status: 'PENDING';
  amountCents: bigint;
  currency: string;
};

/** Un trop-perçu ne rend pas la dette négative : il alimente le crédit de l'élève. */
function applyPayment(
  student: { debtCents: bigint; creditCents: bigint },
  amountCents: bigint,
): { newDebtCents: bigint; newCreditCents: bigint } {
  const remaining = student.debtCents - amountCents;
  return remaining < 0n
    ? { newDebtCents: 0n, newCreditCents: student.creditCents - remaining }
    : { newDebtCents: remaining, newCreditCents: student.creditCents };
}

/** Résultat d'une confirmation webhook, distingué pour un logging/monitoring clair. */
type ConfirmMobilePaymentOutcome =
  'credited' | 'already_processed' | 'failed' | 'not_found';

@Injectable()
export class LedgerService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly outboxService: OutboxService,
    private readonly receiptService: ReceiptService,
    private readonly auditService: AuditService,
    private readonly virtualAccountService: VirtualAccountService,
    @Inject(PAYMENT_PROVIDER) private readonly paymentProvider: PaymentProvider,
  ) {}

  async recordCashPayment(
    tenantId: string,
    userId: string,
    userRole: string,
    dto: RecordCashPaymentDto,
  ): Promise<RecordPaymentResult> {
    const allowedRoles: RoleType[] = ['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT'];
    if (!allowedRoles.includes(userRole as RoleType)) {
      throw new ForbiddenException(
        'Only admins, cashiers, and accountants can record payments.',
      );
    }

    // Idempotency check
    if (dto.idempotencyKey) {
      const existing = await this.prisma.transaction.findUnique({
        where: {
          tenantId_idempotencyKey: {
            tenantId,
            idempotencyKey: dto.idempotencyKey,
          },
        },
      });

      if (existing) {
        throw new ConflictException(
          'Transaction already recorded (idempotent).',
        );
      }
    }

    // Get tenant + validate
    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
    });

    if (!tenant) {
      throw new NotFoundException('Tenant not found.');
    }

    const amountCents = BigInt(dto.amountCents);

    try {
      return await this.prisma.$transaction(
        async (tx: Prisma.TransactionClient) => {
          const student = await lockStudentBalance(tx, tenantId, dto.studentId);
          if (!student || student.status !== 'ACTIVE') {
            throw new NotFoundException('Active student not found.');
          }

          const recordedAt = new Date();
          const previousDebtCents = student.debtCents;
          const { newDebtCents, newCreditCents } = applyPayment(
            student,
            amountCents,
          );

          const receiptNumber = this.receiptService.generateReceiptNumber(
            tenant.code,
          );
          const receiptHash = this.receiptService.generateReceiptHash({
            tenantId,
            studentId: student.id,
            recordedByUserId: userId,
            amountCents,
            previousDebtCents,
            newDebtCents,
            currency: tenant.currency,
            receiptNumber,
            recordedAt,
          });

          // Generate QR code data (stored as JSON string)
          const qrCodeData = this.receiptService.generateReceiptData({
            id: 'pending',
            receiptNumber,
            receiptHash,
            amountCents,
            previousDebtCents,
            newDebtCents,
            currency: tenant.currency,
            recordedAt,
            student: {
              fullName: student.fullName,
              studentNumber: student.studentNumber,
            },
            recordedBy: { fullName: 'pending' },
          });

          const transaction = await tx.transaction.create({
            data: {
              tenantId,
              studentId: student.id,
              recordedByUserId: userId,
              type: 'CASH_PAYMENT',
              status: 'POSTED',
              amountCents,
              previousDebtCents,
              newDebtCents,
              currency: tenant.currency,
              receiptNumber,
              receiptHash,
              qrCodeData,
              idempotencyKey: dto.idempotencyKey,
              note: dto.note,
              metadata: dto.metadata as Prisma.InputJsonObject | undefined,
              recordedAt,
            },
          });

          await tx.student.update({
            where: { id: student.id },
            data: {
              debtCents: newDebtCents,
              creditCents: newCreditCents,
              lastPaymentAt: recordedAt,
            },
          });

          // Outbox event for notification
          await this.outboxService.createEvent({
            tx,
            tenantId,
            aggregateType: 'transaction',
            aggregateId: transaction.id,
            eventType: 'cash-payment.recorded',
            payload: {
              transactionId: transaction.id,
              tenantId,
              studentId: student.id,
              studentName: student.fullName,
              guardianPhone: student.guardianPhone,
              guardianName: student.guardianName,
              amountCents: amountCents.toString(),
              receiptNumber,
              recordedAt: transaction.recordedAt.toISOString(),
            },
          });

          await this.auditService.record(
            {
              tenantId,
              actorUserId: userId,
              action: 'transaction.cash_payment.record',
              targetType: 'transaction',
              targetId: transaction.id,
              metadata: {
                studentId: student.id,
                amountCents: amountCents.toString(),
                previousDebtCents: previousDebtCents.toString(),
                newDebtCents: newDebtCents.toString(),
                creditCents: newCreditCents.toString(),
                receiptNumber,
              },
            },
            tx,
          );

          return {
            transactionId: transaction.id,
            studentId: student.id,
            amountCents: transaction.amountCents,
            previousDebtCents: transaction.previousDebtCents,
            newDebtCents: transaction.newDebtCents,
            creditCents: newCreditCents,
            currency: transaction.currency,
            receiptNumber,
            receiptHash,
            qrCodeData,
            recordedAt: transaction.recordedAt,
          };
        },
        BALANCE_TRANSACTION_OPTIONS,
      );
    } catch (error: unknown) {
      if (
        error &&
        typeof error === 'object' &&
        'code' in error &&
        error.code === 'P2002'
      ) {
        throw new ConflictException(
          'Transaction already recorded (duplicate).',
        );
      }
      throw error;
    }
  }

  /**
   * Initie un encaissement numérique (MOBILE_PAYMENT) : crée la transaction
   * locale en PENDING puis déclenche la collecte côté prestataire (ex. push
   * USSD). L'appel au prestataire est volontairement hors de toute
   * transaction Prisma (latence réseau non bornée). Si le prestataire
   * refuse immédiatement, la transaction est basculée en FAILED sans
   * jamais rester PENDING sans espoir de confirmation.
   */
  async initializeMobilePayment(
    tenantId: string,
    userId: string,
    userRole: string,
    dto: InitializeMobilePaymentDto,
  ): Promise<InitializeMobilePaymentResult> {
    const allowedRoles: RoleType[] = ['TENANT_ADMIN', 'CASHIER', 'ACCOUNTANT'];
    if (!allowedRoles.includes(userRole as RoleType)) {
      throw new ForbiddenException(
        'Only admins, cashiers, and accountants can initiate mobile payments.',
      );
    }

    const tenant = await this.prisma.tenant.findUnique({
      where: { id: tenantId },
    });
    if (!tenant) {
      throw new NotFoundException('Tenant not found.');
    }

    const student = await this.prisma.student.findFirst({
      where: { id: dto.studentId, tenantId, status: 'ACTIVE' },
    });
    if (!student) {
      throw new NotFoundException('Active student not found.');
    }

    const amountCents = BigInt(dto.amountCents);
    const providerReference = this.generatePaymentReference();

    const transaction = await this.prisma.transaction.create({
      data: {
        tenantId,
        studentId: student.id,
        recordedByUserId: userId,
        type: 'MOBILE_PAYMENT',
        status: 'PENDING',
        amountCents,
        // Non encore appliqué : recalculé à la confirmation à partir du
        // solde alors le plus frais (d'autres paiements peuvent survenir
        // entre l'initiation et la confirmation).
        previousDebtCents: student.debtCents,
        newDebtCents: student.debtCents,
        currency: tenant.currency,
        providerReference,
        payerPhone: dto.payerPhone,
        recordedAt: new Date(),
      },
    });

    let collectResult;
    try {
      collectResult = await this.paymentProvider.collect({
        reference: providerReference,
        amountCents,
        currency: tenant.currency,
        payerPhone: dto.payerPhone,
        tenantId,
        studentName: student.fullName,
      });
    } catch (error) {
      await this.markMobilePaymentInitiationFailed(
        transaction.id,
        tenantId,
        (error as Error).message,
      );
      throw new BadRequestException(
        `Payment collection could not be initiated: ${(error as Error).message}`,
      );
    }

    if (collectResult.status === 'FAILED') {
      const reason =
        collectResult.failureReason ??
        'Provider rejected the collection request.';
      await this.markMobilePaymentInitiationFailed(
        transaction.id,
        tenantId,
        reason,
      );
      throw new BadRequestException(reason);
    }

    await this.auditService.record({
      tenantId,
      actorUserId: userId,
      action: 'transaction.mobile_payment.initiate',
      targetType: 'transaction',
      targetId: transaction.id,
      metadata: {
        studentId: student.id,
        amountCents: amountCents.toString(),
        payerPhone: dto.payerPhone,
        providerReference,
      },
    });

    return {
      transactionId: transaction.id,
      reference: providerReference,
      status: 'PENDING',
      amountCents,
      currency: tenant.currency,
    };
  }

  /**
   * Confirme (ou échoue) un paiement numérique suite au webhook du
   * prestataire. Idempotent par construction : la transition n'a lieu que
   * si la Transaction est encore PENDING (`updateMany` gardé par le
   * statut), donc un rejeu du webhook (retry réseau côté prestataire) ne
   * crédite jamais deux fois.
   *
   * Ne prend PAS de tenantId en entrée : c'est un webhook public, sans
   * contexte tenant (voir PaymentsWebhookController). Le tenant est déduit
   * de la Transaction retrouvée via `providerReference`, qui est unique
   * globalement pour cette raison précise.
   */
  async confirmMobilePayment(input: {
    providerReference: string;
    status: 'SUCCESS' | 'FAILED';
    failureReason?: string;
  }): Promise<{ outcome: ConfirmMobilePaymentOutcome }> {
    const transaction = await this.prisma.transaction.findUnique({
      where: { providerReference: input.providerReference },
    });

    if (!transaction) {
      return { outcome: 'not_found' };
    }

    if (transaction.status !== 'PENDING') {
      return { outcome: 'already_processed' };
    }

    const { tenantId } = transaction;

    if (input.status === 'FAILED') {
      const reason = input.failureReason ?? 'Payment declined by provider.';
      let failedThisCall = false;

      await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
        const result = await tx.transaction.updateMany({
          where: { id: transaction.id, tenantId, status: 'PENDING' },
          data: { status: 'FAILED', note: reason },
        });

        if (result.count === 0) {
          // Rejeu concurrent : déjà traité par un autre appel entre-temps.
          return;
        }

        failedThisCall = true;

        await this.outboxService.createEvent({
          tx,
          tenantId,
          aggregateType: 'transaction',
          aggregateId: transaction.id,
          eventType: 'mobile-payment.failed',
          payload: {
            transactionId: transaction.id,
            tenantId,
            amountCents: transaction.amountCents.toString(),
            payerPhone: transaction.payerPhone,
            reason,
          },
        });

        await this.auditService.record(
          {
            tenantId,
            actorUserId: transaction.recordedByUserId,
            action: 'transaction.mobile_payment.fail',
            targetType: 'transaction',
            targetId: transaction.id,
            metadata: { reason },
          },
          tx,
        );
      });

      return { outcome: failedThisCall ? 'failed' : 'already_processed' };
    }

    const tenant = await this.prisma.tenant.findUniqueOrThrow({
      where: { id: tenantId },
    });
    const amountCents = transaction.amountCents;
    let creditedThisCall = false;

    await this.prisma.$transaction(async (tx: Prisma.TransactionClient) => {
      // Solde relu sous verrou : un encaissement concurrent ne peut pas être
      // écrasé, et la valeur figée à l'initiation n'est jamais réutilisée.
      const student = await lockStudentBalance(
        tx,
        tenantId,
        transaction.studentId,
      );
      if (!student) {
        throw new NotFoundException('Student not found for this transaction.');
      }

      // La date d'application, pas celle de l'initiation : l'annulation s'appuie
      // sur cet ordre pour détecter les mouvements postérieurs.
      const postedAt = new Date();
      const previousDebtCents = student.debtCents;
      const { newDebtCents, newCreditCents } = applyPayment(
        student,
        amountCents,
      );

      const receiptNumber = this.receiptService.generateReceiptNumber(
        tenant.code,
      );
      const receiptHash = this.receiptService.generateReceiptHash({
        tenantId,
        studentId: student.id,
        recordedByUserId: transaction.recordedByUserId,
        amountCents,
        previousDebtCents,
        newDebtCents,
        currency: tenant.currency,
        receiptNumber,
        recordedAt: postedAt,
      });
      const qrCodeData = this.receiptService.generateReceiptData({
        id: transaction.id,
        receiptNumber,
        receiptHash,
        amountCents,
        previousDebtCents,
        newDebtCents,
        currency: tenant.currency,
        recordedAt: postedAt,
        student: {
          fullName: student.fullName,
          studentNumber: student.studentNumber,
        },
        recordedBy: { fullName: 'Mobile payment (Kadryza)' },
      });

      const result = await tx.transaction.updateMany({
        where: { id: transaction.id, tenantId, status: 'PENDING' },
        data: {
          status: 'POSTED',
          previousDebtCents,
          newDebtCents,
          receiptNumber,
          receiptHash,
          qrCodeData,
          recordedAt: postedAt,
        },
      });

      if (result.count === 0) {
        // Rejeu concurrent : déjà traité par un autre appel entre-temps.
        return;
      }

      creditedThisCall = true;

      await tx.student.update({
        where: { id: student.id },
        data: {
          debtCents: newDebtCents,
          creditCents: newCreditCents,
          lastPaymentAt: postedAt,
        },
      });

      await this.virtualAccountService.creditFromPayment(
        tenantId,
        amountCents,
        receiptNumber,
        tx,
        { relatedTransactionId: transaction.id },
      );

      await this.outboxService.createEvent({
        tx,
        tenantId,
        aggregateType: 'transaction',
        aggregateId: transaction.id,
        eventType: 'mobile-payment.confirmed',
        payload: {
          transactionId: transaction.id,
          tenantId,
          studentId: student.id,
          studentName: student.fullName,
          guardianPhone: student.guardianPhone,
          guardianName: student.guardianName,
          payerPhone: transaction.payerPhone,
          amountCents: amountCents.toString(),
          receiptNumber,
          recordedAt: postedAt.toISOString(),
        },
      });

      await this.auditService.record(
        {
          tenantId,
          actorUserId: transaction.recordedByUserId,
          action: 'transaction.mobile_payment.confirm',
          targetType: 'transaction',
          targetId: transaction.id,
          metadata: {
            studentId: student.id,
            amountCents: amountCents.toString(),
            previousDebtCents: previousDebtCents.toString(),
            newDebtCents: newDebtCents.toString(),
            creditCents: newCreditCents.toString(),
            receiptNumber,
          },
        },
        tx,
      );
    }, BALANCE_TRANSACTION_OPTIONS);

    return { outcome: creditedThisCall ? 'credited' : 'already_processed' };
  }

  private async markMobilePaymentInitiationFailed(
    transactionId: string,
    tenantId: string,
    reason: string,
  ): Promise<void> {
    await this.prisma.transaction.updateMany({
      where: { id: transactionId, tenantId, status: 'PENDING' },
      data: { status: 'FAILED', note: reason },
    });
  }

  /** Référence marchande unique transmise au prestataire de paiement. */
  private generatePaymentReference(): string {
    return `MP-${randomUUID()}`;
  }
}
