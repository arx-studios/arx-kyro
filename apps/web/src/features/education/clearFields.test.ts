import { describe, expect, it } from 'vitest';
import type { EducationRequest } from '@kyro/api-client';
import { requestToFormValues, toUpdatePayload } from './schemas';

const base = {
  id: 'r1',
  title: 'First year tuition',
  expenseType: 'TUITION',
  amount: { amountMinor: 1000000, currency: 'INR' },
  story: 'My story',
  institutionName: 'Fictional College',
  institutionCity: 'Pune',
  institutionCountry: 'IN',
  courseName: 'B.Sc',
  feeReference: 'FEE-1',
  dueDate: '2026-12-01',
  version: 3,
} as unknown as EducationRequest;

describe('toUpdatePayload clearFields (D-010)', () => {
  it('sends clearFields only for fields emptied that had a value', () => {
    const values = { ...requestToFormValues(base), courseName: '', dueDate: '' };
    const body = toUpdatePayload(values, 3, base);
    expect(body.clearFields).toEqual(['courseName', 'dueDate']);
    expect(body).not.toHaveProperty('courseName');
    expect(body).not.toHaveProperty('dueDate');
    expect(body.story).toBe('My story');
  });

  it('omits clearFields when nothing was emptied (untouched or changed)', () => {
    const values = { ...requestToFormValues(base), story: 'A new story' };
    const body = toUpdatePayload(values, 3, base);
    expect(body).not.toHaveProperty('clearFields');
    expect(body.story).toBe('A new story');
  });

  it('does not clear fields that were already empty', () => {
    const empty = { ...base, courseName: null, feeReference: undefined } as unknown as EducationRequest;
    const body = toUpdatePayload(requestToFormValues(empty), 3, empty);
    expect(body).not.toHaveProperty('clearFields');
    expect(body).not.toHaveProperty('courseName');
  });

  it('sends no clearFields without an initial request', () => {
    const body = toUpdatePayload({ ...requestToFormValues(base), story: '' }, 3);
    expect(body).not.toHaveProperty('clearFields');
  });
});
