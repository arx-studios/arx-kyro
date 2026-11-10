/**
 * Money utilities. Amounts are ALWAYS integer minor units (e.g. paise, cents)
 * plus an ISO 4217 currency code. No floating point arithmetic is used.
 */

export type MoneyParseError = 'EMPTY' | 'INVALID_FORMAT' | 'TOO_MANY_DECIMALS' | 'NEGATIVE' | 'OUT_OF_RANGE';

export type MoneyParseResult =
  | { ok: true; amountMinor: number }
  | { ok: false; error: MoneyParseError; message: string };

const MESSAGES: Record<MoneyParseError, string> = {
  EMPTY: 'Enter an amount.',
  INVALID_FORMAT: 'Enter a valid amount, for example 12,500.50.',
  TOO_MANY_DECIMALS: 'Too many decimal places for this currency.',
  NEGATIVE: 'Amount cannot be negative.',
  OUT_OF_RANGE: 'Amount is too large.',
};

/** Number of fraction digits (minor unit exponent) for an ISO 4217 code, per Intl (INR=2, JPY=0, KWD=3). */
export function minorUnitDigits(currency: string): number {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
}

/**
 * Format integer minor units as a localized currency string.
 * `formatMoney(1250050, 'INR')` -> "₹12,500.50" (en-IN).
 */
export function formatMoney(amountMinor: number, currency: string, locale = 'en-IN'): string {
  if (!Number.isSafeInteger(amountMinor)) {
    throw new RangeError('amountMinor must be a safe integer (minor units)');
  }
  const digits = minorUnitDigits(currency);
  const negative = amountMinor < 0;
  const abs = String(Math.abs(amountMinor)).padStart(digits + 1, '0');
  const whole = abs.slice(0, abs.length - digits);
  const frac = digits > 0 ? abs.slice(abs.length - digits) : '';
  const decimal = `${negative ? '-' : ''}${whole}${frac ? `.${frac}` : ''}`;
  // Intl.NumberFormat accepts exact decimal strings, so no float rounding occurs.
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(decimal as `${number}`);
}

/**
 * Parse user input into integer minor units. Accepts an optional currency
 * symbol/code, whitespace and "," grouping; "." is the decimal separator.
 * Returns an error result (never NaN) for invalid input.
 */
export function parseMoneyInput(input: string, currency: string): MoneyParseResult {
  const fail = (error: MoneyParseError): MoneyParseResult => ({ ok: false, error, message: MESSAGES[error] });
  const digits = minorUnitDigits(currency);

  let s = (input ?? '').normalize('NFKC').replace(/\s/g, '');
  if (s === '') return fail('EMPTY');
  if (s.startsWith('-')) return fail('NEGATIVE');
  // strip a currency symbol / 3-letter code prefix or suffix
  s = s.replace(/^[^\d.,]+/u, '').replace(/[^\d.,]+$/u, '');
  if (s === '') return fail('INVALID_FORMAT');

  // whole part: digits with optional comma grouping (en-IN or en-US style); optional fraction
  const m = /^(\d+|\d{1,3}(?:,\d{3})+|\d{1,2}(?:,\d{2})+,\d{3})(?:\.(\d*))?$/.exec(s) ?? /^()\.(\d+)$/.exec(s);
  if (!m) return fail('INVALID_FORMAT');
  const whole = (m[1] ?? '').replace(/,/g, '');
  const frac = m[2] ?? '';
  if (frac.length > digits) return fail('TOO_MANY_DECIMALS');

  const minor = BigInt((whole || '0') + frac.padEnd(digits, '0'));
  if (minor > BigInt(Number.MAX_SAFE_INTEGER)) return fail('OUT_OF_RANGE');
  return { ok: true, amountMinor: Number(minor) };
}
