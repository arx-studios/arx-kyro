import type { ReportReason } from '@kyro/api-client';

export type ReasonValue = ReportReason;

export const REASONS: { value: ReasonValue; label: string }[] = [
  { value: 'SUSPECTED_FRAUD', label: 'I suspect fraud' },
  { value: 'ALTERED_DOCUMENT', label: 'A document looks altered' },
  { value: 'DUPLICATE_REQUEST', label: 'This looks like a duplicate request' },
  { value: 'INAPPROPRIATE_CONTENT', label: 'Inappropriate content' },
  { value: 'PRIVACY_CONCERN', label: 'Privacy concern' },
  { value: 'OTHER', label: 'Something else' },
];

export const MAX_DETAILS = 2000;

export type ReportValidation =
  | { ok: true; reason: ReasonValue; details: string; errors: Record<string, never> }
  | { ok: false; errors: { reason?: string; details?: string } };

/** Reason is required; details are required (non-blank) for OTHER and capped at 2000 characters. */
export function validateReport(reason: ReasonValue | '', details: string): ReportValidation {
  const trimmed = details.trim();
  const errors: { reason?: string; details?: string } = {};
  if (!reason) errors.reason = 'Choose a reason.';
  if (reason === 'OTHER' && trimmed === '') errors.details = 'Please describe the concern.';
  if (trimmed.length > MAX_DETAILS) errors.details = `Details can be at most ${MAX_DETAILS} characters.`;
  if (errors.reason || errors.details || !reason) return { ok: false, errors };
  return { ok: true, reason, details: trimmed, errors: {} };
}
