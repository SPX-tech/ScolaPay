import { randomUUID } from 'node:crypto';
import {
  BadRequestException,
  ConflictException,
  Inject,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { Prisma, WithdrawalStatus } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { VirtualAccountService } from './virtual-account.service';
import { AuditService } from '../audit/audit.service';
import { OutboxService } from '../outbox/outbox.service';
import { CreateWithdrawalDto } from './dto/create-withdrawal.dto';
import { CompleteWithdrawalDto } from './dto/complete-withdrawal.dto';
import { FailWithdrawalDto } from './dto/fail-withdrawal.dto';
import { PAYMENT_PROVIDER } from '../payment/payment-provider.interface';
import type { PaymentProvider } from '../payment/payment-provider.interface';

type WithdrawalWithRequester = Prisma.WithdrawalRequestGetPayload<{
  include: { requestedBy: { select: { id: true; fullName: true; phone: true } } };
}>;

/** Résultat d'une confirmation webhook, distingué pour un logging/monitoring clair. */
type ConfirmPayoutOutcome = 'completed' | 'failed' | 'already_processed' | 'not_found';

@Injectable()
export class WithdrawalsService {
  constructor(
    private readonly prisma: PrismaService,
    private readonly virtualAccount: VirtualAccountService,
    private readonly auditService: AuditService,
    private readonly outboxService: OutboxService,
    @Inject(PAYMENT_PROVIDER) private readonly paymentProvider: PaymentProvider,
  ) {}

  /**
   * Initie une demande de retrait :
   *  1) bloque les fonds sur le compte virtuel (échoue si disponible insuffisant) ;
   *  2) crée la demande en statut PENDING.
   * Les deux opérations sont dans une même transaction ACID : pas de blocage
   * de fonds sans demande, ni de demande sans blocage.
   *
   * Aucun appel à un prestataire externe n'est effectué ici (rails locaux).
   */
  async initiate(tenantId: string, userId: string, dto: CreateWithdrawalDto) {
    const amountCents = BigInt(dto.amountCents);
    const reference = this.generateReference();

    return this.prisma.$transaction(async (tx) => {
      await this.virtualAccount.reserveForWithdrawal(tenantId, amountCents, tx);

      return tx.withdrawalRequest.create({
        data: {
          tenantId,
          amountCents,
          method: dto.method,
          destination: dto.destination,
          reference,
          requestedById: userId,
          // status PENDING par défaut (schéma)
        },
      });
    });
  }

  /**
   * Fait passer la demande de PENDING à PROCESSING (prise en charge par un
   * opérateur), puis déclenche le virement sortant côté prestataire. Aucun
   * mouvement de fonds supplémentaire à l'étape PENDING->PROCESSING : le
   * blocage posé par `initiate` reste inchangé.
   *
   * L'appel au prestataire est volontairement hors de toute transaction
   * Prisma (latence réseau non bornée), et n'a lieu qu'une fois la
   * transition PROCESSING commitée en base. S'il échoue (synchrone ou
   * `status: FAILED`), on réutilise directement `fail()` : il fait déjà
   * exactement ce qu'il faut (libération du blocage, audit, notification),
   * sans dupliquer cette logique ici.
   */
  async process(tenantId: string, userId: string, id: string): Promise<WithdrawalWithRequester> {
    const withdrawal = await this.prisma.$transaction(async (tx) => {
      const w = await this.transitionOrThrow(tx, {
        id,
        tenantId,
        from: WithdrawalStatus.PENDING,
        to: WithdrawalStatus.PROCESSING,
      });

      await this.auditService.record(
        {
          tenantId,
          actorUserId: userId,
          action: 'withdrawal.process',
          targetType: 'withdrawal',
          targetId: id,
          before: { status: WithdrawalStatus.PENDING },
          after: { status: WithdrawalStatus.PROCESSING },
        },
        tx,
      );

      return w;
    });

    let payoutResult;
    try {
      payoutResult = await this.paymentProvider.payout({
        amountCents: withdrawal.amountCents,
        destination: withdrawal.destination,
        method: withdrawal.method,
        reference: withdrawal.reference,
      });
    } catch (error) {
      await this.fail(tenantId, userId, id, {
        reason: `Payout initiation failed: ${(error as Error).message}`,
      });
      throw new BadRequestException(
        `Payout could not be initiated: ${(error as Error).message}`,
      );
    }

    if (payoutResult.status === 'FAILED') {
      const reason = payoutResult.failureReason ?? 'Provider rejected the payout request.';
      await this.fail(tenantId, userId, id, { reason });
      throw new BadRequestException(reason);
    }

    return withdrawal;
  }

  /**
   * Fait passer la demande de PROCESSING à COMPLETED : débite définitivement
   * le compte virtuel (Ledger + décrément du blocage), journalise l'audit et
   * planifie la notification SMS, le tout dans une seule transaction ACID.
   */
  async complete(
    tenantId: string,
    userId: string,
    id: string,
    dto: CompleteWithdrawalDto,
  ): Promise<WithdrawalWithRequester> {
    return this.prisma.$transaction(async (tx) => {
      const withdrawal = await this.transitionOrThrow(tx, {
        id,
        tenantId,
        from: WithdrawalStatus.PROCESSING,
        to: WithdrawalStatus.COMPLETED,
        data: { externalReference: dto.externalReference },
      });

      await this.virtualAccount.completeWithdrawal(
        tenantId,
        withdrawal.amountCents,
        withdrawal.reference,
        tx,
        { relatedWithdrawalId: withdrawal.id },
      );

      await this.auditService.record(
        {
          tenantId,
          actorUserId: userId,
          action: 'withdrawal.complete',
          targetType: 'withdrawal',
          targetId: id,
          before: { status: WithdrawalStatus.PROCESSING },
          after: {
            status: WithdrawalStatus.COMPLETED,
            externalReference: dto.externalReference,
          },
        },
        tx,
      );

      await this.outboxService.createEvent({
        tx,
        tenantId,
        aggregateType: 'withdrawal',
        aggregateId: withdrawal.id,
        eventType: 'withdrawal.completed',
        payload: this.buildNotificationPayload(withdrawal, {
          externalReference: dto.externalReference,
        }),
      });

      return withdrawal;
    });
  }

  /**
   * Fait passer la demande de PROCESSING à FAILED : libère le blocage sans
   * mouvement de Ledger, journalise l'audit et planifie la notification SMS,
   * le tout dans une seule transaction ACID.
   */
  async fail(
    tenantId: string,
    userId: string,
    id: string,
    dto: FailWithdrawalDto,
  ): Promise<WithdrawalWithRequester> {
    return this.prisma.$transaction(async (tx) => {
      const withdrawal = await this.transitionOrThrow(tx, {
        id,
        tenantId,
        from: WithdrawalStatus.PROCESSING,
        to: WithdrawalStatus.FAILED,
        data: { failureReason: dto.reason },
      });

      await this.virtualAccount.releaseWithdrawal(tenantId, withdrawal.amountCents, tx);

      await this.auditService.record(
        {
          tenantId,
          actorUserId: userId,
          action: 'withdrawal.fail',
          targetType: 'withdrawal',
          targetId: id,
          before: { status: WithdrawalStatus.PROCESSING },
          after: { status: WithdrawalStatus.FAILED, failureReason: dto.reason },
        },
        tx,
      );

      await this.outboxService.createEvent({
        tx,
        tenantId,
        aggregateType: 'withdrawal',
        aggregateId: withdrawal.id,
        eventType: 'withdrawal.failed',
        payload: this.buildNotificationPayload(withdrawal, { reason: dto.reason }),
      });

      return withdrawal;
    });
  }

  /**
   * Confirme (ou échoue) un virement sortant suite au webhook du
   * prestataire. Ne prend pas de tenantId en entrée : c'est un webhook
   * public, sans contexte tenant (voir PaymentsWebhookController). La
   * demande est retrouvée via `reference`, unique globalement pour cette
   * raison précise.
   *
   * Délègue à `complete()`/`fail()`, qui embarquent déjà la garde
   * d'idempotence (`transitionOrThrow` gardé par le statut PROCESSING) :
   * un rejeu concurrent du webhook lève un ConflictException, capturé ici
   * et traduit en `already_processed` plutôt que remonté comme erreur.
   */
  async confirmPayout(input: {
    reference: string;
    status: 'SUCCESS' | 'FAILED';
    providerTransactionId?: string;
    failureReason?: string;
  }): Promise<{ outcome: ConfirmPayoutOutcome }> {
    const withdrawal = await this.prisma.withdrawalRequest.findUnique({
      where: { reference: input.reference },
    });

    if (!withdrawal) {
      return { outcome: 'not_found' };
    }

    if (withdrawal.status !== WithdrawalStatus.PROCESSING) {
      return { outcome: 'already_processed' };
    }

    try {
      if (input.status === 'FAILED') {
        await this.fail(withdrawal.tenantId, withdrawal.requestedById, withdrawal.id, {
          reason: input.failureReason ?? 'Payout declined by provider.',
        });
        return { outcome: 'failed' };
      }

      await this.complete(withdrawal.tenantId, withdrawal.requestedById, withdrawal.id, {
        externalReference: input.providerTransactionId ?? withdrawal.reference,
      });
      return { outcome: 'completed' };
    } catch (error) {
      if (error instanceof ConflictException) {
        // Rejeu concurrent : déjà traité par un autre appel entre-temps.
        return { outcome: 'already_processed' };
      }
      throw error;
    }
  }

  /**
   * Transition atomique : la mise à jour n'a lieu que si la demande est
   * encore dans le statut `from` (WHERE ... status = from). Deux appels
   * concurrents sur la même demande (double-clic, retry réseau) ne peuvent
   * donc la faire avancer qu'une seule fois ; le second échoue proprement
   * avec ConflictException au lieu de rejouer l'effet (idempotence).
   */
  private async transitionOrThrow(
    tx: Prisma.TransactionClient,
    params: {
      id: string;
      tenantId: string;
      from: WithdrawalStatus;
      to: WithdrawalStatus;
      data?: { externalReference?: string; failureReason?: string };
    },
  ): Promise<WithdrawalWithRequester> {
    const { id, tenantId, from, to, data } = params;

    const result = await tx.withdrawalRequest.updateMany({
      where: { id, tenantId, status: from },
      data: { status: to, ...data },
    });

    if (result.count === 0) {
      const existing = await tx.withdrawalRequest.findFirst({
        where: { id, tenantId },
        select: { status: true },
      });
      if (!existing) {
        throw new NotFoundException('Demande de retrait introuvable.');
      }
      throw new ConflictException(
        `Transition impossible : statut actuel ${existing.status}, attendu ${from}.`,
      );
    }

    return tx.withdrawalRequest.findFirstOrThrow({
      where: { id, tenantId },
      include: {
        requestedBy: { select: { id: true, fullName: true, phone: true } },
      },
    });
  }

  private buildNotificationPayload(
    withdrawal: WithdrawalWithRequester,
    extra: Record<string, unknown>,
  ) {
    return {
      withdrawalId: withdrawal.id,
      tenantId: withdrawal.tenantId,
      amountCents: withdrawal.amountCents.toString(),
      method: withdrawal.method,
      destination: withdrawal.destination,
      reference: withdrawal.reference,
      requesterId: withdrawal.requestedBy.id,
      requesterName: withdrawal.requestedBy.fullName,
      requesterPhone: withdrawal.requestedBy.phone,
      ...extra,
    };
  }

  /** Référence globalement unique pour la réconciliation externe. */
  private generateReference(): string {
    return `WD-${randomUUID()}`;
  }
}
