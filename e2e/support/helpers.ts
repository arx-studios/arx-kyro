import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHmac } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ADMIN_URL, WEB_URL } from '../playwright.config';

export const MAILPIT_URL = process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025';
export const SEED_PASSWORD = process.env.KYRO_SEED_PASSWORD ?? 'KyroLocal!2026';

/** Fails on serious or critical axe violations (WCAG 2.x A/AA tags). */
export async function expectNoSeriousA11yViolations(page: Page, label: string): Promise<void> {
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const bad = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical');
  expect(
    bad.map((v) => `${v.id} (${v.impact}): ${v.nodes.length} node(s), e.g. ${v.nodes[0]?.target.join(' ')}`),
    `axe serious/critical violations on ${label}`,
  ).toEqual([]);
}

/** Polls Mailpit for the verification mail sent to `email` and returns the token from the link. */
export async function verificationToken(email: string): Promise<string> {
  let token: string | null = null;
  await expect
    .poll(
      async () => {
        const list = (await (
          await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)
        ).json()) as { messages?: { ID: string; Subject: string }[] };
        for (const m of list.messages ?? []) {
          if (/reset/i.test(m.Subject)) continue; // a reset mail also carries a token
          const msg = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${m.ID}`)).json()) as { Text?: string; HTML?: string };
          const hit = /verify-email\?token=([A-Za-z0-9._~%-]+)/.exec(`${msg.Text ?? ''}\n${msg.HTML ?? ''}`);
          if (hit?.[1]) {
            token = decodeURIComponent(hit[1]);
            return true;
          }
        }
        return false;
      },
      { message: `verification mail for ${email}`, timeout: 30_000 },
    )
    .toBe(true);
  return token as unknown as string;
}

/** Fictional PDF (magic bytes %PDF-) for the upload step. */
export function fictionalPdf(label: string): Buffer {
  return Buffer.from(
    `%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n` +
      `3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%% ${label} FICTIONAL\n%%EOF\n`,
  );
}

// ---------------------------------------------------------------------------------------------
// Phase 5 helpers: TOTP (RFC 6238), Mailpit search, psql, API shortcuts, logins.
// ---------------------------------------------------------------------------------------------

export const DEV_TOTP_SECRET = 'KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA';
const STEP_SECONDS = 30;
const TOTP_STATE_FILE = join(tmpdir(), 'kyro-totp-state.json');

function base32Decode(input: string): Buffer {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const ch of input.replace(/=+$/, '').toUpperCase()) {
    const idx = alphabet.indexOf(ch);
    if (idx < 0) throw new Error(`invalid base32 character ${ch}`);
    value = (value << 5) | idx;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

export function totpAtStep(secret: string, step: number): string {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const mac = createHmac('sha1', base32Decode(secret)).update(counter).digest();
  const offset = mac[mac.length - 1]! & 0x0f;
  const bin = ((mac[offset]! & 0x7f) << 24) | (mac[offset + 1]! << 16) | (mac[offset + 2]! << 8) | mac[offset + 3]!;
  return String(bin % 1_000_000).padStart(6, '0');
}

const nowStep = (): number => Math.floor(Date.now() / 1000 / STEP_SECONDS);

function readTotpState(): Record<string, number> {
  try {
    return existsSync(TOTP_STATE_FILE)
      ? (JSON.parse(readFileSync(TOTP_STATE_FILE, 'utf8')) as Record<string, number>)
      : {};
  } catch {
    return {};
  }
}

/**
 * A code for a time step that was not handed out before (the API rejects replays: a step can be used once
 * per credential). Steps within +-1 of "now" are valid, so this waits (<= ~30 s) only when the next free
 * step is not valid yet. The last step per secret is kept in a temp file shared with the smoke script.
 */
export async function nextTotp(secret: string): Promise<string> {
  const state = readTotpState();
  const step = Math.max(Math.max(state[secret] ?? 0, nowStep() - 1) + 1, nowStep());
  const waitMs = (step - 1) * STEP_SECONDS * 1000 - Date.now();
  if (waitMs > 0) await new Promise((r) => setTimeout(r, waitMs + 250));
  state[secret] = step;
  writeFileSync(TOTP_STATE_FILE, JSON.stringify(state));
  return totpAtStep(secret, step);
}

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');

/** Runs SQL in the compose postgres (grants roles to throwaway users, ages consent records). */
export function psql(sql: string): void {
  execFileSync(
    'docker',
    ['compose', 'exec', '-T', 'postgres', 'psql', '-U', 'kyro', '-d', 'kyro', '-v', 'ON_ERROR_STOP=1', '-c', sql],
    { cwd: REPO_ROOT, stdio: 'pipe' },
  );
}

/** Polls Mailpit for a message to `email` whose subject and body satisfy the filters. */
export async function waitForMail(
  email: string,
  filter: { subject?: RegExp; text?: RegExp },
  timeoutMs = 30_000,
): Promise<{ subject: string; text: string }> {
  let found: { subject: string; text: string } | null = null;
  await expect
    .poll(
      async () => {
        const list = (await (
          await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`)
        ).json()) as { messages?: { ID: string; Subject: string }[] };
        for (const m of list.messages ?? []) {
          if (filter.subject && !filter.subject.test(m.Subject)) continue;
          const msg = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${m.ID}`)).json()) as {
            Text?: string;
            HTML?: string;
          };
          const text = `${msg.Text ?? ''}\n${msg.HTML ?? ''}`;
          if (filter.text && !filter.text.test(text)) continue;
          found = { subject: m.Subject, text };
          return true;
        }
        return false;
      },
      { message: `mail to ${email} (${String(filter.subject)} ${String(filter.text)})`, timeout: timeoutMs },
    )
    .toBe(true);
  return found as unknown as { subject: string; text: string };
}

/** The token from the "Reset your KYRO password" mail to `email`. */
export async function passwordResetToken(email: string): Promise<string> {
  const mail = await waitForMail(email, { subject: /Reset your KYRO password/ });
  const m = /reset-password\?token=([A-Za-z0-9._~%-]+)/.exec(mail.text);
  if (!m?.[1]) throw new Error('reset link not found in the mail');
  return decodeURIComponent(m[1]);
}

/** Minimal API client from Node (cookie jar + double-submit CSRF) through the web app's /api proxy. */
export class ApiSession {
  private readonly jar = new Map<string, string>();
  private csrf: string | null = null;
  constructor(private readonly base: string = process.env.E2E_API_URL ?? WEB_URL) {}

  private cookie(): string {
    return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  private store(res: Response): void {
    for (const line of res.headers.getSetCookie()) {
      const [pair] = line.split(';');
      const i = pair!.indexOf('=');
      const k = pair!.slice(0, i).trim();
      const v = pair!.slice(i + 1).trim();
      if (/max-age=0/i.test(line) || v === '') this.jar.delete(k);
      else this.jar.set(k, v);
    }
  }
  async refreshCsrf(): Promise<void> {
    const res = await this.fetch('GET', '/api/v1/auth/csrf');
    this.csrf = ((await res.json()) as { token: string }).token;
  }
  async fetch(method: string, path: string, body?: unknown): Promise<Response> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (this.jar.size) headers.Cookie = this.cookie();
    if (method !== 'GET' && this.csrf) headers['X-XSRF-TOKEN'] = this.csrf;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    const res = await fetch(`${this.base}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      redirect: 'manual',
    });
    this.store(res);
    return res;
  }
  async post(path: string, body?: unknown): Promise<Response> {
    if (!this.csrf) await this.refreshCsrf();
    return this.fetch('POST', path, body);
  }
  async login(email: string, password: string): Promise<void> {
    await this.refreshCsrf();
    const res = await this.post('/api/v1/auth/session', { email, password });
    expect(res.status, `API login ${email}`).toBe(200);
    await this.refreshCsrf();
  }
}

