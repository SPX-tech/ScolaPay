import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';

type AuditTarget = 'user' | 'student' | 'transaction' | 'class' | 'outbox';

type AuditInput = {
  tenantId: string;
  actorUserId: string;
  action: string;
  targetType: AuditTarget;
  targetId: string;
  before?: unknown;
  after?: unknown;
  metadata?: Record<string, unknown>;
};

@Injectable()
export class AuditService {
  constructor(private readonly prisma: PrismaService) {}

  async record(input: AuditInput): Promise<void> {
    await this.prisma.auditLog.create({
      data: {
        tenantId: input.tenantId,
        actorUserId: input.actorUserId,
        action: input.action,
        targetType: input.targetType,
        targetId: input.targetId,
        before: toAuditJson(input.before) as any,
        after: toAuditJson(input.after) as any,
        metadata: toAuditJson(input.metadata) as any,
      },
    });
  }
}

function toAuditJson(value: unknown): unknown {
  if (value === undefined) {
    return undefined;
  }

  if (value === null || typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'bigint') {
    return value.toString();
  }

  if (value instanceof Date) {
    return value.toISOString();
  }

  if (Array.isArray(value)) {
    return value.map((item) => toAuditJson(item));
  }

  if (typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value)
        .map(([key, item]) => [key, toAuditJson(item)])
        .filter(([, item]) => item !== undefined),
    );
  }

  return String(value);
}
