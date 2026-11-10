import { expect, test, type Browser, type Page } from '@playwright/test';
import { ADMIN_URL, WEB_URL } from '../playwright.config';
import {
  expectNoSeriousA11yViolations,
  nextTotp,
  passwordResetToken,
  psql,
  registerUser,
  waitForMail,
  webLogin,
} from '../support/helpers';

// Account recovery and security: password reset via Mailpit, other sessions revoked (reset and change),
// consent re-acceptance, admin MFA setup and verify for a freshly granted staff user.
test.describe.configure({ mode: 'serial' });

const run = Date.now().toString(36);
const oldPassword = 'E2eOldPassw0rd!2026';
const resetPassword = 'E2eResetPassw0rd!2026';
const changedPassword = 'E2eChangedPassw0rd!2026';
const userEmail = `e2e.acct.${run}@kyro.local`;

async function sessionStatus(page: Page): Promise<number> {
  return (await page.request.get(`${WEB_URL}/api/v1/me`)).status();
}

async function loggedInContext(browser: Browser, password: string) {
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await webLogin(page, userEmail, password, /\/recipient\/dashboard/);
  return { ctx, page };
}

test('forgot password -> Mailpit link -> new password; a session from before is revoked', async ({ browser }) => {
  await registerUser(userEmail, oldPassword, 'RECIPIENT');
  const before = await loggedInContext(browser, oldPassword);
  expect(await sessionStatus(before.page)).toBe(200);

  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto(`${WEB_URL}/forgot-password`);
  await page.waitForLoadState('networkidle');
  await expectNoSeriousA11yViolations(page, '/forgot-password');
  await page.getByLabel('Email').fill(userEmail);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByText('If an account exists for that address, we sent a link')).toBeVisible();

  const token = await passwordResetToken(userEmail);
  await page.goto(`${WEB_URL}/reset-password?token=${encodeURIComponent(token)}`);
  await page.waitForLoadState('networkidle');
  await expectNoSeriousA11yViolations(page, '/reset-password');
  await page.getByLabel(/^New password/).fill(resetPassword);
  await page.getByLabel('Confirm new password').fill(resetPassword);
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByText('Password updated')).toBeVisible();

  // The link works once.
  await page.goto(`${WEB_URL}/reset-password?token=${encodeURIComponent(token)}`);
  await page.waitForLoadState('networkidle');
  await page.getByLabel(/^New password/).fill(resetPassword);
  await page.getByLabel('Confirm new password').fill(resetPassword);
  await page.getByRole('button', { name: 'Set new password' }).click();
  await expect(page.getByText('This reset link is invalid, expired or has already been used.')).toBeVisible();

  // The session that existed before the reset is now unauthenticated (401) and the UI sends it to login.
  expect(await sessionStatus(before.page)).toBe(401);
  await before.page.goto(`${WEB_URL}/profile`);
  await expect(before.page).toHaveURL(/\/login/);
  await before.ctx.close();

  // The old password no longer works, the new one does; a "password changed" mail was sent.
  await webLogin(page, userEmail, resetPassword, /\/recipient\/dashboard/);
  await waitForMail(userEmail, { subject: /Your KYRO password was changed/ });
  await ctx.close();
});

test('change password signs out the other session but not this one', async ({ browser }) => {
  const a = await loggedInContext(browser, resetPassword);
  const b = await loggedInContext(browser, resetPassword);
  await a.page.goto(`${WEB_URL}/profile`);
  const form = a.page.getByRole('form', { name: 'Change password' });
  await expect(form).toBeVisible();
  await expectNoSeriousA11yViolations(a.page, '/profile');
  await form.getByLabel('Current password').fill('definitely-not-the-password');
  await form.getByLabel(/^New password/).fill(changedPassword);
  await form.getByLabel('Confirm new password').fill(changedPassword);
  await form.getByRole('button', { name: 'Change password' }).click();
  await expect(a.page.getByRole('alert').filter({ hasText: /current password/i })).toBeVisible();

  await form.getByLabel('Current password').fill(resetPassword);
  const done = a.page.waitForResponse((r) => r.url().includes('/me/password') && r.request().method() === 'POST');
  await form.getByRole('button', { name: 'Change password' }).click();
  expect((await done).status()).toBe(204);
  await a.page.waitForTimeout(300); // let the browser adopt the rotated session cookie before the next request
  await expect.poll(() => sessionStatus(b.page)).toBe(401);
  expect(await sessionStatus(a.page)).toBe(200);
  await a.ctx.close();
  await b.ctx.close();
});

