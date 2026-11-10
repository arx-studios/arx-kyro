import { expect, test, type Browser } from '@playwright/test';
import { ADMIN_URL, WEB_URL } from '../playwright.config';
import {
  ApiSession,
  createSubmittedRequest,
  decideInAdmin,
  expectNoSeriousA11yViolations,
  registerUser,
  staffContext,
  webLogin,
  waitForMail,
} from '../support/helpers';

// Review outcomes beyond approve: request changes -> resubmit -> approve, reject, withdraw while in review,
// optional-field clearing, status-change emails. Each run uses its own fictional users and requests.
test.describe.configure({ mode: 'serial' });

const run = Date.now().toString(36);
const password = 'E2eRecipient!2026-pw';
const email = `e2e.loop.${run}@kyro.local`;
const RATIONALE = `INTERNAL-RATIONALE-${run} never for the recipient`;

let api: ApiSession;

test.beforeAll(async () => {
  // A second, API-only session for the same recipient, used to create requests quickly.
  await registerUser(email, password, 'RECIPIENT');
  api = new ApiSession();
  await api.login(email, password);
});

test('request changes -> recipient feedback and email -> edit -> resubmit -> approve -> in the feed', async ({ browser }) => {
  const title = `E2E correction ${run}`;
  const id = await createSubmittedRequest(api, title);
  const message = `Please add a clearer invoice scan ${run}`;

  const staff = await staffContext(browser, 'verifier');
  const admin = await staff.newPage();
  await decideInAdmin(admin, title, 'Request changes', { rationale: RATIONALE, message });

  const recipient = await userLogin(browser);
  const page = await recipient.newPage();
  await page.goto(`${WEB_URL}/recipient/requests/${id}`);
  await expect(page.getByRole('heading', { level: 1, name: title })).toBeVisible();
  await expect(page.getByText('Changes requested').first()).toBeVisible();
  await expect(page.getByText('Message from the reviewer')).toBeVisible();
  await expect(page.getByText(message).first()).toBeVisible();
  expect(await page.content()).not.toContain(RATIONALE);

  // The status email names the outcome and links to the request; it never carries the rationale or the message.
  const mail = await waitForMail(email, { subject: /Update on your KYRO request/, text: /changes are needed/ });
  expect(mail.text).toContain(`/recipient/requests/${id}`);
  expect(mail.text).not.toContain(RATIONALE);
  expect(mail.text).not.toContain(message);

  // Edit and resubmit.
  const newTitle = `${title} (clearer scan)`;
  await page.getByLabel('Title').fill(newTitle);
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('heading', { level: 1, name: newTitle })).toBeVisible();
  await page.getByRole('button', { name: 'Submit for verification' }).click();
  await expect(page.getByRole('button', { name: 'Submit for verification' })).toBeHidden();

  // Case #2 for the same request; approve it.
  await admin.goto(`${ADMIN_URL}/queue`);
  await admin.getByRole('tab', { name: 'All' }).click();
  await admin.getByRole('link', { name: newTitle }).first().click();
  await expect(admin.getByText('Submission #2')).toBeVisible();
  await decideInAdmin(admin, newTitle, 'Approve', { rationale: 'Fictional: the new scan is clear.' });
  await page.reload();
  await expect(page.getByText(/Your request has been verified and is visible to donors/)).toBeVisible();
  await waitForMail(email, { subject: /Update on your KYRO request/, text: /was approved/ });

  const feed = await new ApiSession().fetch('GET', '/api/v1/discovery/feed?limit=50');
  const items = ((await feed.json()) as { items: { id: string }[] }).items;
  expect(items.some((i) => i.id === id), 'approved request is in the public feed').toBe(true);
  await staff.close();
  await recipient.close();
});

