import { expect, test, type Page } from '@playwright/test';
import { mockApi, restoreSession, STUDENT } from './fixtures';

const WIDTHS = [360, 768, 1440];

const PUBLIC_PAGES = ['/', '/login'];
const DASHBOARD_PAGES = [
  '/dashboard',
  '/dashboard/students',
  `/dashboard/students/${STUDENT.id}`,
  '/dashboard/classes',
  `/dashboard/ledger?studentId=${STUDENT.id}`,
  '/dashboard/transactions',
  '/dashboard/finance',
  '/dashboard/reports',
  '/dashboard/users',
  '/dashboard/settings',
];

const slug = (path: string) => path.replace(/\?.*$/, '').replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '') || 'accueil';

async function expectNoHorizontalOverflow(page: Page) {
  const overflow = await page.evaluate(() => {
    const { scrollWidth, clientWidth } = document.documentElement;
    return { scrollWidth, clientWidth };
  });
  expect(overflow.scrollWidth, `largeur ${overflow.clientWidth}`).toBeLessThanOrEqual(overflow.clientWidth);
}

for (const width of WIDTHS) {
  test.describe(`largeur ${width} px`, () => {
    test.use({ viewport: { width, height: 900 } });

    for (const path of [...PUBLIC_PAGES, ...DASHBOARD_PAGES]) {
      test(`${path} ne déborde pas`, async ({ page }, testInfo) => {
        const errors: string[] = [];
        page.on('pageerror', (error) => errors.push(error.message));
        await mockApi(page);
        if (path.startsWith('/dashboard')) {
          await restoreSession(page);
        }
        await page.goto(path);
        await page.waitForLoadState('networkidle');

        await expectNoHorizontalOverflow(page);
        expect(errors).toEqual([]);
        await page.screenshot({
          path: testInfo.outputPath(`${slug(path)}-${width}.png`),
          fullPage: true,
        });
      });
    }
  });
}

test.describe('navigation mobile', () => {
  test.use({ viewport: { width: 360, height: 800 } });

  test('la page d’accueil ouvre et ferme son menu', async ({ page }) => {
    await page.goto('/');
    const nav = page.locator('#mobile-navigation');
    await expect(nav).toBeHidden();
    await page.getByRole('button', { name: 'Ouvrir la navigation' }).click();
    await expect(nav).toBeVisible();
    await page.getByRole('button', { name: 'Fermer la navigation' }).click();
    await expect(nav).toBeHidden();
  });

  test('le tableau de bord ouvre son tiroir et y navigue', async ({ page }) => {
    await restoreSession(page);
    await mockApi(page);
    await page.goto('/dashboard');
    const link = page.getByRole('link', { name: 'Transactions', exact: true }).first();
    await expect(link).not.toBeInViewport();
    await page.locator('main button').first().click();
    await expect(link).toBeInViewport();
    await link.click();
    await page.waitForURL('**/dashboard/transactions');
    await expect(page.getByRole('heading', { name: 'Transactions' })).toBeVisible();
  });
});
