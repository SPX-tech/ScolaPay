-- AlterEnum: nouveaux types de transactions
ALTER TYPE "TransactionType" ADD VALUE 'MOBILE_PAYMENT';
ALTER TYPE "TransactionType" ADD VALUE 'WITHDRAWAL';

-- CreateEnum
CREATE TYPE "WithdrawalMethod" AS ENUM ('MOBILE_MONEY', 'BANK_TRANSFER');
CREATE TYPE "WithdrawalStatus" AS ENUM ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED');

-- CreateTable
CREATE TABLE "virtual_accounts" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "balanceCents" BIGINT NOT NULL DEFAULT 0,
    "pendingWithdrawalCents" BIGINT NOT NULL DEFAULT 0,
    "currency" TEXT NOT NULL DEFAULT 'XAF',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "virtual_accounts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "withdrawal_requests" (
    "id" TEXT NOT NULL,
    "tenantId" TEXT NOT NULL,
    "amountCents" BIGINT NOT NULL,
    "method" "WithdrawalMethod" NOT NULL,
    "destination" TEXT NOT NULL,
    "status" "WithdrawalStatus" NOT NULL DEFAULT 'PENDING',
    "reference" TEXT NOT NULL,
    "requestedById" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "withdrawal_requests_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "virtual_accounts_tenantId_key" ON "virtual_accounts"("tenantId");
CREATE UNIQUE INDEX "withdrawal_requests_reference_key" ON "withdrawal_requests"("reference");
CREATE INDEX "withdrawal_requests_tenantId_status_createdAt_idx" ON "withdrawal_requests"("tenantId", "status", "createdAt");

-- AddForeignKey
ALTER TABLE "virtual_accounts"
ADD CONSTRAINT "virtual_accounts_tenantId_fkey"
FOREIGN KEY ("tenantId") REFERENCES "tenants"("id")
ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "withdrawal_requests"
ADD CONSTRAINT "withdrawal_requests_tenantId_fkey"
FOREIGN KEY ("tenantId") REFERENCES "tenants"("id")
ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "withdrawal_requests"
ADD CONSTRAINT "withdrawal_requests_requestedById_fkey"
FOREIGN KEY ("requestedById") REFERENCES "users"("id")
ON DELETE RESTRICT ON UPDATE CASCADE;
