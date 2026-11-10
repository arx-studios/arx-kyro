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
//
// Admin MFA is enforced by default: staff logins use the dev TOTP secret (totp.mjs). Run against an API started
// with the raised limits from docs/runbooks/local-dev.md (the report limit must stay at its default of 5/min).
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { DEV_TOTP_SECRET, nextTotp } from './totp.mjs';

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
  /**
   * Staff login with enforced admin MFA: password, then the second step. Before the TOTP verify the admin API answers
   * 403 MFA_REQUIRED (or MFA_ENROLLMENT_REQUIRED when no authenticator is linked yet).
   */
  async loginStaff(email, secret = DEV_TOTP_SECRET) {
    const me = await this.login(email);
    check(me.mfa?.required === true && me.mfa?.verified === false, `${this.name}: login response says MFA required, not yet verified`, me.mfa);
    const blocked = await this.call('GET', '/api/v1/admin/dashboard');
    check(blocked.status === 403 && blocked.body?.code === 'MFA_REQUIRED', `${this.name}: admin API before MFA verify -> 403 MFA_REQUIRED`, blocked);
    await this.verifyTotp(secret);
    return me;
  }
  async verifyTotp(secret) {
    const code = await nextTotp(secret);
    const v = await this.call('POST', '/api/v1/auth/mfa/verify', { body: { code } });
    check(v.status === 200 && v.body?.mfa?.verified === true, `${this.name}: MFA verify -> 200 verified`, v);
    await this.refreshCsrf();
    const ok = await this.call('GET', '/api/v1/admin/dashboard');
    check(ok.status === 200, `${this.name}: admin API after MFA verify -> 200`, ok);
    return code;
  }
}

/** Searches Mailpit for a mail to `email` matching subject and body patterns; returns { subject, text }. */
async function mailpitFind(email, { subject, text }, attempts = 40) {
  for (let i = 0; i < attempts; i++) {
    const res = await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent(`to:${email}`)}`);
    const list = await res.json();
    for (const m of list.messages ?? []) {
      if (subject && !subject.test(m.Subject)) continue;
      const msg = await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
      const body = `${msg.Text ?? ''}\n${msg.HTML ?? ''}`;
      if (text && !text.test(body)) continue;
      return { subject: m.Subject, text: body };
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no matching mail for ${email}`);
}

