-- CreateEnum
CREATE TYPE "LedgerEntryType" AS ENUM ('PAYMENT', 'WITHDRAWAL', 'FEE', 'ADJUSTMENT');

-- CreateTable
CREATE TABLE "ledger_entries" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "virtualAccountId" TEXT NOT NULL,
    "amountCents" BIGINT NOT NULL,
    "balanceAfterCents" BIGINT NOT NULL,
    "type" "LedgerEntryType" NOT NULL,
    "reference" TEXT NOT NULL,
    "relatedTransactionId" TEXT,
    "relatedWithdrawalId" TEXT,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "ledger_entries_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "ledger_entries_reference_key" ON "ledger_entries"("reference");

-- CreateIndex
CREATE INDEX "ledger_entries_tenantId_createdAt_idx" ON "ledger_entries"("tenantId", "createdAt");

-- CreateIndex
CREATE INDEX "ledger_entries_virtualAccountId_createdAt_idx" ON "ledger_entries"("virtualAccountId", "createdAt");

-- CreateIndex
CREATE INDEX "ledger_entries_tenantId_type_createdAt_idx" ON "ledger_entries"("tenantId", "type", "createdAt");

-- AddForeignKey
ALTER TABLE "ledger_entries" ADD CONSTRAINT "ledger_entries_tenantId_fkey" FOREIGN KEY ("tenantId") REFERENCES "tenants"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ledger_entries" ADD CONSTRAINT "ledger_entries_virtualAccountId_fkey" FOREIGN KEY ("virtualAccountId") REFERENCES "virtual_accounts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ledger_entries" ADD CONSTRAINT "ledger_entries_relatedTransactionId_fkey" FOREIGN KEY ("relatedTransactionId") REFERENCES "transactions"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "ledger_entries" ADD CONSTRAINT "ledger_entries_relatedWithdrawalId_fkey" FOREIGN KEY ("relatedWithdrawalId") REFERENCES "withdrawal_requests"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- Immutabilité du Grand Livre : "ledger_entries" est append-only.
-- Aucun code applicatif (présent ou futur) ni accès direct à la base ne
-- peut modifier ou supprimer une ligne postée. Garantie au niveau base de
-- données plutôt que par simple convention de code.
CREATE OR REPLACE FUNCTION ledger_entries_forbid_mutation()
RETURNS TRIGGER AS $$
BEGIN
  RAISE EXCEPTION 'ledger_entries est immuable (append-only) : % interdit sur la ligne id=%',
    TG_OP,
    OLD."id"
    USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_immutable
  BEFORE UPDATE OR DELETE ON "ledger_entries"
  FOR EACH ROW
  EXECUTE FUNCTION ledger_entries_forbid_mutation();

