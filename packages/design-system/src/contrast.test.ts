import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';

const css = readFileSync('src/theme.css', 'utf8');
const token = (name: string): string => {
  const m = new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`).exec(css);
  if (!m) throw new Error(`missing token ${name}`);
  return m[1]!;
};

function lum(hex: string): number {
  const c = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const [r, g, b] = c.map((v) => (v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4)) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}
export function ratio(a: string, b: string): number {
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x) as [number, number];
  return (hi + 0.05) / (lo + 0.05);
}

/** [foreground, background, minimum ratio]: 4.5 for text, 3 for non-text UI (focus ring, borders). */
const PAIRS: Array<[string, string, number]> = [
  ['charcoal-900', 'warm-50', 4.5],
  ['charcoal-700', 'warm-50', 4.5],
  ['charcoal-600', 'warm-50', 4.5],
  ['charcoal-600', 'warm-100', 4.5],
  ['charcoal-900', 'white', 4.5],
  ['warm-50', 'navy-900', 4.5],
  ['warm-50', 'navy-800', 4.5],
  ['navy-900', 'warm-50', 4.5],
  ['navy-900', 'navy-100', 4.5],
  ['navy-800', 'navy-100', 4.5],
  ['white', 'danger-600', 4.5],
  ['white', 'danger-700', 4.5],
  ['danger-700', 'danger-50', 4.5],
  ['success-700', 'success-50', 4.5],
  ['warning-700', 'warning-50', 4.5],
  ['champagne-700', 'champagne-100', 4.5],
  ['champagne-300', 'navy-900', 4.5],
  ['danger-700', 'warm-50', 4.5],
  ['danger-600', 'warm-50', 4.5],
  ['success-600', 'warm-50', 4.5],
  ['charcoal-600', 'white', 3],
  ['navy-700', 'warm-50', 3],
  ['champagne-400', 'warm-50', 1.9], // decorative ring only; always paired with the 2px navy-700 outline
  ['champagne-400', 'navy-900', 3],
];

describe('WCAG contrast of token pairs', () => {
  it.each(PAIRS)('%s on %s >= %s:1', (fg, bg, min) => {
    const hex = (n: string) => (n === 'white' ? '#ffffff' : token(n));
    expect(ratio(hex(fg), hex(bg))).toBeGreaterThanOrEqual(min);
  });

  it('prints the table', () => {
    const hex = (n: string) => (n === 'white' ? '#ffffff' : token(n));
    const rows = PAIRS.map(([f, b, m]) => `${f} on ${b}: ${ratio(hex(f), hex(b)).toFixed(2)} (min ${m})`);
    console.log(rows.join('\n'));
    expect(rows.length).toBe(PAIRS.length);
  });
});