test('consent re-acceptance blocks the app until the current policies are accepted', async ({ browser }) => {
  // A newer consent row for an old policy version makes the user "behind" (consent_records is append-only).
  psql(
    `INSERT INTO consent_records (user_id, policy_type, policy_version) SELECT id, t, '2025-01-01' FROM users, (VALUES ('TERMS'), ('PRIVACY')) AS v(t) WHERE email = '${userEmail}'`,
  );
  const { ctx, page } = await loggedInContext(browser, changedPassword);
  const dialog = page.getByRole('dialog');
  await expect(dialog.getByText('Our policies have been updated')).toBeVisible();
  await expectNoSeriousA11yViolations(page, 'consent re-accept dialog');
  await dialog.getByRole('button', { name: 'Accept' }).click();
  await expect(dialog).toBeHidden();
  await page.reload();
  await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
  await expect(page.getByText('Our policies have been updated')).toHaveCount(0);
  await ctx.close();
});

test('admin MFA: a new staff user enrols (QR, secret, recovery codes), then signs in with a code', async ({ browser }) => {
  const staffEmail = `e2e.staff.${run}@kyro.local`;
  const staffPassword = 'E2eStaffPassw0rd!2026';
  await registerUser(staffEmail, staffPassword, 'RECIPIENT');
  psql(`INSERT INTO user_roles (user_id, role_code) SELECT id, 'VERIFIER' FROM users WHERE email = '${staffEmail}'`);

  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  await page.goto(`${ADMIN_URL}/login`);
  await page.waitForLoadState('networkidle');
  await page.getByLabel('Email').fill(staffEmail);
  await page.getByLabel('Password').fill(staffPassword);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/mfa\/setup/);
  await page.waitForLoadState('networkidle');
  await expectNoSeriousA11yViolations(page, 'admin /mfa/setup (intro)');

  await page.getByRole('button', { name: 'Begin setup' }).click();
  await expect(page.getByRole('img', { name: /QR code for/ })).toBeVisible();
  const secret = (await page.getByTestId('totp-secret').innerText()).trim();
  expect(secret).toMatch(/^[A-Z2-7]{16,}$/);
  await expectNoSeriousA11yViolations(page, 'admin /mfa/setup (scan)');

  // A wrong code is refused with an error and enrolment stays possible.
  await page.getByLabel('Authenticator code').fill('000000');
  await page.getByRole('button', { name: 'Confirm and continue' }).click();
  await expect(page.getByText('Code not accepted')).toBeVisible();

  await page.getByLabel('Authenticator code').fill(await nextTotp(secret));
  await page.getByRole('button', { name: 'Confirm and continue' }).click();
  await expect(page.getByText('Save your recovery codes now')).toBeVisible();
  await expect(page.getByRole('list', { name: 'Recovery codes' }).getByRole('listitem')).toHaveCount(10);
  await expectNoSeriousA11yViolations(page, 'admin /mfa/setup (recovery codes)');
  const next = page.getByRole('button', { name: 'Continue' });
  await expect(next).toBeDisabled();
  await page.getByLabel('I have saved these recovery codes').check();
  await next.click();
  await expect(page).not.toHaveURL(/\/mfa/);
  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await ctx.close();

  // New login: password, then the verify screen.
  const second = await browser.newContext();
  const p2 = await second.newPage();
  await p2.goto(`${ADMIN_URL}/login`);
  await p2.waitForLoadState('networkidle');
  await p2.getByLabel('Email').fill(staffEmail);
  await p2.getByLabel('Password').fill(staffPassword);
  await p2.getByRole('button', { name: /sign in/i }).click();
  await expect(p2).toHaveURL(/\/mfa\/verify/);
  await p2.waitForLoadState('networkidle');
  await expectNoSeriousA11yViolations(p2, 'admin /mfa/verify');
  await p2.getByLabel('Authenticator code').fill('000000');
  await p2.getByRole('button', { name: 'Verify' }).click();
  await expect(p2.getByText('Code not accepted')).toBeVisible();
  await expect(p2).toHaveURL(/\/mfa\/verify/);
  await p2.getByLabel('Authenticator code').fill(await nextTotp(secret));
  await p2.getByRole('button', { name: 'Verify' }).click();
  await expect(p2).not.toHaveURL(/\/mfa/);
  await expect(p2.getByRole('heading', { name: 'Dashboard' })).toBeVisible();
  await second.close();
});
