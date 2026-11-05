import { expect, test, type Page } from '@playwright/test';
import { ACCOUNT_A, ACCOUNT_B, json, mockApi, restoreSession, type Account } from './fixtures';

const AMOUNT_A = /76\s543/;
const AMOUNT_B = /12\s345/;

async function signIn(page: Page, account: Account) {
  await page.locator('#tenantCode').fill(account.user.tenantId);
  await page.locator('#email').fill(account.user.email);
  await page.locator('#password').fill('mot-de-passe-de-test');
  await page.locator('button[type="submit"]').click();
  await page.waitForURL('**/dashboard');
}

test('ne montre jamais la trésorerie d’un établissement à la session suivante', async ({ page }) => {
  await mockApi(page);
  await page.route('**/api/v1/auth/login', (route) => {
    const { email } = route.request().postDataJSON() as { email: string };
    const account = email === ACCOUNT_B.user.email ? ACCOUNT_B : ACCOUNT_A;
    return json(route, 201, { accessToken: account.token, expiresInSeconds: 3600, user: account.user });
  });

  await page.goto('/login');
  await signIn(page, ACCOUNT_A);
  await page.getByRole('link', { name: 'Trésorerie' }).click();
  await expect(page.getByText(AMOUNT_A).first()).toBeVisible();

  // Navigation client uniquement : un rechargement viderait le cache et masquerait le défaut.
  await page.getByRole('button', { name: 'Déconnexion' }).click();
  await page.waitForURL('**/login');

  await page.route('**/api/v1/schools/balance', async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 800));
    await route.fallback();
  });
  await signIn(page, ACCOUNT_B);
  await page.getByRole('link', { name: 'Trésorerie' }).click();

  await expect(page.getByRole('heading', { name: 'Trésorerie' })).toBeVisible();
  await expect(page.getByText(AMOUNT_A)).toHaveCount(0);
  await expect(page.getByText(AMOUNT_B).first()).toBeVisible();
  await expect(page.getByText(AMOUNT_A)).toHaveCount(0);
});

test('suit un changement de compte effectué dans une autre fenêtre', async ({ page, context }) => {
  await restoreSession(page, ACCOUNT_A);
  await mockApi(page);
  await page.goto('/dashboard/finance');
  await expect(page.getByText(AMOUNT_A).first()).toBeVisible();

  const otherTab = await context.newPage();
  await otherTab.goto('/login');
  await otherTab.evaluate(({ token, user }) => {
    localStorage.setItem('access_token', token);
    localStorage.setItem('user', JSON.stringify(user));
  }, ACCOUNT_B);

  await expect(page.getByText(AMOUNT_B).first()).toBeVisible();
  await expect(page.getByText(AMOUNT_A)).toHaveCount(0);
});
