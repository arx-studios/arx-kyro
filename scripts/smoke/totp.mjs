// RFC 6238 TOTP (SHA-1, 6 digits, 30 s) with replay-aware step selection. Sandbox helper only.
//
// The API refuses a code whose time step is <= the credential's last used step and accepts steps within
// +-1 of "now". So two MFA logins for the same user need two different steps: this helper remembers the
// last step it handed out per secret (in a small file shared with the Playwright helpers, so smoke and
// e2e runs back to back do not collide) and waits for the clock when the next free step is not yet valid.
import { createHmac } from 'node:crypto';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

export const DEV_TOTP_SECRET = 'KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA';
const STEP_SECONDS = 30;
const STATE_FILE = join(tmpdir(), 'kyro-totp-state.json');

function base32Decode(input) {
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
  let bits = 0;
  let value = 0;
  const out = [];
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

export function totpAtStep(secret, step) {
  const counter = Buffer.alloc(8);
  counter.writeBigUInt64BE(BigInt(step));
  const mac = createHmac('sha1', base32Decode(secret)).update(counter).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  const bin = ((mac[offset] & 0x7f) << 24) | (mac[offset + 1] << 16) | (mac[offset + 2] << 8) | mac[offset + 3];
  return String(bin % 1_000_000).padStart(6, '0');
}

const nowStep = () => Math.floor(Date.now() / 1000 / STEP_SECONDS);

function readState() {
  try {
    return existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, 'utf8')) : {};
  } catch {
    return {};
  }
}

/** A code for a step not used before by this process family; waits (<= ~30 s) when the next step is not valid yet. */
export async function nextTotp(secret) {
  const state = readState();
  const want = Math.max(state[secret] ?? 0, nowStep() - 1) + 1; // never below "now"; never reuse a handed-out step
  const step = Math.max(want, nowStep());
  const waitMs = (step - 1) * STEP_SECONDS * 1000 - Date.now(); // step is valid from (step-1) * 30 s on (skew 1)
  if (waitMs > 0) await new Promise((r) => setTimeout(r, waitMs + 250));
  state[secret] = step;
  writeFileSync(STATE_FILE, JSON.stringify(state));
  return totpAtStep(secret, step);
}
