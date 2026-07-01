-- AlterEnum
-- This migration adds more than one value to an enum.
-- With PostgreSQL versions 11 and earlier, this is not possible
-- in a single migration. This can be worked around by creating
-- multiple migrations, each migration adding only one value to
-- the enum.


ALTER TYPE "TransactionStatus" ADD VALUE 'PENDING';
ALTER TYPE "TransactionStatus" ADD VALUE 'FAILED';

-- AlterTable
ALTER TABLE "transactions" ADD COLUMN     "payerPhone" TEXT,
ADD COLUMN     "providerReference" TEXT,
ALTER COLUMN "receiptNumber" DROP NOT NULL,
ALTER COLUMN "receiptHash" DROP NOT NULL;

-- CreateIndex
CREATE UNIQUE INDEX "transactions_providerReference_key" ON "transactions"("providerReference");
