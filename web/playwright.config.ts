import { defineConfig, devices } from '@playwright/test';

const PORT = 3101;

export default defineConfig({
  testDir: './e2e',
  forbidOnly: Boolean(process.env.CI),
  // Un test financier instable doit être corrigé, pas rejoué jusqu'au vert.
  retries: 0,
  workers: 2,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Desktop Chrome'],
    baseURL: `http://127.0.0.1:${PORT}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  // Le build autonome assemblé par la CI, pas un serveur de développement.
  webServer: {
    command: 'node .next/standalone/server.js',
    url: `http://127.0.0.1:${PORT}`,
    reuseExistingServer: false,
    timeout: 30_000,
    env: { PORT: String(PORT), HOSTNAME: '127.0.0.1' },
  },
});
