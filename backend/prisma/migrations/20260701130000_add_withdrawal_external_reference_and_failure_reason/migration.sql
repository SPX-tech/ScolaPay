-- AlterTable
ALTER TABLE "withdrawal_requests" ADD COLUMN     "externalReference" TEXT,
ADD COLUMN     "failureReason" TEXT;