async function mailpitLink(email, linkPattern = /verify-email\?token=([A-Za-z0-9._~%-]+)/) {
  const mail = await mailpitFind(email, { text: linkPattern });
  return decodeURIComponent(linkPattern.exec(mail.text)[1]);
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
  await ver.loginStaff('verifier@kyro.local');
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

  step('Status-change email for the approval (no internal rationale in it)');
  const approvedMail = await mailpitFind(rec.email, { subject: /Update on your KYRO request/, text: /was approved/ });
  check(approvedMail.text.includes(`/recipient/requests/${requestId}`), 'approval email links to the request');
  check(!approvedMail.text.includes('Fictional invoice matches'), 'approval email does not contain the internal rationale');

  step('Pledges: idempotency, uniqueness, cap, pledged vs confirmed, cancel');
  const pledger = await registerVerifyLogin('pledger', ['DONOR']);
  const pc = pledger.c;
  const pledgeBody = (amountMinor, ack = true) => ({ amount: { amountMinor, currency: 'INR' }, acknowledgeNonBinding: ack });
  const pledgeUrl = `/api/v1/funding/requests/${requestId}/pledges`;
  const sumBefore = await anon.call('GET', `/api/v1/funding/requests/${requestId}/summary`);
  check(sumBefore.status === 200 && sumBefore.body.confirmedMinor === 0 && sumBefore.body.pledgesAreNonBinding === true, 'public funding summary: confirmed 0, pledges non-binding', sumBefore.body);
  const targetMinor = sumBefore.body.target.amountMinor;
  const noKey = await pc.call('POST', pledgeUrl, { body: pledgeBody(100000) });
  check(noKey.status === 400 && noKey.body?.code === 'MALFORMED_REQUEST', 'pledge without Idempotency-Key -> 400 MALFORMED_REQUEST', noKey);
  const noAck = await pc.call('POST', pledgeUrl, { body: pledgeBody(100000, false), headers: { 'Idempotency-Key': randomUUID() } });
  check(noAck.status === 422, 'pledge without acknowledgement -> 422', noAck);
  const anonPledge = await anon.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': randomUUID() } });
  check(anonPledge.status === 401, 'anonymous pledge -> 401', anonPledge);
  const recPledge = await rec.c.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': randomUUID() } });
  check(recPledge.status === 403, 'recipient cannot pledge -> 403', recPledge);
  const key = randomUUID();
  const p1 = await pc.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': key } });
  check(p1.status === 201 && p1.body.status === 'ACTIVE' && p1.body.nonBinding === true, 'create pledge -> 201 ACTIVE non-binding', p1);
  const replay = await pc.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': key } });
  check(replay.status === 200 && replay.body.id === p1.body.id, 'idempotent replay -> 200 with the same pledge id', replay);
  const reuse = await pc.call('POST', pledgeUrl, { body: pledgeBody(200000), headers: { 'Idempotency-Key': key } });
  check(reuse.status === 422 && reuse.body?.code === 'IDEMPOTENCY_KEY_REUSED', 'same key, different body -> 422 IDEMPOTENCY_KEY_REUSED', reuse);
  const second = await pc.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': randomUUID() } });
  check(second.status === 409 && second.body?.code === 'ACTIVE_PLEDGE_EXISTS', 'second active pledge for the same request -> 409 ACTIVE_PLEDGE_EXISTS', second);
  const bigDonor = await registerVerifyLogin('bigdonor', ['DONOR']);
  const over = await bigDonor.c.call('POST', pledgeUrl, { body: pledgeBody(targetMinor + 100000), headers: { 'Idempotency-Key': randomUUID() } });
  check(over.status === 409, 'pledge above the remaining cap -> 409', over);
  const feedP = await anon.call('GET', '/api/v1/discovery/feed?limit=50');
  const cardP = feedP.body.items.find((i) => i.id === requestId);
  check(cardP.funding.pledgedMinor === 100000 && cardP.funding.confirmedMinor === 0, 'feed card: pledged 100000, confirmed still 0', cardP.funding);
  const sumP = await pc.call('GET', `/api/v1/funding/requests/${requestId}/summary`);
  check(sumP.body.remainingMinor === targetMinor && sumP.body.pledgedMinor === 100000 && sumP.body.viewerPledge?.id === p1.body.id, 'summary: remaining unchanged by the pledge; viewerPledge present', sumP.body);
  check(!JSON.stringify(sumP.body).includes(pledger.email), 'summary does not expose donor identity');
  const mineP = await pc.call('GET', '/api/v1/me/funding/pledges');
  check(mineP.status === 200 && mineP.body.items.some((i) => i.pledge.id === p1.body.id), 'my pledges lists the pledge', mineP.body);
  const foreignCancel = await bigDonor.c.call('POST', `/api/v1/funding/pledges/${p1.body.id}/cancel`);
  check(foreignCancel.status === 404 || foreignCancel.status === 403, 'another donor cannot cancel the pledge', foreignCancel);
  const cancelled = await pc.call('POST', `/api/v1/funding/pledges/${p1.body.id}/cancel`);
  check(cancelled.status === 200 && cancelled.body.status === 'CANCELLED', 'cancel pledge -> CANCELLED', cancelled);
  const feedC = await anon.call('GET', '/api/v1/discovery/feed?limit=50');
  check(feedC.body.items.find((i) => i.id === requestId).funding.pledgedMinor === 0, 'feed card: pledged back to 0 after cancel');

  step('Rate limit: the 6th report within a minute is 429');
  const spammer = await registerVerifyLogin('spammer', ['DONOR']);
  const reportBody = { requestId, reason: 'OTHER', details: 'Fictional smoke-test report.' };
  const statuses = [];
  let limited = null;
  for (let i = 0; i < 6; i++) {
    const r = await spammer.c.call('POST', '/api/v1/reports', { body: reportBody });
    statuses.push(r.status);
    if (r.status === 429) limited = r;
  }
  check(statuses[0] === 202 && statuses.slice(1, 5).every((s) => s === 409) && statuses[5] === 429, 'reports: 202, then 409 x4, then 429 on the 6th', statuses);
  check(Number(limited.headers.get('retry-after')) > 0, '429 carries Retry-After');

  step('Reports and moderation hold: report, claim, resolve with hold, hidden, ADMIN release');
  const reporter = await registerVerifyLogin('reporter', ['DONOR']);
  const rc = reporter.c;
  check((await anon.call('POST', '/api/v1/reports', { body: reportBody })).status === 401, 'anonymous report -> 401');
  check((await rec.c.call('POST', '/api/v1/reports', { body: reportBody })).status === 422, 'owner cannot report their own request -> 422');
  const noDetails = await rc.call('POST', '/api/v1/reports', { body: { requestId, reason: 'OTHER' } });
  check(noDetails.status === 422, 'reason OTHER without details -> 422', noDetails);
  const rep = await rc.call('POST', '/api/v1/reports', { body: { requestId, reason: 'SUSPECTED_FRAUD', details: 'Fictional concern from the smoke test.' } });
  check(rep.status === 202 && rep.body.status === 'OPEN', 'report -> 202 OPEN', rep);
  const dupe = await rc.call('POST', '/api/v1/reports', { body: { requestId, reason: 'SUSPECTED_FRAUD' } });
  check(dupe.status === 409 && dupe.body?.code === 'REPORT_ALREADY_OPEN', 'duplicate open report -> 409 REPORT_ALREADY_OPEN', dupe);
  check((await rc.call('GET', '/api/v1/admin/reports')).status === 403, 'donor cannot list reports -> 403');
  const rq = await ver.call('GET', '/api/v1/admin/reports?queue=OPEN&limit=50');
  const mine2 = rq.body.items?.find((r) => r.id === rep.body.id);
  check(rq.status === 200 && Boolean(mine2) && mine2.requestId === requestId, 'verifier sees the report in the OPEN queue', rq.body);
  const dash = await ver.call('GET', '/api/v1/admin/dashboard');
  check(dash.status === 200 && dash.body.openReports >= 1, 'dashboard counts open reports', dash.body);
  const assign = await ver.call('POST', `/api/v1/admin/reports/${rep.body.id}/assign`);
  check(assign.status === 200, 'claim report -> 200', assign);
  const shortNote = await ver.call('POST', `/api/v1/admin/reports/${rep.body.id}/resolve`, { body: { resolution: 'ACTION_TAKEN', note: 'short', holdRequest: true } });
  check(shortNote.status === 422, 'resolve with a too-short note -> 422', shortNote);
  const dismissHold = await ver.call('POST', `/api/v1/admin/reports/${rep.body.id}/resolve`, { body: { resolution: 'DISMISSED', note: 'Fictional: dismiss must not hold.', holdRequest: true } });
  check(dismissHold.status === 422, 'DISMISSED with a hold -> 422', dismissHold);
  const resolved = await ver.call('POST', `/api/v1/admin/reports/${rep.body.id}/resolve`, { body: { resolution: 'ACTION_TAKEN', note: 'Fictional: hide while we investigate.', holdRequest: true } });
  check(resolved.status === 200 && resolved.body.status === 'RESOLVED' && resolved.body.requestOnHold === true, 'resolve ACTION_TAKEN with hold -> 200', resolved.body);
  const feedH = await anon.call('GET', '/api/v1/discovery/feed?limit=50');
  check(!feedH.body.items.some((i) => i.id === requestId), 'held request is gone from the public feed');
  check((await anon.call('GET', `/api/v1/discovery/feed/${requestId}`)).status === 404, 'held request detail -> 404');
  const pledgeHeld = await bigDonor.c.call('POST', pledgeUrl, { body: pledgeBody(100000), headers: { 'Idempotency-Key': randomUUID() } });
  check(pledgeHeld.status === 404, 'pledge on a held request -> 404', pledgeHeld);
  const verRelease = await ver.call('POST', `/api/v1/admin/requests/${requestId}/hold/release`);
  check(verRelease.status === 403, 'VERIFIER cannot release a hold -> 403', verRelease);
  const adm = new Client('admin');
  await adm.loginStaff('admin@kyro.local');
  const release = await adm.call('POST', `/api/v1/admin/requests/${requestId}/hold/release`);
  check([200, 204].includes(release.status), 'ADMIN releases the hold', release);
  const feedR = await anon.call('GET', '/api/v1/discovery/feed?limit=50');
  check(feedR.body.items.some((i) => i.id === requestId), 'request is visible in the feed again after release');
  check((await anon.call('GET', `/api/v1/discovery/feed/${requestId}`)).status === 200, 'request detail is 200 again');
  const again2 = await adm.call('POST', `/api/v1/admin/requests/${requestId}/hold/release`);
  check(again2.status === 404, 'releasing again (no active hold) -> 404', again2);
  const holdAudit = await adm.call('GET', `/api/v1/admin/audit-events?entityId=${requestId}&limit=50`);
  check(holdAudit.status === 200 && holdAudit.body.items.length > 0, 'audit has events for the request', holdAudit.body);

  step('Optional field clearing (PATCH clearFields) and withdraw cancels pledges');
  const draftId = (
    await rec.c.call('POST', '/api/v1/education/requests', {
      body: {
        title: `Smoke clear ${run}`,
        expenseType: 'TUITION',
        amount: { amountMinor: 1000000, currency: 'INR' },
        story: 'Fictional story to be cleared again.',
        courseName: 'Fictional course',
        feeReference: `CLEAR-REF-${run}`,
      },
    })
  ).body.id;
  const draft = await rec.c.call('GET', `/api/v1/education/requests/${draftId}`);
  const cleared = await rec.c.call('PATCH', `/api/v1/education/requests/${draftId}`, {
    body: { version: draft.body.version, clearFields: ['courseName', 'feeReference'] },
  });
  check(cleared.status === 200 && !cleared.body.courseName && !cleared.body.feeReference, 'PATCH clearFields nulls the fields', cleared.body);
  const conflicting = await rec.c.call('PATCH', `/api/v1/education/requests/${draftId}`, {
    body: { version: cleared.body.version, story: 'Another story that is long enough.', clearFields: ['story'] },
  });
  check(conflicting.status === 422, 'a field both cleared and given -> 422', conflicting);
  const withdrawPledge = await pc.call('POST', pledgeUrl, { body: pledgeBody(150000), headers: { 'Idempotency-Key': randomUUID() } });
  check(withdrawPledge.status === 201, 'pledger pledges again after the cancel (new key)', withdrawPledge);
  const withdrawn = await rec.c.call('POST', `/api/v1/education/requests/${requestId}/withdraw`);
  check(withdrawn.status === 200 && withdrawn.body.status === 'WITHDRAWN', 'recipient withdraws the approved request', withdrawn);
  const afterWithdraw = await pc.call('GET', `/api/v1/funding/pledges/${withdrawPledge.body.id}`);
  check(afterWithdraw.body.status === 'CANCELLED' && afterWithdraw.body.cancelReason === 'REQUEST_WITHDRAWN', 'withdrawal cancelled the pledge (REQUEST_WITHDRAWN)', afterWithdraw.body);
  check((await anon.call('GET', `/api/v1/discovery/feed/${requestId}`)).status === 404, 'withdrawn request is not public');

  step('Password change and reset revoke other sessions');
  const second2 = new Client('other-session');
  await second2.login(other.email, PASSWORD);
  const NEW1 = 'SmokeChanged!2026-pw1';
  const NEW2 = 'SmokeReset!2026-pw2';
  const wrongCurrent = await other.c.call('POST', '/api/v1/me/password', { body: { currentPassword: 'definitely-wrong-pw', newPassword: NEW1 } });
  check(wrongCurrent.status === 422 && wrongCurrent.body?.code === 'CURRENT_PASSWORD_INCORRECT', 'change password with a wrong current -> 422 CURRENT_PASSWORD_INCORRECT', wrongCurrent);
  const changed = await other.c.call('POST', '/api/v1/me/password', { body: { currentPassword: PASSWORD, newPassword: NEW1 } });
  check([200, 204].includes(changed.status), 'change password', changed);
  check((await other.c.call('GET', '/api/v1/me')).status === 200, 'the changing session stays signed in');
  check((await second2.call('GET', '/api/v1/me')).status === 401, 'the other session is revoked after the password change -> 401');
  const oldLogin = new Client('old-password');
  await oldLogin.refreshCsrf();
  check((await oldLogin.call('POST', '/api/v1/auth/session', { body: { email: other.email, password: PASSWORD } })).status === 401, 'old password no longer works');
  const third = new Client('third-session');
  await third.login(other.email, NEW1);
  const unknownReset = await anon.call('POST', '/api/v1/auth/password-reset', { body: { email: `nobody.${run}@kyro.local` } });
  check(unknownReset.status === 202, 'password reset for an unknown address -> 202 (neutral)', unknownReset);
  const resetReq = await anon.call('POST', '/api/v1/auth/password-reset', { body: { email: other.email } });
  check(resetReq.status === 202, 'password reset request -> 202', resetReq);
  const resetToken = await mailpitLink(other.email, /reset-password\?token=([A-Za-z0-9._~%-]+)/);
  const weak = await anon.call('POST', '/api/v1/auth/password-reset/confirm', { body: { token: resetToken, newPassword: 'short' } });
  check(weak.status === 422, 'reset with a too-short password -> 422', weak);
  const confirmReset = await anon.call('POST', '/api/v1/auth/password-reset/confirm', { body: { token: resetToken, newPassword: NEW2 } });
  check([200, 204].includes(confirmReset.status), 'reset password with the Mailpit token', confirmReset);
  const reuseToken = await anon.call('POST', '/api/v1/auth/password-reset/confirm', { body: { token: resetToken, newPassword: NEW2 } });
  check(reuseToken.status === 422 && reuseToken.body?.code === 'TOKEN_INVALID', 'a reset token works only once -> 422 TOKEN_INVALID', reuseToken);
  check((await third.call('GET', '/api/v1/me')).status === 401, 'sessions from before the reset are revoked -> 401');
  const final = new Client('after-reset');
  await final.login(other.email, NEW2);

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
    const dualMe = await dual.login(rec.email, PASSWORD);
    check(dualMe.mfa?.required === true && dualMe.mfa?.enrolled === false, 'new staff user: MFA required, not enrolled', dualMe.mfa);
    const notEnrolled = await dual.call('GET', '/api/v1/admin/dashboard');
    check(notEnrolled.status === 403 && notEnrolled.body?.code === 'MFA_ENROLLMENT_REQUIRED', 'new staff user: admin API -> 403 MFA_ENROLLMENT_REQUIRED', notEnrolled);
    const enroll = await dual.call('POST', '/api/v1/auth/mfa/totp/enrollment');
    check([200, 201].includes(enroll.status) && enroll.body?.secret, 'start TOTP enrollment returns a secret', enroll);
    const wrongConfirm = await dual.call('POST', '/api/v1/auth/mfa/totp/enrollment/confirm', { body: { code: '000000' } });
    check(wrongConfirm.status === 422, 'confirm enrollment with a wrong code -> 422', wrongConfirm);
    const confirm = await dual.call('POST', '/api/v1/auth/mfa/totp/enrollment/confirm', {
      body: { code: await nextTotp(enroll.body.secret) },
    });
    check(confirm.status === 200 && confirm.body?.codes?.length === 10, 'confirm enrollment -> 10 recovery codes', confirm);
    await dual.verifyTotp(enroll.body.secret);
    const q =await dual.call('GET', '/api/v1/admin/verification/cases?queue=ALL&limit=50');
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
