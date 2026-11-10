#!/usr/bin/env node
// KYRO vertical-slice smoke test (API level). Sandbox only: fictional data.
//
// Needs a running stack: docker compose (postgres, minio, mailpit), the API (profile local, seeded)
// and optionally web/admin if API_URL points at a Next proxy.
//
//   node scripts/smoke/slice.mjs
//
// Env: API_URL (default http://localhost:8080), MAILPIT_URL (default http://localhost:8025),
//      SMOKE_SKIP_SELF_REVIEW=1 to skip the step that needs `docker compose exec postgres psql`.
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const API = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const MAILPIT = (process.env.MAILPIT_URL ?? 'http://localhost:8025').replace(/\/$/, '');
const SEED_PASSWORD = process.env.KYRO_SEED_PASSWORD ?? 'KyroLocal!2026';
const PASSWORD = 'SmokeTest!2026-pw';
const run = randomUUID().slice(0, 8);

let checks = 0;
function check(cond, message, extra) {
  checks += 1;
  if (!cond) {
    console.error(`FAIL: ${message}`, extra === undefined ? '' : JSON.stringify(extra, null, 2));
    process.exit(1);
  }
  console.log(`  ok  ${message}`);
}
const step = (name) => console.log(`\n== ${name}`);

/** Minimal client with cookie jar and double-submit CSRF. */
class Client {
  constructor(name) {
    this.name = name;
    this.jar = new Map();
    this.csrf = null;
  }
  cookieHeader() {
    return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  store(res) {
    for (const line of res.headers.getSetCookie()) {
      const [pair] = line.split(';');
      const i = pair.indexOf('=');
      const k = pair.slice(0, i).trim();
      const v = pair.slice(i + 1).trim();
      if (/max-age=0/i.test(line) || v === '') this.jar.delete(k);
      else this.jar.set(k, v);
    }
  }
  async refreshCsrf() {
    const res = await this.raw('GET', '/api/v1/auth/csrf');
    this.csrf = (await res.json()).token;
  }
  async raw(method, path, { body, headers = {} } = {}) {
    const h = { Accept: 'application/json', ...headers };
    if (this.jar.size) h.Cookie = this.cookieHeader();
    if (method !== 'GET' && this.csrf) h['X-XSRF-TOKEN'] = this.csrf;
    if (body !== undefined) h['Content-Type'] = 'application/json';
    const send = () =>
      fetch(`${API}${path}`, {
        method,
        headers: h,
        body: body === undefined ? undefined : JSON.stringify(body),
        redirect: 'manual',
      });
    let res = await send();
    // Auth endpoints are rate limited per IP and minute (login 10, register 5). Wait out the window once.
    if (res.status === 429 && path.startsWith('/api/v1/auth/')) {
      console.log(`  (rate limited on ${path}, waiting 62s for the window to reset)`);
      await new Promise((r) => setTimeout(r, 62_000));
      res = await send();
    }
    this.store(res);
    return res;
  }
  /** Returns { status, body, headers }. */
  async call(method, path, opts) {
    if (method !== 'GET' && !this.csrf) await this.refreshCsrf();
    const res = await this.raw(method, path, opts);
    const text = await res.text();
    let body = null;
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }
    return { status: res.status, body, headers: res.headers };
  }
  async login(email, password = SEED_PASSWORD) {
    await this.refreshCsrf();
    const r = await this.call('POST', '/api/v1/auth/session', { body: { email, password } });
    check(r.status === 200, `${this.name}: login ${email} -> 200`, r);
    await this.refreshCsrf(); // session id rotated
    return r.body;
  }
}

