import { expect, test, type Locator, type Page } from '@playwright/test';
import { WEB_URL } from '../playwright.config';
import {
  ApiSession,
  createSubmittedRequest,
  decideInAdmin,
  expectNoHorizontalScroll,
  expectNoSeriousA11yViolations,
  registerUser,
  staffContext,
  webLogin,
} from '../support/helpers';

// Non-binding pledges end to end: pledge, figures (pledged is never "raised"), stale-tab duplicate, My pledges,
// cancel, and cancellation when the recipient withdraws the request. Own fictional users and request.
test.describe.configure({ mode: 'serial' });

const run = Date.now().toString(36);
const password = 'E2ePledger!2026-pw';
const recipientEmail = `e2e.pr.${run}@kyro.local`;
const donorEmail = `e2e.pd.${run}@kyro.local`;
const title = `E2E pledge ${run}`;
let requestId = '';

function figure(page: Page, term: string): Locator {
  return page.locator('dl').filter({ hasText: 'Outstanding need' }).locator(':scope > div').filter({ hasText: term });
}

async function pledgeVia(page: Page, amount: string): Promise<void> {
  await page.getByRole('button', { name: 'Pledge (non-binding)' }).click();
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('A pledge is not a payment. No money is collected in this sandbox.')).toBeVisible();
  await dialog.getByLabel('Pledge amount').fill(amount);
  await dialog.getByLabel(/I understand a pledge is not a payment/).check();
  await dialog.getByRole('button', { name: 'Make pledge' }).click();
}

test('setup: an approved request exists', async ({ browser }) => {
  await registerUser(recipientEmail, password, 'RECIPIENT');
  const api = new ApiSession();
  await api.login(recipientEmail, password);
  requestId = await createSubmittedRequest(api, title);
  const staff = await staffContext(browser, 'verifier');
  const admin = await staff.newPage();
  await decideInAdmin(admin, title, 'Approve', { rationale: 'Fictional: approved for the pledge E2E.' });
  await staff.close();
  await registerUser(donorEmail, password, 'DONOR');
});

test('pledge, see figures, duplicate from a stale tab, My pledges, cancel', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await webLogin(page, donorEmail, password, /\/discover/);

  const stale = await ctx.newPage();
  await stale.goto(`${WEB_URL}/discover/${requestId}`);
  await expect(stale.getByRole('button', { name: 'Pledge (non-binding)' })).toBeVisible();

  await page.goto(`${WEB_URL}/discover/${requestId}`);
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
  await expect(figure(page, 'Outstanding need')).toContainText('45,000');
  await expectNoSeriousA11yViolations(page, '/discover/[id] funding panel');

  // The report dialog is a new surface too: open it, check it, close it.
  await page.getByRole('button', { name: 'Report this request' }).click();
  await expect(page.getByRole('dialog').getByText('Report this request').first()).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'report dialog');
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel' }).click();

  // Pledge dialog: axe while open, then pledge Rs 1,000.
  await page.getByRole('button', { name: 'Pledge (non-binding)' }).click();
  await expect(page.getByRole('dialog').getByLabel('Pledge amount')).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'pledge dialog');
  await page.getByRole('dialog').getByRole('button', { name: 'Not now' }).click();
  await pledgeVia(page, '1000');
  await expect(page.getByText('Your non-binding pledge was saved. Nothing was charged.')).toBeVisible();

  // Pledged is separate; confirmed stays 0 and outstanding is unchanged (a pledge is never money raised).
  await expect(figure(page, 'Pledged (not yet paid)')).toContainText('1,000');
  await expect(figure(page, 'Confirmed').first()).toContainText(/₹\s?0(\.00)?/);
  await expect(figure(page, 'Outstanding need')).toContainText('45,000');
  await expect(page.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
  await expect(page.getByText('Your pledge')).toBeVisible();

  // A second pledge from the stale tab gets a friendly error, not a crash.
  await pledgeVia(stale, '500');
  await expect(stale.getByRole('alert').filter({ hasText: 'You already have an active pledge on this request' })).toBeVisible();
  await stale.close();

  // 320px: the detail page with the funding panel does not scroll sideways.
  await page.setViewportSize({ width: 320, height: 640 });
  await page.reload();
  await expect(figure(page, 'Pledged (not yet paid)')).toBeVisible();
  await expectNoHorizontalScroll(page, 'detail with funding panel');
  await expectNoSeriousA11yViolations(page, 'detail with funding panel at 320px');

  // My pledges
  await page.goto(`${WEB_URL}/pledges`);
  await expect(page.getByRole('heading', { name: title })).toBeVisible();
  await expect(page.getByText('Active', { exact: true })).toBeVisible();
  await expectNoHorizontalScroll(page, '/pledges');
  await expectNoSeriousA11yViolations(page, '/pledges at 320px');
  await page.setViewportSize({ width: 1280, height: 800 });
  await expectNoSeriousA11yViolations(page, '/pledges');

  await page.getByRole('button', { name: `Cancel pledge for ${title}` }).click();
  await expectNoSeriousA11yViolations(page, 'cancel pledge dialog');
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel pledge' }).click();
  await expect(page.getByText('Cancelled', { exact: true })).toBeVisible();

  // Pledge again so the withdrawal below has something to cancel.
  await page.goto(`${WEB_URL}/discover/${requestId}`);
  await pledgeVia(page, '2000');
  await expect(figure(page, 'Pledged (not yet paid)')).toContainText('2,000');
  await ctx.close();
});

test('withdrawing the approved request cancels its pledges and removes it from donors', async ({ browser }) => {
  const rc = await browser.newContext();
  const rp = await rc.newPage();
  await webLogin(rp, recipientEmail, password, /\/recipient\/dashboard/);
  await rp.goto(`${WEB_URL}/recipient/requests/${requestId}`);
  // The recipient sees funding with pledges as non-binding.
  await expect(rp.getByText(/Pledges are not payments/i).first()).toBeVisible();
  await rp.getByRole('button', { name: 'Withdraw request' }).click();
  await rp.getByRole('button', { name: 'Yes, withdraw' }).click();
  await expect(rp.getByText('Withdrawn').first()).toBeVisible();
  await rc.close();

  const dc = await browser.newContext();
  const dp = await dc.newPage();
  await webLogin(dp, donorEmail, password, /\/discover/);
  await dp.goto(`${WEB_URL}/pledges`);
  await expect(dp.getByText('Cancelled because the request was withdrawn.')).toBeVisible();
  await expect(dp.getByText('Active', { exact: true })).toHaveCount(0);
  await dp.goto(`${WEB_URL}/discover/${requestId}`);
  await expect(dp.getByText(/This request is not available/).first()).toBeVisible();
  await dc.close();

  const feed = (await (await new ApiSession().fetch('GET', '/api/v1/discovery/feed?limit=50')).json()) as { items: { id: string }[] };
  expect(feed.items.some((i) => i.id === requestId)).toBe(false);
});
