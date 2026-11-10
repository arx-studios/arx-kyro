import { expect, test, type Page } from '@playwright/test';
import { ADMIN_URL, WEB_URL } from '../playwright.config';
import { expectNoSeriousA11yViolations } from '../support/helpers';

const PUBLIC_PAGES = ['/', '/trust', '/login', '/discover'];

test.describe('axe: public pages have no serious or critical violations', () => {
  for (const path of PUBLIC_PAGES) {
    test(`web ${path}`, async ({ page }) => {
      await page.goto(`${WEB_URL}${path}`);
      await expect(page.getByRole('main')).toBeVisible();
      await expectNoSeriousA11yViolations(page, `web ${path}`);
    });
  }
  test('admin /login', async ({ page }) => {
    await page.goto(`${ADMIN_URL}/login`);
    await expectNoSeriousA11yViolations(page, 'admin /login');
  });
});

async function expectNoHorizontalScroll(page: Page, label: string): Promise<void> {
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(overflow.scrollWidth, `${label}: horizontal overflow at 320px`).toBeLessThanOrEqual(overflow.innerWidth);
}

test.describe('320px viewport (WCAG reflow)', () => {
  test.use({ viewport: { width: 320, height: 640 } });

  test('landing reflows without horizontal scroll and keeps the sandbox banner', async ({ page }) => {
    await page.goto(`${WEB_URL}/`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText(/sandbox/i).first()).toBeVisible();
    await expectNoHorizontalScroll(page, 'landing');
    await expectNoSeriousA11yViolations(page, 'landing at 320px');
  });

  test('discover reflows without horizontal scroll', async ({ page }) => {
    await page.goto(`${WEB_URL}/discover`);
    await expect(page.getByRole('region', { name: 'Verified requests' })).toBeVisible();
    await expect(page.getByText(/Loading verified requests/)).toBeHidden();
    await expectNoHorizontalScroll(page, 'discover');
    await expectNoSeriousA11yViolations(page, 'discover at 320px');
  });
});
