import { expect, test } from '@playwright/test';
import { json, mockApi, restoreSession } from './fixtures';

test.beforeEach(async ({ page }) => {
  await restoreSession(page);
  await mockApi(page);
});

test.describe('dans un fuseau à l’ouest de l’établissement', () => {
  test.use({ timezoneId: 'America/New_York' });

  test('affiche la journée calculée par l’établissement, sans la décaler', async ({ page }) => {
    await page.goto('/dashboard/reports');
    await expect(page.getByRole('cell', { name: '6 nov. 2026', exact: true })).toBeVisible();
  });
});

test('explique pourquoi une période est refusée', async ({ page }) => {
  await page.route('**/api/v1/dashboard/reports/collections**', (route) =>
    json(route, 400, { message: 'La date de début doit précéder la date de fin.' }),
  );
  await page.goto('/dashboard/reports');
  await expect(page.locator('main').getByRole('alert')).toHaveText('La date de début doit précéder la date de fin.');
});

test('explique pourquoi les transactions ne se chargent pas', async ({ page }) => {
  await page.route('**/api/v1/transactions?**', (route) => json(route, 400, { message: 'Date invalide : "x".' }));
  await page.goto('/dashboard/transactions');
  await expect(page.locator('main').getByRole('alert')).toHaveText('Date invalide : "x".');
  await expect(page.getByText('Aucune transaction')).toBeVisible();
});

test('ne propose l’annulation que pour un paiement cash', async ({ page }) => {
  const row = (id: string, type: string, status: string) => ({
    id,
    receiptNumber: id,
    type,
    status,
    amountCents: '100000',
    newDebtCents: '0',
    recordedAt: '2026-11-06T09:30:00.000Z',
  });
  await page.route('**/api/v1/transactions?**', (route) =>
    json(route, 200, {
      data: [row('RCP-CASH', 'CASH_PAYMENT', 'POSTED'), row('RCP-MOBILE', 'MOBILE_PAYMENT', 'POSTED'), row('RCP-ECHEC', 'MOBILE_PAYMENT', 'FAILED')],
      meta: { total: 3, page: 1, limit: 20, totalPages: 1 },
    }),
  );
  await page.goto('/dashboard/transactions');

  const voidButton = (name: string) => page.getByRole('row', { name: new RegExp(name) }).getByTitle('Annuler cette transaction');
  await expect(voidButton('RCP-CASH')).toBeVisible();
  await expect(voidButton('RCP-MOBILE')).toHaveCount(0);
  await expect(page.getByRole('row', { name: /RCP-MOBILE/ })).toContainText('Paiement mobile');
  await expect(page.getByRole('row', { name: /RCP-ECHEC/ })).toContainText('Échoué');
});
