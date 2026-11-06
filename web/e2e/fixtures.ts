import type { Page, Route } from '@playwright/test';

export const STUDENT = {
  id: 'student_fixture_00000000001',
  tenantId: 'tenant-a',
  studentNumber: 'ETU-2026-0142',
  fullName: 'Achta Mahamat Abakar',
  guardianPhone: '+23566112233',
  guardianName: 'Mahamat Abakar',
  status: 'ACTIVE',
  debtCents: '12500000',
  creditCents: '0',
  classId: 'class_fixture_0000000000001',
  class: { id: 'class_fixture_0000000000001', name: 'Terminale scientifique D', academicYear: '2026-2027' },
};

const TRANSACTION = {
  id: 'txn_fixture_000000000000001',
  receiptNumber: 'RCP-LYCPL-20261106-A1B2C3D4',
  type: 'CASH_PAYMENT',
  status: 'POSTED',
  amountCents: '2500000',
  previousDebtCents: '15000000',
  newDebtCents: '12500000',
  currency: 'XAF',
  recordedAt: '2026-11-06T09:30:00.000Z',
  student: { id: STUDENT.id, studentNumber: STUDENT.studentNumber, fullName: STUDENT.fullName },
  recordedBy: { id: 'user-a', fullName: 'Fatimé Oumar Issa', email: 'caisse@lycee.test' },
};

export type Account = { token: string; user: { id: string; tenantId: string; fullName: string; email: string; role: string } };

export const ACCOUNT_A: Account = {
  token: 'token-tenant-a',
  user: { id: 'user-a', tenantId: 'tenant-a', fullName: 'Fatimé Oumar Issa', email: 'direction@lycee-a.test', role: 'TENANT_ADMIN' },
};

export const ACCOUNT_B: Account = {
  token: 'token-tenant-b',
  user: { id: 'user-b', tenantId: 'tenant-b', fullName: 'Moussa Hassan Ali', email: 'direction@lycee-b.test', role: 'TENANT_ADMIN' },
};

const page1 = (data: unknown[]) => ({ data, meta: { total: data.length, page: 1, limit: 50, totalPages: 1 } });

const balance = (availableCents: string) => ({
  balanceCents: availableCents,
  pendingWithdrawalCents: '0',
  availableCents,
  currency: 'XAF',
});

const RESPONSES: Record<string, unknown> = {
  'GET /dashboard/summary': {
    students: { total: 1284, active: 1250, withDebt: 412, totalDebtCents: '4872500000', totalCreditCents: '12500000' },
    transactions: { total: 9321, today: 37, thisMonth: 842 },
    cashCollected: { todayCents: '185000000', thisMonthCents: '4210000000' },
    recentTransactions: [TRANSACTION],
  },
  'GET /students': page1([STUDENT]),
  [`GET /students/${STUDENT.id}`]: STUDENT,
  'GET /classes': [{ id: STUDENT.class.id, name: STUDENT.class.name, academicYear: '2026-2027', status: 'ACTIVE', description: null, _count: { students: 34 } }],
  'GET /transactions': page1([TRANSACTION]),
  [`GET /transactions/student/${STUDENT.id}`]: page1([TRANSACTION]),
  'GET /users': [{ id: 'user-a', fullName: ACCOUNT_A.user.fullName, email: ACCOUNT_A.user.email, phone: '+23566000000', role: 'TENANT_ADMIN', status: 'ACTIVE', createdAt: '2026-09-01T08:00:00.000Z', lastLoginAt: '2026-11-06T08:00:00.000Z' }],
  'GET /tenants/me': { id: 'tenant-a', code: 'LYCPL', name: 'Lycée de la Paix', currency: 'XAF', timezone: 'Africa/Ndjamena', isActive: true, createdAt: '2026-09-01T08:00:00.000Z' },
  'GET /schools/withdrawals': page1([]),
  'GET /schools/ledger': page1([]),
  'GET /dashboard/reports/collections': {
    period: { startDate: '2026-10-31T23:00:00.000Z', endDate: '2026-11-06T22:59:59.999Z' },
    summary: { totalCollectedCents: '4210000000', transactionCount: 842 },
    dailyCollections: [{ date: '2026-11-06', totalCents: '185000000', transactionCount: 37 }],
    topPayers: [{ studentId: STUDENT.id, fullName: STUDENT.fullName, studentNumber: STUDENT.studentNumber, totalCents: '2500000', transactionCount: 1 }],
    debtOverview: { totalStudents: 1284, studentsWithDebt: 412, totalDebtCents: '4872500000', totalCreditCents: '12500000' },
  },
  'GET /dashboard/reports/students-debt': [{ id: STUDENT.id, studentNumber: STUDENT.studentNumber, fullName: STUDENT.fullName, className: STUDENT.class.name, debtCents: STUDENT.debtCents, creditCents: '0', guardianPhone: STUDENT.guardianPhone, status: 'ACTIVE' }],
};

export const json = (route: Route, status: number, body: unknown) =>
  route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });

/** Toute route non décrite échoue : une page ne peut pas passer sur une réponse inventée. */
export async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname.replace(/^\/api\/v1/, '');
    const key = `${request.method()} ${path}`;
    if (key === 'GET /schools/balance') {
      const isTenantB = request.headers().authorization === `Bearer ${ACCOUNT_B.token}`;
      return json(route, 200, balance(isTenantB ? '1234500' : '7654300'));
    }
    if (key in RESPONSES) {
      return json(route, 200, RESPONSES[key]);
    }
    return json(route, 501, { message: `Route non simulée : ${key}` });
  });
}

export async function restoreSession(page: Page, account: Account = ACCOUNT_A) {
  await page.addInitScript(({ token, user }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('user', JSON.stringify(user));
  }, account);
}
