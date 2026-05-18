CREATE TABLE "audit_logs" (
    "id" BIGSERIAL NOT NULL,
    "tenantId" TEXT NOT NULL,
    "actorUserId" TEXT NOT NULL,
    "action" TEXT NOT NULL,
    "targetType" TEXT NOT NULL,
    "targetId" TEXT NOT NULL,
    "before" JSONB,
    "after" JSONB,
    "metadata" JSONB,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "audit_logs_pkey" PRIMARY KEY ("id")
);

CREATE INDEX "audit_logs_tenantId_createdAt_idx" ON "audit_logs"("tenantId", "createdAt");
CREATE INDEX "audit_logs_tenantId_actorUserId_createdAt_idx" ON "audit_logs"("tenantId", "actorUserId", "createdAt");
CREATE INDEX "audit_logs_tenantId_targetType_targetId_idx" ON "audit_logs"("tenantId", "targetType", "targetId");

ALTER TABLE "audit_logs"
ADD CONSTRAINT "audit_logs_tenantId_fkey"
FOREIGN KEY ("tenantId") REFERENCES "tenants"("id")
ON DELETE RESTRICT ON UPDATE CASCADE;
