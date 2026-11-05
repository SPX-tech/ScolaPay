import { Prisma } from '../../generated/prisma/client';

export type LockedStudent = {
  id: string;
  tenantId: string;
  fullName: string;
  studentNumber: string;
  guardianPhone: string | null;
  guardianName: string | null;
  status: string;
  debtCents: bigint;
  creditCents: bigint;
};

/**
 * `timeout` reste supérieur au `lock_timeout` : un verrou indisponible échoue
 * sur l'erreur PostgreSQL explicite plutôt que sur l'expiration de la transaction.
 */
export const BALANCE_TRANSACTION_OPTIONS = {
  maxWait: 5_000,
  timeout: 15_000,
} as const;

/**
 * Sérialise toute écriture sur le solde d'un élève. Tout calcul de dette ou de
 * crédit doit lire les valeurs retournées ici, dans la même transaction.
 * Ordre de verrouillage : élève puis compte virtuel, pour éviter les interblocages.
 */
export async function lockStudentBalance(
  tx: Prisma.TransactionClient,
  tenantId: string,
  studentId: string,
): Promise<LockedStudent | null> {
  await tx.$executeRaw`SET LOCAL lock_timeout = '5s'`;
  const rows = await tx.$queryRaw<LockedStudent[]>`
    SELECT
      "id",
      "tenantId",
      "fullName",
      "studentNumber",
      "guardianPhone",
      "guardianName",
      "status"::text AS "status",
      "debtCents",
      "creditCents"
    FROM "students"
    WHERE "id" = ${studentId} AND "tenantId" = ${tenantId}
    FOR UPDATE
  `;
  return rows[0] ?? null;
}