test('reject: recipient sees Rejected, cannot edit, request is not public, email arrives', async ({ browser }) => {
  const title = `E2E reject ${run}`;
  const id = await createSubmittedRequest(api, title);
  const staff = await staffContext(browser, 'verifier');
  const admin = await staff.newPage();
  await decideInAdmin(admin, title, 'Reject', {
    rationale: RATIONALE,
    message: `This request cannot be verified ${run}`,
  });

  const recipient = await userLogin(browser);
  const page = await recipient.newPage();
  await page.goto(`${WEB_URL}/recipient/requests/${id}`);
  await expect(page.getByText('Rejected').first()).toBeVisible();
  await expect(page.getByText(`This request cannot be verified ${run}`).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Save changes' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Submit for verification' })).toHaveCount(0);
  await expect(page.getByLabel('Title')).toHaveCount(0);
  expect(await page.content()).not.toContain(RATIONALE);

  const feed = (await (await new ApiSession().fetch('GET', '/api/v1/discovery/feed?limit=50')).json()) as { items: { id: string }[] };
  expect(feed.items.some((i) => i.id === id)).toBe(false);
  const mail = await waitForMail(email, { subject: /Update on your KYRO request/, text: /not approved/ });
  expect(mail.text).not.toContain(RATIONALE);
  await staff.close();
  await recipient.close();
});

test('withdraw while in review closes the case as withdrawn', async ({ browser }) => {
  const title = `E2E withdraw review ${run}`;
  const id = await createSubmittedRequest(api, title);
  const staff = await staffContext(browser, 'verifier');
  const admin = await staff.newPage();
  await admin.goto(`${ADMIN_URL}/queue`);
  await admin.getByRole('tab', { name: 'All' }).click();
  await admin.getByRole('link', { name: title }).first().click();
  await admin.getByRole('button', { name: 'Claim case' }).click();
  await expect(admin.getByRole('form', { name: 'Record a decision' })).toBeVisible();

  const recipient = await userLogin(browser);
  const page = await recipient.newPage();
  await page.goto(`${WEB_URL}/recipient/requests/${id}`);
  await page.getByRole('button', { name: 'Withdraw request' }).click();
  await page.getByRole('button', { name: 'Yes, withdraw' }).click();
  await expect(page.getByText('Withdrawn').first()).toBeVisible();

  await admin.goto(`${ADMIN_URL}/queue`);
  await admin.getByRole('tab', { name: 'Closed' }).click();
  const row = admin.getByRole('row').filter({ has: admin.getByRole('link', { name: title }) });
  await expect(row).toBeVisible();
  await expect(row.getByText(/withdrawn/i).first()).toBeVisible();
  await staff.close();
  await recipient.close();
});

test('clearing an optional field on a draft persists after reload', async ({ browser }) => {
  const recipient = await userLogin(browser);
  const page = await recipient.newPage();
  await page.goto(`${WEB_URL}/recipient/requests/new`);
  await page.getByLabel('Title').fill(`E2E clear ${run}`);
  await page.getByLabel(/Amount \(INR\)/).fill('12000');
  await page.getByLabel('Your story').fill('Fictional draft story that will be saved, then an optional field is cleared.');
  await page.getByLabel('Course').fill('Fictional course to clear');
  await page.getByLabel('Fee reference').fill(`CLEAR-ME-${run}`);
  await page.getByRole('button', { name: 'Save draft and continue' }).click();
  await expect(page).toHaveURL(/\/recipient\/requests\/[0-9a-f-]{36}/);
  await expect(page.getByLabel('Fee reference')).toHaveValue(`CLEAR-ME-${run}`);

  await page.getByLabel('Fee reference').fill('');
  await page.getByLabel('Course').fill('');
  const patch = page.waitForResponse((r) => r.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Save changes' }).click();
  expect((await patch).status()).toBe(200);

  await page.reload();
  await expect(page.getByLabel('Title')).toHaveValue(`E2E clear ${run}`);
  await expect(page.getByLabel('Fee reference')).toHaveValue('');
  await expect(page.getByLabel('Course')).toHaveValue('');
  await expectNoSeriousA11yViolations(page, 'recipient draft after clearing fields');
  await recipient.close();
});

async function userLogin(browser: Browser) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await webLogin(page, email, password, /\/recipient\/dashboard/);
  await page.close();
  return ctx;
}
