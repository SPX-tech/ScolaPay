import { BadRequestException, Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

/**
 * Accepte soit le client Prisma racine, soit un client de transaction
 * (`prisma.$transaction(async (tx) => ...)`), pour que ces opérations
 * puissent participer à une transaction ACID plus large.
 */
type PrismaClientLike = PrismaService | Prisma.TransactionClient;

export type VirtualAccountBalance = {
  balanceCents: bigint;
  pendingWithdrawalCents: bigint;
  /** Solde réellement retirable = balance - fonds déjà bloqués. */
  availableCents: bigint;
  currency: string;
};

/**
 * Gère le « float numérique » retirable d'une école (VirtualAccount).
 *
 * Invariant garanti à tout moment :
 *   availableCents = balanceCents - pendingWithdrawalCents >= 0
 *
 * Toutes les mutations utilisent des incréments atomiques en base
 * (`{ increment } / { decrement }`) pour rester correctes sous concurrence
 * au sein d'une même transaction.
 */
@Injectable()
export class VirtualAccountService {
  constructor(private readonly prisma: PrismaService) {}

  private db(tx?: PrismaClientLike): PrismaClientLike {
    return tx ?? this.prisma;
  }

  /** Récupère le compte virtuel du tenant, en le créant s'il n'existe pas. */
  async getOrCreate(tenantId: string, tx?: PrismaClientLike) {
    const db = this.db(tx);
    return db.virtualAccount.upsert({
      where: { tenantId },
      create: { tenantId },
      update: {},
    });
  }

  /** Solde courant + disponible (lecture seule). */
  async getBalance(tenantId: string): Promise<VirtualAccountBalance> {
    const account = await this.getOrCreate(tenantId);
    return {
      balanceCents: account.balanceCents,
      pendingWithdrawalCents: account.pendingWithdrawalCents,
      availableCents: account.balanceCents - account.pendingWithdrawalCents,
      currency: account.currency,
    };
  }

  /**
   * Crédite le solde lors d'un paiement numérique (MOBILE_PAYMENT).
   * Le cash physique n'alimente PAS le compte virtuel.
   */
  async creditFromPayment(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant du crédit doit être positif.');
    }
    const db = this.db(tx);
    await this.getOrCreate(tenantId, tx);
    return db.virtualAccount.update({
      where: { tenantId },
      data: { balanceCents: { increment: amountCents } },
    });
  }

  /**
   * Bloque des fonds pour un retrait (sans débiter le solde).
   * Échoue si le disponible est insuffisant.
   */
  async reserveForWithdrawal(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant du retrait doit être positif.');
    }
    const db = this.db(tx);
    const account = await this.getOrCreate(tenantId, tx);
    const available = account.balanceCents - account.pendingWithdrawalCents;
    if (available < amountCents) {
      throw new BadRequestException(
        `Solde disponible insuffisant : ${available} < ${amountCents}.`,
      );
    }
    return db.virtualAccount.update({
      where: { tenantId },
      data: { pendingWithdrawalCents: { increment: amountCents } },
    });
  }

  /**
   * Finalise un retrait : débite définitivement le solde et libère le blocage.
   */
  async completeWithdrawal(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    const db = this.db(tx);
    return db.virtualAccount.update({
      where: { tenantId },
      data: {
        balanceCents: { decrement: amountCents },
        pendingWithdrawalCents: { decrement: amountCents },
      },
    });
  }

  /**
   * Relâche un blocage (retrait échoué/annulé) : le solde redevient disponible,
   * sans mouvement sur balanceCents.
   */
  async releaseWithdrawal(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    const db = this.db(tx);
    return db.virtualAccount.update({
      where: { tenantId },
      data: { pendingWithdrawalCents: { decrement: amountCents } },
    });
  }
}
