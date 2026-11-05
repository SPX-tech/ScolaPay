import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { randomUUID } from 'node:crypto';
import { AppModule } from '../src/app.module';
import { configureApp } from '../src/configure-app';
import { PrismaService } from '../src/prisma/prisma.service';
import { LedgerService } from '../src/modules/ledger/ledger.service';
import { TransactionsService } from '../src/modules/transactions/transactions.service';
import { signAccessToken } from '../src/common/security/access-token.util';

describe('Intégrité financière PostgreSQL', () => {
  let app: INestApplication;
  let prisma: PrismaService;
  let ledger: LedgerService;
  let transactions: TransactionsService;
  let tenantId: string;
  let otherTenantId: string;
  let userId: string;
  let studentId: string;
  let otherClassId: string;
  let token: string;

  beforeAll(async () => {
    if (process.env.NODE_ENV !== 'test' || !new URL(process.env.DATABASE_URL!).pathname.endsWith('_test')) {
      throw new Error('Une base dédiée dont le nom se termine par _test est requise.');
    }
    const module = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = module.createNestApplication({ rawBody: true });
    configureApp(app);
    await app.init();
    prisma = module.get(PrismaService);
    ledger = module.get(LedgerService);
    transactions = module.get(TransactionsService);
    const tenant = await prisma.tenant.create({ data: { code: `T-${randomUUID()}`, name: 'Établissement de test' } });
    tenantId = tenant.id;
    const otherTenant = await prisma.tenant.create({ data: { code: `T-${randomUUID()}`, name: 'Autre établissement de test' } });
    otherTenantId = otherTenant.id;
    const user = await prisma.user.create({ data: { tenantId, email: 'admin@example.test', fullName: 'Administrateur Test', passwordHash: 'not-used-in-this-test', role: 'TENANT_ADMIN' } });
    userId = user.id;
    const otherClass = await prisma.schoolClass.create({ data: { tenantId: otherTenantId, name: 'Classe autre école', academicYear: '2026-2027' } });
    otherClassId = otherClass.id;
    token = signAccessToken({ sub: userId, tenantId, role: 'TENANT_ADMIN' }, process.env.ACCESS_TOKEN_SECRET!, 300);
  });

  beforeEach(async () => {
    const student = await prisma.student.create({ data: { tenantId, studentNumber: randomUUID(), fullName: 'Élève Test', debtCents: 10_000n } });
    studentId = student.id;
  });

  afterAll(async () => {
    if (prisma && tenantId) {
      const where = { tenantId: { in: [tenantId, otherTenantId] } };
      await prisma.ledgerEntry.deleteMany({ where });
      await prisma.withdrawalRequest.deleteMany({ where });
      await prisma.outboxEvent.deleteMany({ where });
      await prisma.auditLog.deleteMany({ where });
      await prisma.transaction.deleteMany({ where });
      await prisma.student.deleteMany({ where });
      await prisma.schoolClass.deleteMany({ where });
      await prisma.user.deleteMany({ where });
      await prisma.virtualAccount.deleteMany({ where });
      await prisma.tenant.deleteMany({ where: { id: { in: [tenantId, otherTenantId] } } });
    }
    await app?.close();
  });

  const pay = (id: string, amountCents = '1000', key = randomUUID()) => ledger.recordCashPayment(tenantId, userId, 'TENANT_ADMIN', { studentId: id, amountCents, idempotencyKey: key });

  it('ne perd aucun encaissement concurrent sur un même élève', async () => {
    await Promise.all(Array.from({ length: 10 }, () => pay(studentId)));
    const student = await prisma.student.findUniqueOrThrow({ where: { id: studentId } });
    expect(student.debtCents).toBe(0n);
    expect(student.creditCents).toBe(0n);
    const rows = await prisma.transaction.findMany({ where: { tenantId, studentId }, orderBy: { previousDebtCents: 'desc' } });
    expect(rows.map((row) => row.previousDebtCents)).toEqual([10000n, 9000n, 8000n, 7000n, 6000n, 5000n, 4000n, 3000n, 2000n, 1000n]);
  });

  it('ne comptabilise qu’une seule fois deux requêtes avec la même clé', async () => {
    const key = randomUUID();
    const results = await Promise.allSettled([pay(studentId, '1000', key), pay(studentId, '1000', key)]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect(await prisma.transaction.count({ where: { tenantId, idempotencyKey: key } })).toBe(1);
    expect((await prisma.student.findUniqueOrThrow({ where: { id: studentId } })).debtCents).toBe(9000n);
  });

  it('sérialise les dates et exécute le reporting SQL sur le schéma migré', async () => {
    const payment = await pay(studentId);
    const response = await request(app.getHttpServer()).get(`/api/v1/transactions/${payment.transactionId}`).auth(token, { type: 'bearer' }).expect(200);
    expect(response.body.amountCents).toBe('1000');
    expect(response.body.recordedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    const report = await request(app.getHttpServer()).get('/api/v1/dashboard/reports/collections').auth(token, { type: 'bearer' }).expect(200);
    expect(report.body.dailyCollections.length).toBeGreaterThan(0);
    expect(report.body.dailyCollections[0].date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('rejette une classe appartenant à un autre établissement', async () => {
    await request(app.getHttpServer()).post('/api/v1/students').auth(token, { type: 'bearer' }).send({ studentNumber: 'CROSS-TENANT', fullName: 'Élève Test', classId: otherClassId }).expect(404);
    await request(app.getHttpServer()).patch(`/api/v1/students/${studentId}`).auth(token, { type: 'bearer' }).send({ classId: otherClassId }).expect(404);
  });

  it('ignore tout identifiant d’établissement forgé dans les en-têtes', async () => {
    await request(app.getHttpServer()).get('/api/v1/students').auth(token, { type: 'bearer' }).set('x-tenant-id', otherTenantId).expect(401);
  });

  it('sérialise un paiement mobile et un encaissement cash concurrents', async () => {
    const mobile = await ledger.initializeMobilePayment(tenantId, userId, 'TENANT_ADMIN', { studentId, amountCents: '7000', payerPhone: '+23566000000' });
    await Promise.all([ledger.confirmMobilePayment({ providerReference: mobile.reference, status: 'SUCCESS' }), pay(studentId, '7000')]);
    const student = await prisma.student.findUniqueOrThrow({ where: { id: studentId } });
    expect(student.debtCents).toBe(0n);
    expect(student.creditCents).toBe(4000n);
  });

  it('refuse l’annulation générique d’un paiement mobile crédité', async () => {
    const mobile = await ledger.initializeMobilePayment(tenantId, userId, 'TENANT_ADMIN', { studentId, amountCents: '1000', payerPhone: '+23566000000' });
    await ledger.confirmMobilePayment({ providerReference: mobile.reference, status: 'SUCCESS' });
    await expect(transactions.voidTransaction(tenantId, mobile.transactionId, userId, 'TENANT_ADMIN', { reason: 'Test annulation' })).rejects.toThrow();
    expect((await prisma.transaction.findUniqueOrThrow({ where: { id: mobile.transactionId } })).status).toBe('POSTED');
  });

  it('refuse les dates de reporting invalides', async () => {
    await request(app.getHttpServer()).get('/api/v1/dashboard/reports/collections?startDate=invalid').auth(token, { type: 'bearer' }).expect(400);
    await request(app.getHttpServer()).get('/api/v1/dashboard/reports/collections?startDate=2026-02-01&endDate=2026-01-01').auth(token, { type: 'bearer' }).expect(400);
  });
});
