import { describe, expect, it } from 'vitest';
import { confirmedPercent, outstandingMinor, validatePledgeAmount } from './amounts';

describe('funding amounts', () => {
  it('outstanding need ignores pledges (target - confirmed)', () => {
    expect(outstandingMinor(4500000, 0)).toBe(4500000);
    expect(outstandingMinor(4500000, 1000000)).toBe(3500000);
    expect(outstandingMinor(4500000, 9000000)).toBe(0);
  });
  it('progress uses confirmed only', () => {
    expect(confirmedPercent(1000, 0)).toBe(0);
    expect(confirmedPercent(1000, 250)).toBe(25);
    expect(confirmedPercent(0, 0)).toBe(0);
  });
  it('validates the pledge amount against 1 unit and the pledgeable maximum', () => {
    expect(validatePledgeAmount('5,000', 'INR', 1000000)).toEqual({ ok: true, amountMinor: 500000 });
    expect(validatePledgeAmount('0.50', 'INR', 1000000).ok).toBe(false);
    expect(validatePledgeAmount('10,001', 'INR', 1000000).ok).toBe(false);
    expect(validatePledgeAmount('', 'INR', 1000000).ok).toBe(false);
    expect(validatePledgeAmount('abc', 'INR', 1000000).ok).toBe(false);
  });
});
