import type { ReportReason, ReportResolution } from '@kyro/api-client';

export const NOTE_MIN = 10;
export const NOTE_MAX = 2000;

export interface ResolutionDraft {
  resolution: ReportResolution | '';
  note: string;
  holdRequest: boolean;
}

export interface ResolutionErrors {
  resolution?: string;
  note?: string;
}

export const RESOLUTION_LABELS: Record<ReportResolution, string> = {
  DISMISSED: 'Dismiss (no action needed)',
  ACTION_TAKEN: 'Action taken',
};

export const REASON_LABELS: Record<ReportReason, string> = {
  SUSPECTED_FRAUD: 'Suspected fraud',
  ALTERED_DOCUMENT: 'Altered document',
  DUPLICATE_REQUEST: 'Duplicate request',
  INAPPROPRIATE_CONTENT: 'Inappropriate content',
  PRIVACY_CONCERN: 'Privacy concern',
  OTHER: 'Other',
};

/** The hold option only exists for ACTION_TAKEN (the server returns 422 otherwise). */
export function holdAllowed(resolution: ReportResolution | ''): boolean {
  return resolution === 'ACTION_TAKEN';
}

export function validateResolution(draft: ResolutionDraft): ResolutionErrors {
  const errors: ResolutionErrors = {};
  if (!draft.resolution) errors.resolution = 'Choose a resolution.';
  const note = draft.note.trim();
  if (note.length < NOTE_MIN) errors.note = `A note is required (at least ${NOTE_MIN} characters).`;
  else if (note.length > NOTE_MAX) errors.note = `Note must be at most ${NOTE_MAX} characters.`;
  return errors;
}
