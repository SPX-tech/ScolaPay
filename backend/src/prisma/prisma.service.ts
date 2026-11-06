import {
  Injectable,
  OnModuleInit,
  OnApplicationShutdown,
} from '@nestjs/common';
import { PrismaPg } from '@prisma/adapter-pg';
import { PrismaClient } from '../generated/prisma/client';

@Injectable()
export class PrismaService
  extends PrismaClient
  implements OnModuleInit, OnApplicationShutdown
{
  constructor() {
    const connectionString = process.env.DATABASE_URL;
    if (!connectionString) {
      throw new Error('DATABASE_URL is required.');
    }
    let schema: string;
    try {
      const url = new URL(connectionString);
      if (!['postgres:', 'postgresql:'].includes(url.protocol)) {
        throw new Error('Invalid protocol.');
      }
      schema = url.searchParams.get('schema') ?? 'public';
    } catch {
      throw new Error('Invalid DATABASE_URL configuration.');
    }
    super({
      adapter: new PrismaPg(
        {
          connectionString,
          max: 10,
          connectionTimeoutMillis: 5_000,
          idleTimeoutMillis: 30_000,
          statement_timeout: 15_000,
        },
        {
          schema,
        },
      ),
    });
  }

  async onModuleInit() {
    await this.$connect();
  }

  async onApplicationShutdown() {
    await this.$disconnect();
  }
}
