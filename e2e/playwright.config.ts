import { defineConfig, devices } from '@playwright/test';

// The stack (compose infra, API, web, admin) is started outside Playwright:
// see docs/runbooks/local-dev.md and .github/workflows/e2e.yml.
export const WEB_URL = process.env.E2E_WEB_URL ?? 'http://localhost:3000';
export const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:3001';

export default defineConfig({
  testDir: './tests',
  // The slice is one ordered journey sharing state, so run serially.
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  timeout: 120_000,
  expect: { timeout: 20_000 },
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: WEB_URL,
    actionTimeout: 20_000,
    navigationTimeout: 60_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
