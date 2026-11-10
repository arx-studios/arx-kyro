import { expect, test, type Browser, type BrowserContext, type Page } from '@playwright/test';
import { ADMIN_URL, WEB_URL } from '../playwright.config';
import {
  SEED_PASSWORD,
  expectNoSeriousA11yViolations,
  fictionalPdf,
  verificationToken,
} from '../support/helpers';

// The first vertical slice, driven through the real web (recipient, donor) and admin UIs.
// One ordered journey: each test continues from the state left by the previous one.
test.describe.configure({ mode: 'serial' });

const run = Date.now().toString(36);
const recipientEmail = `e2e.recipient.${run}@kyro.local`;
const recipientPassword = 'E2eRecipient!2026-pw';
const title = `E2E tuition ${run}`;
let requestId = '';

async function newContext(browser: Browser): Promise<BrowserContext> {
  return browser.newContext();
}

async function loginWeb(page: Page, email: string, password: string, expectedPath: RegExp): Promise<void> {
  await page.goto(`${WEB_URL}/login`);
  await page.waitForLoadState('networkidle'); // let React hydrate before submitting the form
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(expectedPath);
}

test.describe('recipient (web)', () => {
  test('registers, verifies email via Mailpit and logs in', async ({ browser }) => {
    const ctx = await newContext(browser);
    const page = await ctx.newPage();

    await page.goto(`${WEB_URL}/register`);
    await expectNoSeriousA11yViolations(page, '/register');
    await page.getByLabel('Email').fill(recipientEmail);
    await page.getByLabel('Password').fill(recipientPassword);
    await page.getByLabel('Your name').fill('E2E Recipient');
    await page.getByLabel('Public display name').fill('E2E R.');
    await page.getByLabel('I need support with a verified education expense').check();
    await expect(page.getByLabel(/I accept the Terms of Use \(version/)).toBeVisible();
    await page.getByLabel(/I accept the Terms of Use/).check();
    await page.getByLabel(/I accept the Privacy Policy/).check();
    await page.getByRole('button', { name: 'Create account' }).click();
    await expect(page).toHaveURL(/\/register\/check-email/);

    const token = await verificationToken(recipientEmail);
    await page.goto(`${WEB_URL}/verify-email?token=${encodeURIComponent(token)}`);
    await expect(page.getByText('Your email address is verified')).toBeVisible();

    await loginWeb(page, recipientEmail, recipientPassword, /\/recipient\/dashboard/);
    await expectNoSeriousA11yViolations(page, '/recipient/dashboard');
    await ctx.storageState({ path: 'test-results/recipient-state.json' });
    await ctx.close();
  });

  test('creates a tuition request, uploads a document to MinIO and submits', async ({ browser }) => {
    const ctx = await browser.newContext({ storageState: 'test-results/recipient-state.json' });
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/recipient/requests/new`);
    await expectNoSeriousA11yViolations(page, '/recipient/requests/new');

    await page.getByLabel('Title').fill(title);
    await page.getByLabel(/Amount \(INR\)/).fill('45000');
    await page
      .getByLabel('Your story')
      .fill('Fictional E2E story: the semester fee for a fictional institute is due and I cannot pay it in full.');
    await page.getByLabel('Institution name').fill('Fictional Institute of Technology');
    await page.getByLabel('Institution city').fill('Pune');
    await page.getByLabel('Course').fill('B.Tech Computer Science');
    await page.getByLabel('Fee reference').fill(`SECRET-FEE-REF-${run}`);
    await page.getByLabel('Payment due date').fill('2027-12-31');
    await page.getByRole('button', { name: 'Save draft and continue' }).click();
    await expect(page).toHaveURL(/\/recipient\/requests\/[0-9a-f-]{36}/);
    requestId = /requests\/([0-9a-f-]{36})/.exec(page.url())![1]!;
    await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();

    // Browser PUT straight to the MinIO presigned URL (this exercises MinIO CORS).
    const putResponse = page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().includes('X-Amz-Signature'));
    await page.getByLabel('Choose a file').setInputFiles({
      name: 'tuition-invoice.pdf',
      mimeType: 'application/pdf',
      buffer: fictionalPdf(run),
    });
    expect((await putResponse).ok()).toBe(true);
    await expect(page.getByText('tuition-invoice.pdf was uploaded.')).toBeVisible();

    await expectNoSeriousA11yViolations(page, 'request detail (draft)');

    await page.getByRole('button', { name: 'Submit for verification' }).click();
    await expect(page.getByRole('button', { name: 'Submit for verification' })).toBeHidden();
    await expect(page.getByText(/Submitted|In review/).first()).toBeVisible();
    await ctx.close();
  });
});

test.describe('verifier (admin)', () => {
  test('logs in, claims the case, views the document and approves', async ({ browser }) => {
    const ctx = await newContext(browser);
    const page = await ctx.newPage();

    await page.goto(`${ADMIN_URL}/login`);
    await page.waitForLoadState('networkidle'); // let React hydrate before submitting the form
    await expectNoSeriousA11yViolations(page, 'admin /login');
    await page.getByLabel('Email').fill('verifier@kyro.local');
    await page.getByLabel('Password').fill(SEED_PASSWORD);
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page).toHaveURL(/\/queue/);

    await page.getByRole('tab', { name: 'All' }).click();
    await page.getByRole('link', { name: title }).click();
    await expect(page).toHaveURL(/\/cases\//);
    await expect(page.getByRole('heading', { name: title }).first()).toBeVisible();

    await page.getByRole('button', { name: 'Claim case' }).click();
    await expect(page.getByRole('form', { name: 'Record a decision' })).toBeVisible();
    await expectNoSeriousA11yViolations(page, 'admin case detail');

    // Document view: audited presigned URL opens in a new tab.
    const viewRequest = ctx.waitForEvent('request', (r) => r.url().includes('X-Amz-Signature'));
    await page.getByRole('button', { name: /^View tuition-invoice\.pdf/ }).click();
    const viewUrl = (await viewRequest).url();
    expect(viewUrl).toContain('X-Amz-Signature');
    const bytes = await (await ctx.request.get(viewUrl)).body();
    expect(bytes.subarray(0, 5).toString()).toBe('%PDF-');

    await page.getByLabel('Approve').check();
    await page.getByLabel('Internal rationale').fill('Fictional invoice matches the request details.');
    await page.getByRole('button', { name: 'Review decision' }).click();
    await page.getByRole('button', { name: 'Confirm: Approve' }).click();
    await expect(page.getByRole('form', { name: 'Record a decision' })).toBeHidden();
    await expect(page.getByText(/Outcome: approved/i).first()).toBeVisible();
    await ctx.close();
  });

  test('recipient sees the request as approved', async ({ browser }) => {
    const ctx = await browser.newContext({ storageState: 'test-results/recipient-state.json' });
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/recipient/requests/${requestId}`);
    await expect(page.getByText(/Your request has been verified and is visible to donors/)).toBeVisible();
    await ctx.close();
  });
});

