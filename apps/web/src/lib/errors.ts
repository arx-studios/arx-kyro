import { ApiError, type FieldError } from '@kyro/api-client';
import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';

/** Human-readable message for any thrown error. Never includes stack traces or internals. */
export function errorMessage(err: unknown, fallback = 'Something went wrong. Please try again.'): string {
  if (err instanceof ApiError) {
    if (err.code === 'RATE_LIMITED') return 'Too many attempts. Please wait a moment and try again.';
    if (err.status >= 500) return fallback;
    return err.message || fallback;
  }
  return fallback;
}

export function problemFieldErrors(err: unknown): FieldError[] {
  return err instanceof ApiError ? (err.problem.errors ?? []) : [];
}

/**
 * Copies server field errors onto a react-hook-form instance. `mapField` turns a server field
 * path (e.g. `amount.amountMinor`) into a form field name or undefined to skip it.
 * Returns the errors that could not be mapped.
 */
export function applyServerErrors<T extends FieldValues>(
  err: unknown,
  setError: UseFormSetError<T>,
  mapField: (serverField: string) => Path<T> | undefined,
): FieldError[] {
  const unmapped: FieldError[] = [];
  for (const fe of problemFieldErrors(err)) {
    const name = mapField(fe.field);
    if (name) setError(name, { type: 'server', message: fe.message });
    else unmapped.push(fe);
  }
  return unmapped;
}

/** Only same-site relative paths are allowed as a post-login redirect target. */
export function safeNextPath(next: string | null | undefined, fallback = '/'): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.includes('\\')) return fallback;
  return next;
}
