import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { APP_GUARD } from '@nestjs/core';
import { ThrottlerGuard, ThrottlerModule } from '@nestjs/throttler';
import request from 'supertest';
import { AuthController } from './../src/modules/auth/auth.controller';
import { AuthService } from './../src/modules/auth/auth.service';

/**
 * Test e2e hermétique du rate-limiting de connexion : boot minimal (le vrai
 * AuthController + son décorateur @Throttle(5/min)), AuthService remplacé par
 * un stub → aucune dépendance base de données. Prouve que la 6e tentative en
 * une minute est bien rejetée en 429.
 */
describe('Rate-limiting connexion (e2e)', () => {
  let app: INestApplication;

  const stubAuthService = {
    login: jest.fn().mockResolvedValue({
      accessToken: 'stub-token',
      expiresInSeconds: 1,
      user: { id: 'u1' },
    }),
  };

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [ThrottlerModule.forRoot([{ ttl: 60_000, limit: 120 }])],
      controllers: [AuthController],
      providers: [
        { provide: AuthService, useValue: stubAuthService },
        { provide: APP_GUARD, useClass: ThrottlerGuard },
      ],
    }).compile();

    app = moduleFixture.createNestApplication();
    app.setGlobalPrefix('api/v1');
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  it('autorise 5 tentatives puis bloque la 6e en 429', async () => {
    const url = '/api/v1/auth/login';
    const body = { tenantCode: 'DEMO001', email: 'a@b.c', password: 'x' };

    for (let i = 0; i < 5; i++) {
      const res = await request(app.getHttpServer()).post(url).send(body);
      expect(res.status).not.toBe(429);
    }

    const blocked = await request(app.getHttpServer()).post(url).send(body);
    expect(blocked.status).toBe(429);
  });
});
