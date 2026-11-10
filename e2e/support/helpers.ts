import AxeBuilder from '@axe-core/playwright';
import { expect, type Page } from '@playwright/test';

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
        ).json()) as { messages?: { ID: string }[] };
        const id = list.messages?.[0]?.ID;
        if (!id) return false;
        const msg = (await (await fetch(`${MAILPIT_URL}/api/v1/message/${id}`)).json()) as { Text?: string; HTML?: string };
        const m = /token=([A-Za-z0-9._~%-]+)/.exec(`${msg.Text ?? ''}\n${msg.HTML ?? ''}`);
        token = m?.[1] ? decodeURIComponent(m[1]) : null;
        return token !== null;
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
