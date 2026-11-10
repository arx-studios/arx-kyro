import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

function sources(dir: string) {
  return readdirSync(dir)
    .filter((f) => /\.(ts|tsx)$/.test(f) && !f.includes('.test.'))
    .map((f) => ({ f, text: readFileSync(path.join(dir, f), 'utf8') }));
}

describe('pledge wording and privacy', () => {
  const owned = [__dirname, path.join(__dirname, '..', 'reports')];
  const discovery = path.join(__dirname, '..', 'discovery');

  it('never describes pledges as raised or funded money', () => {
    for (const dir of owned) {
      for (const { f, text } of sources(dir)) {
        expect(text, f).not.toMatch(/\braised\b|\bfunded\b/i);
      }
    }
  });

  it('never references feeReference or other private fields', () => {
    for (const dir of owned) {
      for (const { f, text } of sources(dir)) {
        expect(text, f).not.toMatch(/feeReference|recipientEmail|documentId/);
      }
    }
  });

  it('the detail view no longer subtracts pledges from the remaining amount', () => {
    for (const { f, text } of sources(discovery)) {
      expect(text, f).not.toMatch(/-\s*r\.funding\.pledgedMinor/);
    }
  });
});