async function mailpitLink(email, attempts = 30) {
  for (let i = 0; i < attempts; i++) {
    const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    const list = await res.json();
    if (list.messages?.length) {
      const msg = await (await fetch(`${MAILPIT}/api/v1/message/${list.messages[0].ID}`)).json();
      const m = /token=([A-Za-z0-9._~%-]+)/.exec(`${msg.Text ?? ''}\n${msg.HTML ?? ''}`);
      if (m) return decodeURIComponent(m[1]);
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no verification mail for ${email}`);
}

// Smallest valid-looking PDF (magic bytes %PDF-). Fictional content.
function fictionalPdf(label) {
  return Buffer.from(
    `%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n` +
      `3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\ntrailer<</Root 1 0 R>>\n%% ${label} FICTIONAL\n%%EOF\n`,
  );
}

async function registerVerifyLogin(label, roles) {
  const c = new Client(label);
  const email = `${label}.${run}@kyro.local`;
  await c.refreshCsrf();
  const reg = await c.call('POST', '/api/v1/auth/register', {
    body: {
      email,
      password: PASSWORD,
      displayName: `Smoke ${label}`,
      publicDisplayName: `Smoke ${label}`.slice(0, 40),
      countryCode: 'IN',
      roles,
      consents: { termsVersion: '2026-11-01', privacyVersion: '2026-11-01' },
    },
  });
  check(reg.status === 202, `${label}: register -> 202`, reg);
  const early = await c.call('POST', '/api/v1/auth/session', { body: { email, password: PASSWORD } });
  check(early.status === 403 && early.body?.code === 'EMAIL_NOT_VERIFIED', `${label}: login before verification -> 403 EMAIL_NOT_VERIFIED`, early);
  const token = await mailpitLink(email);
  const ver = await c.call('POST', '/api/v1/auth/verify-email', { body: { token } });
  check(ver.status === 204 || ver.status === 200, `${label}: verify-email via Mailpit token`, ver);
  const me = await c.login(email, PASSWORD);
  return { c, email, me };
}

async function createSubmittedRequest(c, title, withDocument = true) {
  const created = await c.call('POST', '/api/v1/education/requests', {
    body: {
      title,
      expenseType: 'TUITION',
      amount: { amountMinor: 4500000, currency: 'INR' },
      story: 'Fictional story for the smoke test: semester fees for a fictional institute.',
      institutionName: 'Fictional Institute of Technology',
      institutionCity: 'Pune',
      institutionCountry: 'IN',
      courseName: 'B.Tech Computer Science',
      feeReference: `SECRET-FEE-REF-${run}`,
      dueDate: '2027-12-31',
    },
  });
  check(created.status === 201, `create request "${title}" -> 201`, created);
  const id = created.body.id;
  if (withDocument) {
    const pdf = fictionalPdf(title);
    const auth = await c.call('POST', `/api/v1/education/requests/${id}/documents/upload-url`, {
      body: { documentType: 'TUITION_INVOICE', fileName: 'invoice.pdf', contentType: 'application/pdf', sizeBytes: pdf.length },
    });
    check(auth.status === 201, 'upload-url -> 201', auth);
    check(!JSON.stringify(auth.body.document).includes('storageKey'), 'upload-url response document has no storage key');
    const put = await fetch(auth.body.uploadUrl, { method: 'PUT', headers: auth.body.requiredHeaders, body: pdf });
    check(put.ok, `PUT fictional PDF to presigned MinIO URL -> ${put.status}`);
    const done = await c.call('POST', `/api/v1/education/requests/${id}/documents/${auth.body.document.id}/complete`);
    check(done.status === 200 && done.body.status === 'UPLOADED', 'complete upload -> UPLOADED', done);
  }
  const submit = await c.call('POST', `/api/v1/education/requests/${id}/submit`);
  check(submit.status === 200, `submit -> 200`, submit);
  check(['SUBMITTED', 'IN_REVIEW'].includes(submit.body.status), `request status ${submit.body.status}`);
  return id;
}

const FORBIDDEN_KEYS = ['feeReference', 'recipientUserId', 'email', 'documents', 'storageKey', 'rationale', 'passwordHash'];
function assertNoPrivateFields(value, where) {
  const text = JSON.stringify(value);
  for (const k of FORBIDDEN_KEYS) check(!text.includes(`"${k}"`), `${where}: no "${k}" field`);
  check(!text.includes(`SECRET-FEE-REF-${run}`), `${where}: fee reference value not exposed`);
}

async function main() {
  console.log(`KYRO smoke: API=${API} MAILPIT=${MAILPIT} run=${run}`);

  step('Health and policies');
  const health = await fetch(`${API}/actuator/health`).catch(() => null);
  // A Next proxy (API_URL=http://localhost:3000) does not expose /actuator: 404 is fine there.
  if (health && health.status !== 404) check(health.status === 200, 'actuator health 200');
  const anon = new Client('anon');
  const pol = await anon.call('GET', '/api/v1/policies/current');
  check(pol.status === 200 && pol.body.termsVersion, 'GET /policies/current', pol);

  step('Recipient: register, verify via Mailpit, login');
  const rec = await registerVerifyLogin('recipient', ['RECIPIENT']);
  check(rec.me.roles.includes('RECIPIENT'), 'me has RECIPIENT role', rec.me);

  step('Recipient: create request, upload document to MinIO, submit');
  const title = `Smoke tuition ${run}`;
  const requestId = await createSubmittedRequest(rec.c, title);

  step('Negative: another recipient cannot read it (404); anonymous swipe is 401');
  const other = await registerVerifyLogin('otherrecipient', ['RECIPIENT']);
  const foreign = await other.c.call('GET', `/api/v1/education/requests/${requestId}`);
  check(foreign.status === 404, 'other recipient GET foreign request -> 404', foreign);
  const anonSwipe = await anon.call('POST', `/api/v1/discovery/requests/${requestId}/swipes`, { body: { action: 'SAVED' } });
  check(anonSwipe.status === 401, 'anonymous swipe -> 401', anonSwipe);
  const recSwipeList = await rec.c.call('GET', '/api/v1/admin/verification/cases');
  check(recSwipeList.status === 403, 'recipient cannot list verification cases -> 403', recSwipeList);

  step('Not visible before approval');
  const preFeed = await anon.call('GET', '/api/v1/discovery/feed?limit=50');
  check(preFeed.status === 200 && !preFeed.body.items.some((i) => i.id === requestId), 'submitted request is not in the public feed');

  step('Verifier: queue, claim, view document, approve');
  const ver = new Client('verifier');
  await ver.login('verifier@kyro.local');
  const queue = await ver.call('GET', '/api/v1/admin/verification/cases?queue=ALL&limit=50');
  check(queue.status === 200, 'queue UNASSIGNED -> 200', queue);
  const kase = queue.body.items.find((i) => i.requestId === requestId);
  check(Boolean(kase), 'case for the submitted request is in the unassigned queue', queue.body.items.map((i) => i.requestId));
  const claim = await ver.call('POST', `/api/v1/admin/verification/cases/${kase.id}/assign`);
  check(claim.status === 200, 'claim case -> 200', claim);
  const detail = await ver.call('GET', `/api/v1/admin/verification/cases/${kase.id}`);
  check(detail.status === 200 && detail.body.canDecide === true, 'case detail canDecide=true', detail.body);
  const doc = detail.body.request.documents[0];
  check(Boolean(doc), 'case shows the uploaded document');
  const view = await ver.call('POST', `/api/v1/admin/verification/cases/${kase.id}/documents/${doc.id}/view-url`);
  check(view.status === 200 || view.status === 201, 'document view-url issued', view);
  const file = await fetch(view.body.url);
  const bytes = Buffer.from(await file.arrayBuffer());
  check(file.ok && bytes.subarray(0, 5).toString() === '%PDF-', 'presigned view URL returns the PDF');
  const noRationale = await ver.call('POST', `/api/v1/admin/verification/cases/${kase.id}/decision`, { body: { decision: 'APPROVE', rationale: 'short' } });
  check(noRationale.status === 422, 'decision with short rationale -> 422', noRationale);
  const approve = await ver.call('POST', `/api/v1/admin/verification/cases/${kase.id}/decision`, {
    body: { decision: 'APPROVE', rationale: 'Fictional invoice matches the request details.' },
  });
  check(approve.status === 200, 'approve -> 200', approve);
  const audit = await ver.call('GET', `/api/v1/admin/audit-events?entityId=${requestId}`);
  check(audit.status === 200 && audit.body.items.length > 0, 'audit events recorded for the request', audit.body);

  step('Recipient sees APPROVED');
  const mine = await rec.c.call('GET', `/api/v1/education/requests/${requestId}`);
  check(mine.body.status === 'APPROVED', 'request status APPROVED', mine.body.status);

  step('Donor: feed contains request, swipe SAVED, saved list contains it');
  const donor = new Client('donor');
  await donor.login('donor.demo@kyro.local');
  const feed = await donor.call('GET', '/api/v1/discovery/feed?limit=50');
  check(feed.status === 200, 'feed -> 200', feed);
  const card = feed.body.items.find((i) => i.id === requestId);
  check(Boolean(card), 'approved request appears in the donor feed');
  assertNoPrivateFields(feed.body, 'feed');
  const pubDetail = await donor.call('GET', `/api/v1/discovery/feed/${requestId}`);
  check(pubDetail.status === 200, 'public detail -> 200', pubDetail);
  assertNoPrivateFields(pubDetail.body, 'public detail');
  const swipe = await donor.call('POST', `/api/v1/discovery/requests/${requestId}/swipes`, { body: { action: 'SAVED' } });
  check(swipe.status === 200 && swipe.body.action === 'SAVED', 'swipe SAVED -> 200', swipe);
  const again = await donor.call('POST', `/api/v1/discovery/requests/${requestId}/swipes`, { body: { action: 'SAVED' } });
  check(again.status === 200, 'swipe is idempotent');
  const saved = await donor.call('GET', '/api/v1/discovery/saved');
  check(saved.body.items.some((i) => i.request.id === requestId && i.action === 'SAVED'), 'saved list contains the request');
  assertNoPrivateFields(saved.body, 'saved');
  const interested = await donor.call('POST', `/api/v1/discovery/requests/${requestId}/swipes`, { body: { action: 'INTERESTED' } });
  check(interested.status === 200, 'swipe INTERESTED -> 200', interested);
  const saved2 = await donor.call('GET', '/api/v1/discovery/saved');
  check(saved2.body.items.some((i) => i.request.id === requestId && i.action === 'INTERESTED'), 'saved list shows INTERESTED (latest action wins)');
  const recSwipe = await rec.c.call('POST', `/api/v1/discovery/requests/${requestId}/swipes`, { body: { action: 'SAVED' } });
  check(recSwipe.status === 403, 'recipient (not a donor) cannot swipe -> 403', recSwipe);
  const noCsrf = await fetch(`${API}/api/v1/discovery/requests/${requestId}/swipes`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Cookie: donor.cookieHeader() },
    body: JSON.stringify({ action: 'PASS' }),
  });
  check(noCsrf.status === 403, 'POST without CSRF header -> 403', noCsrf.status);

  step('Self-review is forbidden');
  if (process.env.SMOKE_SKIP_SELF_REVIEW === '1') {
    console.log('  skipped (SMOKE_SKIP_SELF_REVIEW=1)');
  } else {
    const second = await createSubmittedRequest(rec.c, `Smoke self-review ${run}`);
    try {
      execFileSync(
        'docker',
        [
          'compose', 'exec', '-T', 'postgres', 'psql', '-U', 'kyro', '-d', 'kyro', '-v', 'ON_ERROR_STOP=1', '-c',
          `INSERT INTO user_roles (user_id, role_code) SELECT id, 'VERIFIER' FROM users WHERE email = '${rec.email}'`,
        ],
        { stdio: 'pipe' },
      );
    } catch (e) {
      console.error('Could not grant VERIFIER via docker compose exec postgres psql. Set SMOKE_SKIP_SELF_REVIEW=1 to skip.', String(e.stderr ?? e));
      process.exit(1);
    }
    const dual = new Client('recipient-verifier');
    await dual.login(rec.email, PASSWORD);
    const q = await dual.call('GET', '/api/v1/admin/verification/cases?queue=ALL&limit=50');
    const own = q.body.items?.find((i) => i.requestId === second);
    check(Boolean(own), 'own request case is visible to the dual-role user', q);
    const selfClaim = await dual.call('POST', `/api/v1/admin/verification/cases/${own.id}/assign`);
    check(selfClaim.status === 403 && selfClaim.body?.code === 'SELF_REVIEW_FORBIDDEN', 'self-claim -> 403 SELF_REVIEW_FORBIDDEN', selfClaim);
    const adminCall = await ver.call('GET', `/api/v1/admin/audit-events?entityId=${own.id}`);
    check(adminCall.body.items.some((e) => e.action === 'ACCESS_DENIED'), 'ACCESS_DENIED audited for the self-review attempt', adminCall.body);
  }

  console.log(`\nSMOKE PASSED (${checks} checks)`);
}

main().catch((e) => {
  console.error('SMOKE ERROR:', e);
  process.exit(1);
});