/** Registers a fictional user and verifies the address through Mailpit (no browser). */
export async function registerUser(email: string, password: string, role: 'DONOR' | 'RECIPIENT'): Promise<void> {
  const api = new ApiSession();
  const reg = await api.post('/api/v1/auth/register', {
    email,
    password,
    displayName: `E2E ${role.toLowerCase()}`,
    publicDisplayName: `E2E ${role.toLowerCase()}`.slice(0, 40),
    countryCode: 'IN',
    roles: [role],
    consents: { termsVersion: '2026-11-01', privacyVersion: '2026-11-01' },
  });
  expect(reg.status, 'register').toBe(202);
  const token = await verificationToken(email);
  const ver = await api.post('/api/v1/auth/verify-email', { token });
  expect([200, 204], 'verify-email').toContain(ver.status);
}

/** Logs a user in through the web UI (waits for hydration before submitting). */
export async function webLogin(page: Page, email: string, password: string, expectedPath: RegExp): Promise<void> {
  await page.goto(`${WEB_URL}/login`);
  await page.waitForLoadState('networkidle');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(expectedPath);
}

/** Admin app login with the second step: password, then a TOTP code (dev seed secret for seeded staff). */
export async function adminLogin(
  page: Page,
  email: string,
  password: string = SEED_PASSWORD,
  secret: string = DEV_TOTP_SECRET,
): Promise<void> {
  await page.goto(`${ADMIN_URL}/login`);
  await page.waitForLoadState('networkidle');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/mfa\/verify/);
  await page.waitForLoadState('networkidle');
  await page.getByLabel('Authenticator code').fill(await nextTotp(secret));
  await page.getByRole('button', { name: 'Verify' }).click();
  await expect(page).not.toHaveURL(/\/(mfa|login)/);
}
