import { ApiError, type CurrentUser } from '@kyro/api-client';

export const MFA_REQUIRED_CODES = ['MFA_REQUIRED', 'MFA_ENROLLMENT_REQUIRED'] as const;

/** Where a signed-in staff user must go before using the console, or null when no step-up is needed. */
export function mfaRedirectTarget(user: Pick<CurrentUser, 'mfa'>): '/mfa/setup' | '/mfa/verify' | null {
  const mfa = user.mfa;
  if (!mfa || !mfa.required || mfa.verified) return null;
  return mfa.enrolled ? '/mfa/verify' : '/mfa/setup';
}

/** Target implied by a 403 problem code, or null when the error is not an MFA one. */
export function mfaTargetForError(error: unknown): '/mfa/setup' | '/mfa/verify' | null {
  if (!(error instanceof ApiError) || error.status !== 403) return null;
  if (error.code === 'MFA_ENROLLMENT_REQUIRED') return '/mfa/setup';
  if (error.code === 'MFA_REQUIRED') return '/mfa/verify';
  return null;
}

export const RECOVERY_CODE_PATTERN = /^[A-Z0-9]{5}-[A-Z0-9]{5}$/;
export const TOTP_PATTERN = /^[0-9]{6}$/;

export function normaliseRecoveryCode(input: string): string {
  return input.trim().toUpperCase();
}
