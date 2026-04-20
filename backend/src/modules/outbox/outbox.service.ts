import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';

type CreateOutboxEventInput = {
  tx: Prisma.TransactionClient;
  tenantId: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  payload: unknown;
};

@Injectable()
export class OutboxService {
  constructor(private readonly prisma: PrismaService) {}

  async createEvent(input: CreateOutboxEventInput) {
    const { tx, tenantId, aggregateType, aggregateId, eventType, payload } = input;

    return tx.outboxEvent.create({
      data: {
        tenantId,
        aggregateType,
        aggregateId,
        eventType,
        payload: payload as object,
      },
    });
  }

  async listPendingEvents(tenantId: string, limit = 100) {
    return this.prisma.outboxEvent.findMany({
      where: {
        tenantId,
        status: {
          in: ['PENDING', 'FAILED'],
        },
        availableAt: {
          lte: new Date(),
        },
      },
      orderBy: {
        createdAt: 'asc',
      },
      take: Math.max(1, Math.min(limit, 500)),
    });
  }
}
