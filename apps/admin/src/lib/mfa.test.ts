import { describe, expect, it } from 'vitest';
import { ApiError } from '@kyro/api-client';
import { mfaRedirectTarget, mfaTargetForError, normaliseRecoveryCode, RECOVERY_CODE_PATTERN } from './mfa';

const problem = (status: number, code: string) => new ApiError({ type: 't', title: 'x', status, code });

describe('mfa helpers', () => {
  it('maps user MFA state to a redirect', () => {
    expect(mfaRedirectTarget({})).toBeNull();
    expect(mfaRedirectTarget({ mfa: { required: false, enrolled: false, verified: false } })).toBeNull();
    expect(mfaRedirectTarget({ mfa: { required: true, enrolled: true, verified: true } })).toBeNull();
    expect(mfaRedirectTarget({ mfa: { required: true, enrolled: true, verified: false } })).toBe('/mfa/verify');
    expect(mfaRedirectTarget({ mfa: { required: true, enrolled: false, verified: false } })).toBe('/mfa/setup');
  });

  it('maps 403 MFA problem codes to redirects and ignores everything else', () => {
    expect(mfaTargetForError(problem(403, 'MFA_REQUIRED'))).toBe('/mfa/verify');
    expect(mfaTargetForError(problem(403, 'MFA_ENROLLMENT_REQUIRED'))).toBe('/mfa/setup');
    expect(mfaTargetForError(problem(403, 'FORBIDDEN'))).toBeNull();
    expect(mfaTargetForError(problem(401, 'MFA_REQUIRED'))).toBeNull();
    expect(mfaTargetForError(new Error('x'))).toBeNull();
  });

  it('normalises recovery codes', () => {
    expect(RECOVERY_CODE_PATTERN.test(normaliseRecoveryCode(' abcde-12345 '))).toBe(true);
    expect(RECOVERY_CODE_PATTERN.test('ABCDE12345')).toBe(false);
  });
});
