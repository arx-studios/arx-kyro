import { describe, expect, it } from 'vitest';
import {
  EMPTY_REQUEST_FORM,
  minorToInputString,
  requestFormSchema,
  toCreatePayload,
  toUpdatePayload,
} from './schemas';

const valid = { ...EMPTY_REQUEST_FORM, title: 'First year tuition', amount: '12,500.50' };

describe('request form money input', () => {
  it('converts to integer minor units with no floats', () => {
    expect(toCreatePayload(valid).amount).toEqual({ amountMinor: 1250050, currency: 'INR' });
    expect(toCreatePayload({ ...valid, amount: '0.07' }).amount.amountMinor).toBe(7);
    expect(toCreatePayload({ ...valid, amount: '19.99' }).amount.amountMinor).toBe(1999);
  });

  it.each(['', 'abc', '12.345', '-5', '0', '12,50'])('rejects %j', (amount) => {
    const r = requestFormSchema.safeParse({ ...valid, amount });
    expect(r.success).toBe(false);
    if (!r.success) expect(r.error.issues.some((i) => i.path[0] === 'amount')).toBe(true);
  });

  it('round-trips minor units to the input string', () => {
    expect(minorToInputString(1250050, 'INR')).toBe('12500.50');
    expect(minorToInputString(1250000, 'INR')).toBe('12500');
    expect(minorToInputString(5, 'INR')).toBe('0.05');
  });
});

describe('request payloads', () => {
  it('omits empty optional fields and includes the version on update', () => {
    const create = toCreatePayload(valid);
    expect(create).not.toHaveProperty('story');
    expect(create).not.toHaveProperty('dueDate');
    const upd = toUpdatePayload({ ...valid, story: 'x'.repeat(60), dueDate: '2030-01-31' }, 7);
    expect(upd).toMatchObject({ version: 7, dueDate: '2030-01-31' });
  });

  it('validates title length and country code', () => {
    expect(requestFormSchema.safeParse({ ...valid, title: 'abc' }).success).toBe(false);
    expect(requestFormSchema.safeParse({ ...valid, institutionCountry: 'india' }).success).toBe(false);
    expect(requestFormSchema.safeParse(valid).success).toBe(true);
  });
});
