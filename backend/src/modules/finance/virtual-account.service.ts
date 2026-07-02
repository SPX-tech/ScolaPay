import { BadRequestException, Injectable } from '@nestjs/common';
import { LedgerEntryType, Prisma } from '@prisma/client';
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

type LockedAccount = {
  id: string;
  balanceCents: bigint;
  pendingWithdrawalCents: bigint;
  currency: string;
};

/** Contexte de traçabilité optionnel attaché à une ligne de Ledger. */
type LedgerContext = {
  relatedTransactionId?: string;
  relatedWithdrawalId?: string;
  metadata?: Prisma.InputJsonValue;
};

/**
 * Gère le « float numérique » retirable d'une école (VirtualAccount).
 *
 * Invariant garanti à tout moment :
 *   availableCents = balanceCents - pendingWithdrawalCents >= 0
 *
 * Le Ledger (LedgerEntry) est la source de vérité comptable : balanceCents
 * n'est qu'une projection dénormalisée, mise à jour UNIQUEMENT comme effet
 * de bord atomique d'une insertion de LedgerEntry, dans la même transaction.
 * Seuls les mouvements définitifs (crédit de paiement, retrait finalisé)
 * génèrent une ligne de Ledger ; les blocages (reserve/release) restent de
 * simples compteurs d'encumbrance, protégés par le même verrou de ligne.
 *
 * Toute mutation acquiert d'abord un verrou pessimiste (`SELECT ... FOR
 * UPDATE`) sur la ligne du compte, à l'intérieur d'une transaction Prisma,
 * pour fermer toute race check-then-act entre deux mutations concurrentes
 * du même tenant (ex. double-retrait).
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

  /** Solde courant + disponible (lecture seule, sans verrou). */
  async getBalance(tenantId: string): Promise<VirtualAccountBalance> {
    const account = await this.getOrCreate(tenantId);
    return {
      balanceCents: account.balanceCents,
      pendingWithdrawalCents: account.pendingWithdrawalCents,
      availableCents: account.balanceCents - account.pendingWithdrawalCents,
      currency: account.currency,
    };
  }

  /** Liste paginée du grand livre du tenant, plus récent en premier. */
  async listLedgerEntries(tenantId: string, query: { page?: number; limit?: number }) {
    const page = query.page ?? 1;
    const limit = query.limit ?? 50;
    const skip = (page - 1) * limit;

    const [data, total] = await Promise.all([
      this.prisma.ledgerEntry.findMany({
        where: { tenantId },
        orderBy: { createdAt: 'desc' },
        skip,
        take: limit,
      }),
      this.prisma.ledgerEntry.count({ where: { tenantId } }),
    ]);

    return {
      data,
      meta: { total, page, limit, totalPages: Math.ceil(total / limit) },
    };
  }

  /**
   * Verrouille la ligne du compte (`SELECT ... FOR UPDATE`) au sein de la
   * transaction `tx`. Doit toujours être appelé depuis l'intérieur d'une
   * transaction interactive : la durée de vie de `tx` détermine celle du
   * verrou. `lock_timeout` évite un blocage indéfini si une autre
   * transaction tient déjà le verrou (échec rapide plutôt que pool de
   * connexions épuisé).
   */
  private async lockAccount(
    tx: Prisma.TransactionClient,
    tenantId: string,
  ): Promise<LockedAccount> {
    await this.getOrCreate(tenantId, tx);
    await tx.$executeRaw`SET LOCAL lock_timeout = '5s'`;
    const rows = await tx.$queryRaw<LockedAccount[]>`
      SELECT "id", "balanceCents", "pendingWithdrawalCents", "currency"
      FROM "virtual_accounts"
      WHERE "tenantId" = ${tenantId}
      FOR UPDATE
    `;
    const account = rows[0];
    if (!account) {
      throw new BadRequestException(
        `Compte virtuel introuvable pour le tenant ${tenantId}.`,
      );
    }
    return account;
  }

  /** Insère la ligne de Ledger immuable d'un mouvement définitif. */
  private async appendLedgerEntry(
    tx: Prisma.TransactionClient,
    input: {
      tenantId: string;
      virtualAccountId: string;
      amountCents: bigint;
      balanceAfterCents: bigint;
      type: LedgerEntryType;
      reference: string;
      relatedTransactionId?: string;
      relatedWithdrawalId?: string;
      metadata?: Prisma.InputJsonValue;
    },
  ) {
    return tx.ledgerEntry.create({ data: input });
  }

  /**
   * Exécute `fn` dans la transaction fournie si elle existe déjà (pour
   * participer à une transaction ACID plus large, ex. création de la
   * WithdrawalRequest), sinon ouvre sa propre transaction.
   */
  private runInTransaction<T>(
    tx: PrismaClientLike | undefined,
    fn: (tx: Prisma.TransactionClient) => Promise<T>,
  ): Promise<T> {
    if (tx) {
      return fn(tx as Prisma.TransactionClient);
    }
    return this.prisma.$transaction((trx) => fn(trx));
  }

  /**
   * Crédite le solde lors d'un paiement numérique (MOBILE_PAYMENT) et
   * enregistre la ligne de Ledger correspondante (type PAYMENT) dans la
   * même transaction. Le cash physique n'alimente PAS le compte virtuel.
   *
   * `reference` est la clé d'idempotence côté appelant (ex. receiptNumber
   * de la Transaction d'origine) : rejouer le même crédit avec la même
   * reference échoue sur la contrainte unique du Ledger plutôt que de
   * créditer deux fois.
   */
  async creditFromPayment(
    tenantId: string,
    amountCents: bigint,
    reference: string,
    tx?: PrismaClientLike,
    context?: LedgerContext,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant du crédit doit être positif.');
    }
    return this.runInTransaction(tx, async (trx) => {
      const account = await this.lockAccount(trx, tenantId);
      const balanceAfterCents = account.balanceCents + amountCents;
      const updated = await trx.virtualAccount.update({
        where: { tenantId },
        data: { balanceCents: { increment: amountCents } },
      });
      await this.appendLedgerEntry(trx, {
        tenantId,
        virtualAccountId: account.id,
        amountCents,
        balanceAfterCents,
        type: LedgerEntryType.PAYMENT,
        reference,
        relatedTransactionId: context?.relatedTransactionId,
        relatedWithdrawalId: context?.relatedWithdrawalId,
        metadata: context?.metadata,
      });
      return updated;
    });
  }

  /**
   * Bloque des fonds pour un retrait (sans débiter le solde ni écrire au
   * Ledger : une réservation n'est pas un mouvement d'argent définitif,
   * seule sa finalisation via `completeWithdrawal` en est un).
   * Échoue si le disponible est insuffisant. Le verrou de ligne empêche
   * deux réservations concurrentes de lire le même disponible avant que
   * l'une des deux n'écrive (double-retrait).
   */
  async reserveForWithdrawal(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant du retrait doit être positif.');
    }
    return this.runInTransaction(tx, async (trx) => {
      const account = await this.lockAccount(trx, tenantId);
      const available = account.balanceCents - account.pendingWithdrawalCents;
      if (available < amountCents) {
        throw new BadRequestException(
          `Solde disponible insuffisant : ${available} < ${amountCents}.`,
        );
      }
      return trx.virtualAccount.update({
        where: { tenantId },
        data: { pendingWithdrawalCents: { increment: amountCents } },
      });
    });
  }

  /**
   * Finalise un retrait : débite définitivement le solde, libère le
   * blocage, et enregistre la ligne de Ledger correspondante (type
   * WITHDRAWAL, montant négatif) dans la même transaction.
   */
  async completeWithdrawal(
    tenantId: string,
    amountCents: bigint,
    reference: string,
    tx?: PrismaClientLike,
    context?: LedgerContext,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant du retrait doit être positif.');
    }
    return this.runInTransaction(tx, async (trx) => {
      const account = await this.lockAccount(trx, tenantId);
      if (account.pendingWithdrawalCents < amountCents) {
        throw new BadRequestException(
          `Blocage insuffisant pour finaliser ce retrait : ${account.pendingWithdrawalCents} < ${amountCents}.`,
        );
      }
      const balanceAfterCents = account.balanceCents - amountCents;
      const updated = await trx.virtualAccount.update({
        where: { tenantId },
        data: {
          balanceCents: { decrement: amountCents },
          pendingWithdrawalCents: { decrement: amountCents },
        },
      });
      await this.appendLedgerEntry(trx, {
        tenantId,
        virtualAccountId: account.id,
        amountCents: -amountCents,
        balanceAfterCents,
        type: LedgerEntryType.WITHDRAWAL,
        reference,
        relatedTransactionId: context?.relatedTransactionId,
        relatedWithdrawalId: context?.relatedWithdrawalId,
        metadata: context?.metadata,
      });
      return updated;
    });
  }

  /**
   * Relâche un blocage (retrait échoué/annulé) : le solde redevient
   * disponible, sans mouvement sur balanceCents ni ligne de Ledger.
   */
  async releaseWithdrawal(
    tenantId: string,
    amountCents: bigint,
    tx?: PrismaClientLike,
  ) {
    if (amountCents <= 0n) {
      throw new BadRequestException('Le montant à libérer doit être positif.');
    }
    return this.runInTransaction(tx, async (trx) => {
      const account = await this.lockAccount(trx, tenantId);
      if (account.pendingWithdrawalCents < amountCents) {
        throw new BadRequestException(
          `Blocage insuffisant à libérer : ${account.pendingWithdrawalCents} < ${amountCents}.`,
        );
      }
      return trx.virtualAccount.update({
        where: { tenantId },
        data: { pendingWithdrawalCents: { decrement: amountCents } },
      });
    });
  }
}