test.describe('donor (web)', () => {
  test('sees the request in the feed, saves it with the keyboard and finds it in Saved', async ({ browser }) => {
    const ctx = await newContext(browser);
    const page = await ctx.newPage();
    await loginWeb(page, 'donor.demo@kyro.local', SEED_PASSWORD, /\/discover/);

    // The newest approved request is the top card.
    const card = page.getByRole('region', { name: 'Verified requests' });
    await expect(card.getByRole('heading', { name: title })).toBeVisible();
    await expectNoSeriousA11yViolations(page, '/discover (donor)');

    // Private fields never reach the browser.
    const html = await page.content();
    expect(html).not.toContain(`SECRET-FEE-REF-${run}`);
    expect(html).not.toContain(recipientEmail);

    // Keyboard-only: focus the first action button, then use the S shortcut.
    await page.getByRole('button', { name: 'Save this request for later' }).focus();
    const swipe = page.waitForResponse((r) => r.url().includes('/swipes') && r.request().method() === 'POST');
    await page.keyboard.press('s');
    expect((await swipe).status()).toBe(200);

    await page.goto(`${WEB_URL}/saved`);
    const savedItem = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title }) });
    await expect(savedItem).toBeVisible();
    await expect(savedItem.getByText('Saved', { exact: true })).toBeVisible();
    await expectNoSeriousA11yViolations(page, '/saved');

    // Switch to INTERESTED from the public detail page.
    await savedItem.getByRole('link', { name: 'View details' }).click();
    await expect(page).toHaveURL(new RegExp(`/discover/${requestId}`));
    await page.getByRole('button', { name: 'I am interested in this request' }).click();
    await expect(page.getByRole('button', { name: 'I am interested in this request' })).toHaveAttribute('aria-pressed', 'true');
    await expectNoSeriousA11yViolations(page, '/discover/[id]');

    await page.goto(`${WEB_URL}/saved`);
    await expect(savedItem.getByText('Interested', { exact: true })).toBeVisible();
    await ctx.close();
  });

  test('anonymous visitors see a read-only feed', async ({ browser }) => {
    const ctx = await newContext(browser);
    const page = await ctx.newPage();
    await page.goto(`${WEB_URL}/discover`);
    await expect(page.getByRole('link', { name: 'Sign in as a donor' })).toBeVisible();
    await ctx.close();
  });
});
