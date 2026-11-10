import { describe, expect, it } from 'vitest';
import { formatMoney, minorUnitDigits, parseMoneyInput } from './money';

describe('minorUnitDigits', () => {
  it('uses ISO 4217 exponents', () => {
    expect(minorUnitDigits('INR')).toBe(2);
    expect(minorUnitDigits('JPY')).toBe(0);
    expect(minorUnitDigits('KWD')).toBe(3);
  });
});

describe('parseMoneyInput', () => {
  it('parses grouped decimals into integer minor units', () => {
    expect(parseMoneyInput('12,500.50', 'INR')).toEqual({ ok: true, amountMinor: 1250050 });
  });
  it('parses en-IN grouping, symbols and whitespace', () => {
    expect(parseMoneyInput('₹ 1,25,000', 'INR')).toEqual({ ok: true, amountMinor: 12500000 });
    expect(parseMoneyInput(' 99.5 ', 'INR')).toEqual({ ok: true, amountMinor: 9950 });
    expect(parseMoneyInput('.5', 'INR')).toEqual({ ok: true, amountMinor: 50 });
  });
  it('has no float error (0.1 + 0.2 style, large values)', () => {
    expect(parseMoneyInput('1.15', 'INR')).toEqual({ ok: true, amountMinor: 115 });
    expect(parseMoneyInput('4503599627370.91', 'INR')).toEqual({ ok: true, amountMinor: 450359962737091 });
  });
  it('respects currency digits', () => {
    expect(parseMoneyInput('500', 'JPY')).toEqual({ ok: true, amountMinor: 500 });
    expect(parseMoneyInput('500.5', 'JPY')).toMatchObject({ ok: false, error: 'TOO_MANY_DECIMALS' });
    expect(parseMoneyInput('1.234', 'KWD')).toEqual({ ok: true, amountMinor: 1234 });
  });
  it.each([
    ['', 'EMPTY'],
    ['   ', 'EMPTY'],
    ['abc', 'INVALID_FORMAT'],
    ['12.3.4', 'INVALID_FORMAT'],
    ['1,2,3', 'INVALID_FORMAT'],
    ['12,50', 'INVALID_FORMAT'],
    ['1.234', 'TOO_MANY_DECIMALS'],
    ['-5', 'NEGATIVE'],
    ['99999999999999999999', 'OUT_OF_RANGE'],
  ])('returns an error result (not NaN) for %j', (input, error) => {
    const r = parseMoneyInput(input, 'INR');
    expect(r.ok).toBe(false);
    expect(r).toMatchObject({ ok: false, error });
    expect('amountMinor' in r).toBe(false);
  });
});

describe('formatMoney', () => {
  it('formats minor units', () => {
    expect(formatMoney(1250050, 'INR')).toBe('₹12,500.50');
    expect(formatMoney(5, 'INR')).toBe('₹0.05');
    expect(formatMoney(-150, 'INR')).toBe('-₹1.50');
    expect(formatMoney(500, 'JPY', 'en-US')).toBe('¥500');
    expect(formatMoney(12345, 'USD', 'en-US')).toBe('$123.45');
  });
  it('rejects non-integer amounts', () => {
    expect(() => formatMoney(1.5, 'INR')).toThrow(RangeError);
    expect(() => formatMoney(Number.NaN, 'INR')).toThrow(RangeError);
  });
  it('round-trips with parseMoneyInput', () => {
    for (const minor of [0, 1, 99, 100, 1250050, 999999999]) {
      const text = formatMoney(minor, 'INR');
      expect(parseMoneyInput(text, 'INR')).toEqual({ ok: true, amountMinor: minor });
    }
  });
});
