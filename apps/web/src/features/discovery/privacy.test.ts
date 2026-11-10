import { readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

describe('discovery privacy', () => {
  it('never references private fields', () => {
    const dir = __dirname;
    const sources = readdirSync(dir).filter((f) => /\.(ts|tsx)$/.test(f) && !f.includes('.test.'));
    expect(sources.length).toBeGreaterThan(0);
    for (const f of sources) {
      const text = readFileSync(path.join(dir, f), 'utf8');
      expect(text, f).not.toMatch(/feeReference|recipientId|recipientEmail|\.documents\b|documentId/);
    }
  });
});
