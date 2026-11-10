import { describe, expect, it } from 'vitest';
import { canAccessConsole, hasErrors, safeNext, validateDecision } from './decision';

const ok = 'This is a sufficient rationale.';

describe('validateDecision', () => {
  it('requires a decision', () => {
    expect(validateDecision({ decision: '', rationale: ok, messageToRecipient: '' }).decision).toBeDefined();
  });

  it('requires a rationale of 10 to 2000 characters (trimmed)', () => {
    expect(validateDecision({ decision: 'APPROVE', rationale: '', messageToRecipient: '' }).rationale).toBeDefined();
    expect(validateDecision({ decision: 'APPROVE', rationale: '   short   ', messageToRecipient: '' }).rationale).toBeDefined();
    expect(validateDecision({ decision: 'APPROVE', rationale: 'x'.repeat(10), messageToRecipient: '' }).rationale).toBeUndefined();
    expect(validateDecision({ decision: 'APPROVE', rationale: 'x'.repeat(2000), messageToRecipient: '' }).rationale).toBeUndefined();
    expect(validateDecision({ decision: 'APPROVE', rationale: 'x'.repeat(2001), messageToRecipient: '' }).rationale).toBeDefined();
  });

  it('does not require a message for APPROVE', () => {
    expect(hasErrors(validateDecision({ decision: 'APPROVE', rationale: ok, messageToRecipient: '' }))).toBe(false);
  });

  it.each(['REJECT', 'REQUEST_CHANGES'] as const)('requires a recipient message for %s', (decision) => {
    expect(validateDecision({ decision, rationale: ok, messageToRecipient: '  ' }).messageToRecipient).toBeDefined();
    expect(hasErrors(validateDecision({ decision, rationale: ok, messageToRecipient: 'Please upload page 2.' }))).toBe(false);
  });

  it('caps the recipient message at 2000 characters', () => {
    expect(
      validateDecision({ decision: 'APPROVE', rationale: ok, messageToRecipient: 'm'.repeat(2001) }).messageToRecipient,
    ).toBeDefined();
  });
});

describe('canAccessConsole', () => {
  it('allows VERIFIER and ADMIN only', () => {
    expect(canAccessConsole(['VERIFIER'])).toBe(true);
    expect(canAccessConsole(['DONOR', 'ADMIN'])).toBe(true);
    expect(canAccessConsole(['RECIPIENT', 'DONOR'])).toBe(false);
    expect(canAccessConsole([])).toBe(false);
    expect(canAccessConsole(undefined)).toBe(false);
  });
});

describe('safeNext', () => {
  it('only accepts same-origin relative paths', () => {
    expect(safeNext('/cases/1')).toBe('/cases/1');
    expect(safeNext('//evil.example')).toBe('/queue');
    expect(safeNext('https://evil.example')).toBe('/queue');
    expect(safeNext(null)).toBe('/queue');
  });
});
