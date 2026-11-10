import { formatMoney, minorUnitDigits, parseMoneyInput } from '@kyro/design-system';

/** Outstanding need = target - confirmed. Pledges are not payments, so they are not an input. */
export function outstandingMinor(targetMinor: number, confirmedMinor: number): number {
  return Math.max(0, targetMinor - confirmedMinor);
}

/** Share of the target that is confirmed (0-100). Pledges do not move the progress bar. */
export function confirmedPercent(targetMinor: number, confirmedMinor: number): number {
  return targetMinor > 0 ? Math.min(100, Math.round((confirmedMinor / targetMinor) * 100)) : 0;
}

export type PledgeAmountResult = { ok: true; amountMinor: number } | { ok: false; message: string };

/** Client-side pledge amount checks: at least one major unit (₹1) and at most the pledgeable amount. */
export function validatePledgeAmount(input: string, currency: string, pledgeableMinor: number): PledgeAmountResult {
  const parsed = parseMoneyInput(input, currency);
  if (!parsed.ok) return { ok: false, message: parsed.message };
  const minimum = 10 ** minorUnitDigits(currency);
  if (parsed.amountMinor < minimum) return { ok: false, message: `The smallest pledge is ${formatMoney(minimum, currency)}.` };
  if (parsed.amountMinor > pledgeableMinor) {
    return { ok: false, message: 'That is more than can still be pledged for this request.' };
  }
  return { ok: true, amountMinor: parsed.amountMinor };
}
