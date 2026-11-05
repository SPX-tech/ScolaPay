import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { createHash, randomUUID } from 'node:crypto';
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
  const extraTenantIds: string[] = [];

  beforeAll(async () => {
    if (
      process.env.NODE_ENV !== 'test' ||
      !new URL(process.env.DATABASE_URL!).pathname.endsWith('_test')
    ) {
      throw new Error(
        'Une base dédiée dont le nom se termine par _test est requise.',
      );
    }
    const module = await Test.createTestingModule({
      imports: [AppModule],
    }).compile();
    app = module.createNestApplication({ rawBody: true });
    configureApp(app);
    await app.init();
    prisma = module.get(PrismaService);
    ledger = module.get(LedgerService);
    transactions = module.get(TransactionsService);
    const tenant = await prisma.tenant.create({
      data: { code: `T-${randomUUID()}`, name: 'Établissement de test' },
    });
    tenantId = tenant.id;
    const otherTenant = await prisma.tenant.create({
      data: { code: `T-${randomUUID()}`, name: 'Autre établissement de test' },
    });
    otherTenantId = otherTenant.id;
    const user = await prisma.user.create({
      data: {
        tenantId,
        email: 'admin@example.test',
        fullName: 'Administrateur Test',
        passwordHash: 'not-used-in-this-test',
        role: 'TENANT_ADMIN',
      },
    });
    userId = user.id;
    const otherClass = await prisma.schoolClass.create({
      data: {
        tenantId: otherTenantId,
        name: 'Classe autre école',
        academicYear: '2026-2027',
      },
    });
    otherClassId = otherClass.id;
    token = signAccessToken(
      { sub: userId, tenantId, role: 'TENANT_ADMIN' },
      process.env.ACCESS_TOKEN_SECRET!,
      300,
    );
  });

  beforeEach(async () => {
    const student = await prisma.student.create({
      data: {
        tenantId,
        studentNumber: randomUUID(),
        fullName: 'Élève Test',
        debtCents: 10_000n,
      },
    });
    studentId = student.id;
  });

  afterAll(async () => {
    try {
      if (prisma && tenantId) {
        const tenantIds = [tenantId, otherTenantId, ...extraTenantIds];
        const where = { tenantId: { in: tenantIds } };
        // Le grand livre est immuable par trigger. Le contournement reste confiné
        // à cette transaction, sur une base dont le nom est vérifié dans beforeAll.
        await prisma.$transaction(
          async (tx) => {
            await tx.$executeRaw`ALTER TABLE "ledger_entries" DISABLE TRIGGER ledger_entries_immutable`;
            await tx.ledgerEntry.deleteMany({ where });
            await tx.$executeRaw`ALTER TABLE "ledger_entries" ENABLE TRIGGER ledger_entries_immutable`;
            await tx.withdrawalRequest.deleteMany({ where });
            await tx.outboxEvent.deleteMany({ where });
            await tx.auditLog.deleteMany({ where });
            await tx.transaction.deleteMany({ where });
            await tx.student.deleteMany({ where });
            await tx.schoolClass.deleteMany({ where });
            await tx.user.deleteMany({ where });
            await tx.virtualAccount.deleteMany({ where });
            await tx.tenant.deleteMany({ where: { id: { in: tenantIds } } });
          },
          { timeout: 30_000 },
        );
      }
    } finally {
      await app?.close();
    }
  });

  const pay = (id: string, amountCents = '1000', key = randomUUID()) =>
    ledger.recordCashPayment(tenantId, userId, 'TENANT_ADMIN', {
      studentId: id,
      amountCents,
      idempotencyKey: key,
    });

  it('ne perd aucun encaissement concurrent sur un même élève', async () => {
    await Promise.all(Array.from({ length: 10 }, () => pay(studentId)));
    const student = await prisma.student.findUniqueOrThrow({
      where: { id: studentId },
    });
    expect(student.debtCents).toBe(0n);
    expect(student.creditCents).toBe(0n);
    const rows = await prisma.transaction.findMany({
      where: { tenantId, studentId },
      orderBy: { previousDebtCents: 'desc' },
    });
    expect(rows.map((row) => row.previousDebtCents)).toEqual([
      10000n,
      9000n,
      8000n,
      7000n,
      6000n,
      5000n,
      4000n,
      3000n,
      2000n,
      1000n,
    ]);
  });

  it('ne comptabilise qu’une seule fois deux requêtes avec la même clé', async () => {
    const key = randomUUID();
    const results = await Promise.allSettled([
      pay(studentId, '1000', key),
      pay(studentId, '1000', key),
    ]);
    expect(
      results.filter((result) => result.status === 'fulfilled'),
    ).toHaveLength(1);
    expect(
      await prisma.transaction.count({
        where: { tenantId, idempotencyKey: key },
      }),
    ).toBe(1);
    expect(
      (await prisma.student.findUniqueOrThrow({ where: { id: studentId } }))
        .debtCents,
    ).toBe(9000n);
  });

  it('sérialise les dates et exécute le reporting SQL sur le schéma migré', async () => {
    const payment = await pay(studentId);
    const response = await request(app.getHttpServer())
      .get(`/api/v1/transactions/${payment.transactionId}`)
      .auth(token, { type: 'bearer' })
      .expect(200);
    expect(response.body.amountCents).toBe('1000');
    expect(response.body.recordedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    const report = await request(app.getHttpServer())
      .get('/api/v1/dashboard/reports/collections')
      .auth(token, { type: 'bearer' })
      .expect(200);
    expect(report.body.dailyCollections.length).toBeGreaterThan(0);
    expect(report.body.dailyCollections[0].date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('rejette une classe appartenant à un autre établissement', async () => {
    await request(app.getHttpServer())
      .post('/api/v1/students')
      .auth(token, { type: 'bearer' })
      .send({
        studentNumber: 'CROSS-TENANT',
        fullName: 'Élève Test',
        classId: otherClassId,
      })
      .expect(404);
    await request(app.getHttpServer())
      .patch(`/api/v1/students/${studentId}`)
      .auth(token, { type: 'bearer' })
      .send({ classId: otherClassId })
      .expect(404);
  });

  it('ignore tout identifiant d’établissement forgé dans les en-têtes', async () => {
    await request(app.getHttpServer())
      .get('/api/v1/students')
      .auth(token, { type: 'bearer' })
      .set('x-tenant-id', otherTenantId)
      .expect(401);
  });

  it('sérialise un paiement mobile et un encaissement cash concurrents', async () => {
    const mobile = await ledger.initializeMobilePayment(
      tenantId,
      userId,
      'TENANT_ADMIN',
      { studentId, amountCents: '7000', payerPhone: '+23566000000' },
    );
    await Promise.all([
      ledger.confirmMobilePayment({
        providerReference: mobile.reference,
        status: 'SUCCESS',
      }),
      pay(studentId, '7000'),
    ]);
    const student = await prisma.student.findUniqueOrThrow({
      where: { id: studentId },
    });
    expect(student.debtCents).toBe(0n);
    expect(student.creditCents).toBe(4000n);
  });

  it('refuse l’annulation générique d’un paiement mobile crédité', async () => {
    const mobile = await ledger.initializeMobilePayment(
      tenantId,
      userId,
      'TENANT_ADMIN',
      { studentId, amountCents: '1000', payerPhone: '+23566000000' },
    );
    await ledger.confirmMobilePayment({
      providerReference: mobile.reference,
      status: 'SUCCESS',
    });
    await expect(
      transactions.voidTransaction(
        tenantId,
        mobile.transactionId,
        userId,
        'TENANT_ADMIN',
        { reason: 'Test annulation' },
      ),
    ).rejects.toThrow();
    expect(
      (
        await prisma.transaction.findUniqueOrThrow({
          where: { id: mobile.transactionId },
        })
      ).status,
    ).toBe('POSTED');
  });

  it('refuse les dates de reporting invalides', async () => {
    await request(app.getHttpServer())
      .get('/api/v1/dashboard/reports/collections?startDate=invalid')
      .auth(token, { type: 'bearer' })
      .expect(400);
    await request(app.getHttpServer())
      .get(
        '/api/v1/dashboard/reports/collections?startDate=2026-02-01&endDate=2026-01-01',
      )
      .auth(token, { type: 'bearer' })
      .expect(400);
  });

  it('audite et publie l’encaissement dans sa transaction', async () => {
    const payment = await pay(studentId);
    expect(
      await prisma.auditLog.count({
        where: {
          tenantId,
          targetId: payment.transactionId,
          action: 'transaction.cash_payment.record',
        },
      }),
    ).toBe(1);
    expect(
      await prisma.outboxEvent.count({
        where: {
          tenantId,
          aggregateId: payment.transactionId,
          eventType: 'cash-payment.recorded',
        },
      }),
    ).toBe(1);
  });

  it('produit une empreinte de reçu recalculable depuis la ligne stockée', async () => {
    const payment = await pay(studentId);
    const row = await prisma.transaction.findUniqueOrThrow({
      where: { id: payment.transactionId },
    });
    const expected = createHash('sha256')
      .update(
        [
          tenantId,
          studentId,
          userId,
          row.amountCents,
          row.previousDebtCents,
          row.newDebtCents,
          row.currency,
          row.receiptNumber,
          row.recordedAt.toISOString(),
        ].join('|'),
      )
      .digest('hex');
    expect(row.receiptHash).toBe(expected);
  });

  it('n’annule qu’une fois un encaissement cash, même en concurrence', async () => {
    const payment = await pay(studentId, '4000');
    const results = await Promise.allSettled(
      [1, 2, 3].map(() =>
        transactions.voidTransaction(
          tenantId,
          payment.transactionId,
          userId,
          'TENANT_ADMIN',
          { reason: 'Double saisie' },
        ),
      ),
    );
    expect(
      results.filter((result) => result.status === 'fulfilled'),
    ).toHaveLength(1);
    const student = await prisma.student.findUniqueOrThrow({
      where: { id: studentId },
    });
    expect(student.debtCents).toBe(10_000n);
    expect(student.creditCents).toBe(0n);
    expect(
      await prisma.auditLog.count({
        where: {
          tenantId,
          targetId: payment.transactionId,
          action: 'transaction.void',
        },
      }),
    ).toBe(1);
  });

  it('retire uniquement le trop-perçu du crédit à l’annulation', async () => {
    const payment = await pay(studentId, '12500');
    expect(
      (await prisma.student.findUniqueOrThrow({ where: { id: studentId } }))
        .creditCents,
    ).toBe(2_500n);
    await transactions.voidTransaction(
      tenantId,
      payment.transactionId,
      userId,
      'TENANT_ADMIN',
      { reason: 'Montant erroné' },
    );
    const student = await prisma.student.findUniqueOrThrow({
      where: { id: studentId },
    });
    expect(student.debtCents).toBe(10_000n);
    expect(student.creditCents).toBe(0n);
  });

  it('ne laisse pas une annulation cash effacer un paiement mobile confirmé après elle', async () => {
    const mobile = await ledger.initializeMobilePayment(
      tenantId,
      userId,
      'TENANT_ADMIN',
      { studentId, amountCents: '3000', payerPhone: '+23566000000' },
    );
    const cash = await pay(studentId, '1000');
    await ledger.confirmMobilePayment({
      providerReference: mobile.reference,
      status: 'SUCCESS',
    });
    await expect(
      transactions.voidTransaction(
        tenantId,
        cash.transactionId,
        userId,
        'TENANT_ADMIN',
        { reason: 'Test annulation' },
      ),
    ).rejects.toThrow();
    const student = await prisma.student.findUniqueOrThrow({
      where: { id: studentId },
    });
    expect(student.debtCents).toBe(6_000n);
    expect(
      (
        await prisma.transaction.findUniqueOrThrow({
          where: { id: cash.transactionId },
        })
      ).status,
    ).toBe('POSTED');
  });

  it('refuse d’annuler un encaissement si le solde a été modifié depuis', async () => {
    const payment = await pay(studentId, '1000');
    await request(app.getHttpServer())
      .patch(`/api/v1/students/${studentId}`)
      .auth(token, { type: 'bearer' })
      .send({ debtCents: '500' })
      .expect(200);
    await expect(
      transactions.voidTransaction(
        tenantId,
        payment.transactionId,
        userId,
        'TENANT_ADMIN',
        { reason: 'Test annulation' },
      ),
    ).rejects.toThrow();
    expect(
      (await prisma.student.findUniqueOrThrow({ where: { id: studentId } }))
        .debtCents,
    ).toBe(500n);
  });

  it('ne crédite qu’une fois deux webhooks de confirmation concurrents', async () => {
    const mobile = await ledger.initializeMobilePayment(
      tenantId,
      userId,
      'TENANT_ADMIN',
      { studentId, amountCents: '2000', payerPhone: '+23566000000' },
    );
    const body = {
      type: 'PAYMENT',
      reference: mobile.reference,
      status: 'SUCCESS',
    };
    const responses = await Promise.all(
      [1, 2].map(() =>
        request(app.getHttpServer())
          .post('/api/v1/payments/webhook-kadryza')
          .send(body)
          .expect(201),
      ),
    );
    expect(responses.map((response) => response.body.outcome).sort()).toEqual([
      'already_processed',
      'credited',
    ]);
    expect(
      await prisma.ledgerEntry.count({
        where: { tenantId, relatedTransactionId: mobile.transactionId },
      }),
    ).toBe(1);
    expect(
      (await prisma.student.findUniqueOrThrow({ where: { id: studentId } }))
        .debtCents,
    ).toBe(8_000n);
  });

  it('rejette un webhook de statut inconnu sans aucune mutation', async () => {
    const mobile = await ledger.initializeMobilePayment(
      tenantId,
      userId,
      'TENANT_ADMIN',
      { studentId, amountCents: '2000', payerPhone: '+23566000000' },
    );
    await request(app.getHttpServer())
      .post('/api/v1/payments/webhook-kadryza')
      .send({ type: 'PAYMENT', reference: mobile.reference, status: 'PAID' })
      .expect(400);
    expect(
      (
        await prisma.transaction.findUniqueOrThrow({
          where: { id: mobile.transactionId },
        })
      ).status,
    ).toBe('PENDING');
    expect(
      (await prisma.student.findUniqueOrThrow({ where: { id: studentId } }))
        .debtCents,
    ).toBe(10_000n);
  });

  it('compte les journées dans le fuseau de l’établissement et inclut la journée finale', async () => {
    const reportTenant = await prisma.tenant.create({
      data: {
        code: `T-${randomUUID()}`,
        name: 'Établissement rapport',
        timezone: 'Africa/Ndjamena',
      },
    });
    extraTenantIds.push(reportTenant.id);
    const reportUser = await prisma.user.create({
      data: {
        tenantId: reportTenant.id,
        email: 'rapport@example.test',
        fullName: 'Rapport Test',
        passwordHash: 'not-used-in-this-test',
        role: 'TENANT_ADMIN',
      },
    });
    const reportStudent = await prisma.student.create({
      data: {
        tenantId: reportTenant.id,
        studentNumber: randomUUID(),
        fullName: 'Élève Rapport',
      },
    });
    const insert = (recordedAt: string, amountCents: bigint) =>
      prisma.transaction.create({
        data: {
          tenantId: reportTenant.id,
          studentId: reportStudent.id,
          recordedByUserId: reportUser.id,
          type: 'CASH_PAYMENT',
          status: 'POSTED',
          amountCents,
          previousDebtCents: 0n,
          newDebtCents: 0n,
          recordedAt: new Date(recordedAt),
        },
      });
    await insert('2026-01-31T22:59:00.000Z', 100n); // 23 h 59 le 31 janvier à N'Djamena
    await insert('2026-01-31T23:30:00.000Z', 200n); // 0 h 30 le 1er février à N'Djamena
    await insert('2026-02-01T22:30:00.000Z', 400n); // 23 h 30 le 1er février à N'Djamena
    const reportToken = signAccessToken(
      { sub: reportUser.id, tenantId: reportTenant.id, role: 'TENANT_ADMIN' },
      process.env.ACCESS_TOKEN_SECRET!,
      300,
    );
    const report = await request(app.getHttpServer())
      .get(
        '/api/v1/dashboard/reports/collections?startDate=2026-02-01&endDate=2026-02-01',
      )
      .auth(reportToken, { type: 'bearer' })
      .expect(200);
    expect(report.body.dailyCollections).toEqual([
      { date: '2026-02-01', totalCents: '600', transactionCount: 2 },
    ]);
    expect(report.body.summary).toEqual({
      totalCollectedCents: '600',
      transactionCount: 2,
    });
    const january = await request(app.getHttpServer())
      .get(
        '/api/v1/dashboard/reports/collections?startDate=2026-01-31&endDate=2026-01-31',
      )
      .auth(reportToken, { type: 'bearer' })
      .expect(200);
    expect(january.body.dailyCollections).toEqual([
      { date: '2026-01-31', totalCents: '100', transactionCount: 1 },
    ]);
    const list = await request(app.getHttpServer())
      .get('/api/v1/transactions?startDate=2026-02-01&endDate=2026-02-01')
      .auth(reportToken, { type: 'bearer' })
      .expect(200);
    expect(list.body.meta.total).toBe(2);
  });

  it('refuse les filtres de transactions invalides', async () => {
    await request(app.getHttpServer())
      .get('/api/v1/transactions?startDate=invalid')
      .auth(token, { type: 'bearer' })
      .expect(400);
    await request(app.getHttpServer())
      .get('/api/v1/transactions?startDate=2026-02-01&endDate=2026-01-01')
      .auth(token, { type: 'bearer' })
      .expect(400);
    await request(app.getHttpServer())
      .get('/api/v1/transactions?status=UNKNOWN')
      .auth(token, { type: 'bearer' })
      .expect(400);
    await request(app.getHttpServer())
      .get('/api/v1/transactions?limit=100000')
      .auth(token, { type: 'bearer' })
      .expect(400);
  });
});
