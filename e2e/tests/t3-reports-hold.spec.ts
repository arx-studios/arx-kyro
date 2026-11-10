import { expect, test } from '@playwright/test';
import { ADMIN_URL, WEB_URL } from '../playwright.config';
import {
  ApiSession,
  createSubmittedRequest,
  decideInAdmin,
  expectNoSeriousA11yViolations,
  registerUser,
  staffContext,
  webLogin,
} from '../support/helpers';

// Report -> staff claims and resolves with a moderation hold -> hidden from donors -> ADMIN releases -> visible.
test.describe.configure({ mode: 'serial' });

const run = Date.now().toString(36);
const password = 'E2eReport!2026-pw';
const recipientEmail = `e2e.hr.${run}@kyro.local`;
const donorEmail = `e2e.hd.${run}@kyro.local`;
const title = `E2E hold ${run}`;
let requestId = '';
let reportPath = '';

async function publicFeedHas(id: string): Promise<boolean> {
  const feed = (await (await new ApiSession().fetch('GET', '/api/v1/discovery/feed?limit=50')).json()) as { items: { id: string }[] };
  return feed.items.some((i) => i.id === id);
}

test('setup: approved request and a donor', async ({ browser }) => {
  await registerUser(recipientEmail, password, 'RECIPIENT');
  const api = new ApiSession();
  await api.login(recipientEmail, password);
  requestId = await createSubmittedRequest(api, title);
  const staff = await staffContext(browser, 'verifier');
  const admin = await staff.newPage();
  await decideInAdmin(admin, title, 'Approve', { rationale: 'Fictional: approved for the report E2E.' });
  await staff.close();
  await registerUser(donorEmail, password, 'DONOR');
  expect(await publicFeedHas(requestId)).toBe(true);
});

test('donor reports the request from its detail page', async ({ browser }) => {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await webLogin(page, donorEmail, password, /\/discover/);
  await page.goto(`${WEB_URL}/discover/${requestId}`);
  await page.getByRole('button', { name: 'Report this request' }).click();
  const dialog = page.getByRole('dialog');
  await dialog.getByLabel('I suspect fraud').check();
  await dialog.getByLabel('Details').fill('Fictional E2E concern: the invoice looks odd.');
  await dialog.getByRole('button', { name: 'Send report' }).click();
  await expect(dialog.getByText('Thanks. Our team will review this request.')).toBeVisible();
  await dialog.getByRole('button', { name: 'Close' }).click();

  // A second report of the same request is refused in friendly words.
  await page.getByRole('button', { name: 'Report this request' }).click();
  await page.getByRole('dialog').getByLabel('I suspect fraud').check();
  await page.getByRole('dialog').getByRole('button', { name: 'Send report' }).click();
  await expect(page.getByRole('dialog').getByText('You have already reported this request.')).toBeVisible();
  await ctx.close();
});

test('verifier claims and resolves with a hold; request disappears for donors', async ({ browser }) => {
  const staff = await staffContext(browser, 'verifier');
  const page = await staff.newPage();

  await page.goto(`${ADMIN_URL}/`);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await expect(page.getByText('Open reports')).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'admin /');

  await page.goto(`${ADMIN_URL}/reports`);
  await expect(page.getByRole('heading', { name: 'Reports' })).toBeVisible();
  await expect(page.getByRole('link', { name: title })).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'admin /reports');
  await page.getByRole('link', { name: title }).click();
  await expect(page).toHaveURL(/\/reports\/[0-9a-f-]{36}/);
  reportPath = new URL(page.url()).pathname;
  await page.getByRole('button', { name: 'Claim report' }).click();
  const form = page.getByRole('form', { name: 'Resolve report' });
  await expect(form).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'admin /reports/[id]');

  await form.getByLabel('Action taken').check();
  await form.getByLabel('Resolution note').fill('Fictional: hide the request while we investigate.');
  await form.getByLabel('Hide request from discovery').check();
  await form.getByRole('button', { name: 'Review resolution' }).click();
  await page.getByRole('button', { name: 'Confirm resolution' }).click();
  await expect(page.getByRole('heading', { name: 'Moderation hold' })).toBeVisible();
  // A verifier cannot release it.
  await expect(page.getByText('Only an admin can release a hold.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Release hold' })).toHaveCount(0);
  await staff.close();

  expect(await publicFeedHas(requestId)).toBe(false);
  const ctx = await browser.newContext();
  const donor = await ctx.newPage();
  await webLogin(donor, donorEmail, password, /\/discover/);
  await donor.goto(`${WEB_URL}/discover/${requestId}`);
  await expect(donor.getByText('This request is not available')).toBeVisible();
  await ctx.close();
});

test('ADMIN releases the hold and the request is visible again', async ({ browser }) => {
  const staff = await staffContext(browser, 'admin');
  const page = await staff.newPage();
  await page.goto(`${ADMIN_URL}${reportPath}`);
  await expect(page.getByRole('heading', { name: 'Moderation hold' })).toBeVisible();
  await page.getByRole('button', { name: 'Release hold' }).click();
  await expectNoSeriousA11yViolations(page, 'release hold dialog');
  await page.getByRole('button', { name: 'Confirm release' }).click();
  await expect(page.getByRole('heading', { name: 'Moderation hold' })).toBeHidden();
  await staff.close();

  expect(await publicFeedHas(requestId)).toBe(true);
  const ctx = await browser.newContext();
  const donor = await ctx.newPage();
  await webLogin(donor, donorEmail, password, /\/discover/);
  await donor.goto(`${WEB_URL}/discover/${requestId}`);
  await expect(donor.getByRole('heading', { level: 1, name: title })).toBeVisible();
  await ctx.close();
});
