import { Test, TestingModule } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import request from 'supertest';
import { AppModule } from './../src/app.module';
import { configureApp } from '../src/configure-app';

describe('ScolaPay E2E Tests', () => {
  let app: INestApplication;

  beforeAll(async () => {
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();

    app = moduleFixture.createNestApplication();
    configureApp(app);
    await app.init();
  });

  afterAll(async () => {
    await app.close();
  });

  describe('Health', () => {
    it('GET /api/v1/health should return 200', () => {
      return request(app.getHttpServer()).get('/api/v1/health').expect(200);
    });
  });

  describe('Authentication', () => {
    it('POST /api/v1/auth/login without body should return 400', () => {
      return request(app.getHttpServer())
        .post('/api/v1/auth/login')
        .send({})
        .expect(400);
    });

    it('POST /api/v1/auth/login with invalid credentials should return 401', () => {
      return request(app.getHttpServer())
        .post('/api/v1/auth/login')
        .send({
          tenantCode: 'NONEXISTENT',
          email: 'test@test.com',
          password: 'wrong-password',
        })
        .expect(401);
    });

    it('POST /api/v1/auth/login with partial body should return 400', () => {
      return request(app.getHttpServer())
        .post('/api/v1/auth/login')
        .send({ email: 'test@test.com' })
        .expect(400);
    });
  });

  describe('Protected Endpoints', () => {
    it('GET /api/v1/dashboard/summary without token should return 401', () => {
      return request(app.getHttpServer())
        .get('/api/v1/dashboard/summary')
        .expect(401);
    });

    it('GET /api/v1/students without token should return 401', () => {
      return request(app.getHttpServer()).get('/api/v1/students').expect(401);
    });

    it('GET /api/v1/transactions without token should return 401', () => {
      return request(app.getHttpServer())
        .get('/api/v1/transactions')
        .expect(401);
    });

    it('GET /api/v1/users without token should return 401', () => {
      return request(app.getHttpServer()).get('/api/v1/users').expect(401);
    });

    it('GET /api/v1/classes without token should return 401', () => {
      return request(app.getHttpServer()).get('/api/v1/classes').expect(401);
    });

    it('GET /api/v1/dashboard/reports/collections without token should return 401', () => {
      return request(app.getHttpServer())
        .get('/api/v1/dashboard/reports/collections')
        .expect(401);
    });

    it('GET /api/v1/dashboard/reports/students-debt without token should return 401', () => {
      return request(app.getHttpServer())
        .get('/api/v1/dashboard/reports/students-debt')
        .expect(401);
    });

    it('GET /api/v1/tenants/me without token should return 401', () => {
      return request(app.getHttpServer()).get('/api/v1/tenants/me').expect(401);
    });

    it('GET /api/v1/outbox/pending without token should return 401', () => {
      return request(app.getHttpServer())
        .get('/api/v1/outbox/pending')
        .expect(401);
    });
  });

  describe('API Prefix', () => {
    it('GET /api/v1/health should work (not /health)', () => {
      return request(app.getHttpServer()).get('/api/v1/health').expect(200);
    });

    it('GET /health without prefix should return 404', () => {
      return request(app.getHttpServer()).get('/health').expect(404);
    });

    it('GET /api/v1/nonexistent should return 404', () => {
      return request(app.getHttpServer())
        .get('/api/v1/nonexistent')
        .expect(404);
    });
  });
});
