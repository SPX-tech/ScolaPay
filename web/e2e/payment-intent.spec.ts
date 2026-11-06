import { expect, test, type Page } from '@playwright/test';
import { json, mockApi, restoreSession, STUDENT } from './fixtures';

type PaymentBody = { studentId: string; amountCents: string; idempotencyKey: string };

async function capturePayments(page: Page, failures: number) {
  const bodies: PaymentBody[] = [];
  await page.route('**/api/v1/ledger/record-cash-payment', async (route) => {
    const body = route.request().postDataJSON() as PaymentBody;
    bodies.push(body);
    if (bodies.length <= failures) {
      return json(route, 503, { message: 'Service momentanément indisponible' });
    }
    return json(route, 201, {
      transactionId: `txn_${bodies.length}`,
      studentId: body.studentId,
      amountCents: body.amountCents,
      previousDebtCents: STUDENT.debtCents,
      newDebtCents: '12250000',
      creditCents: '0',
      currency: 'XAF',
      receiptNumber: `RCP-LYCPL-20261106-0000000${bodies.length}`,
      receiptHash: 'a'.repeat(64),
      recordedAt: '2026-11-06T09:30:00.000Z',
    });
  });
  await page.route('**/api/v1/ledger/receipts/**', (route) => json(route, 200, { qrCode: null }));
  return bodies;
}

const amountInput = (page: Page) => page.getByPlaceholder('0', { exact: true });
const submit = (page: Page) => page.getByRole('button', { name: "Valider l'encaissement" });

test.beforeEach(async ({ page }) => {
  await restoreSession(page);
  await mockApi(page);
});

test('réutilise la clé après une erreur, puis en crée une pour un nouvel encaissement', async ({ page }) => {
  const bodies = await capturePayments(page, 1);
  await page.goto(`/dashboard/ledger?studentId=${STUDENT.id}`);

  await amountInput(page).fill('2500');
  await submit(page).click();
  await expect(page.getByText('Service momentanément indisponible')).toBeVisible();

  await submit(page).click();
  await expect(page.getByRole('heading', { name: 'Encaissement réussi !' })).toBeVisible();

  await page.getByRole('button', { name: 'Nouvel encaissement' }).click();
  await page.getByRole('button', { name: new RegExp(STUDENT.fullName) }).click();
  await amountInput(page).fill('2500');
  await submit(page).click();
  await expect(page.getByRole('heading', { name: 'Encaissement réussi !' })).toBeVisible();

  expect(bodies).toHaveLength(3);
  expect(bodies.every((body) => body.amountCents === '250000')).toBe(true);
  expect(bodies[1].idempotencyKey).toBe(bodies[0].idempotencyKey);
  expect(bodies[2].idempotencyKey).not.toBe(bodies[0].idempotencyKey);
});

test('change de clé si le montant est modifié après une erreur', async ({ page }) => {
  const bodies = await capturePayments(page, 1);
  await page.goto(`/dashboard/ledger?studentId=${STUDENT.id}`);

  await amountInput(page).fill('2500');
  await submit(page).click();
  await expect(page.getByText('Service momentanément indisponible')).toBeVisible();

  await amountInput(page).fill('3000');
  await submit(page).click();
  await expect(page.getByRole('heading', { name: 'Encaissement réussi !' })).toBeVisible();

  expect(bodies[1].amountCents).toBe('300000');
  expect(bodies[1].idempotencyKey).not.toBe(bodies[0].idempotencyKey);
});

test('transmet les montants en chaînes exactes au-delà de la précision JavaScript', async ({ page }) => {
  const bodies = await capturePayments(page, 0);
  await page.goto(`/dashboard/ledger?studentId=${STUDENT.id}`);

  await amountInput(page).fill('90071992547409.93');
  await submit(page).click();
  await expect(page.getByRole('heading', { name: 'Encaissement réussi !' })).toBeVisible();

  expect(bodies[0].amountCents).toBe('9007199254740993');
});
