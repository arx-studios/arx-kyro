import { ApiError } from '@kyro/api-client';

export interface FriendlyProblem {
  title: string;
  message: string;
  tone: 'warning' | 'danger';
  correlationId?: string;
  fieldErrors: { field: string; message: string }[];
}

const KNOWN: Record<string, { title: string; message: string; tone: 'warning' | 'danger' }> = {
  CASE_ALREADY_ASSIGNED: {
    title: 'Case already claimed',
    message: 'Another reviewer has already claimed this case. Reload to see its current state.',
    tone: 'warning',
  },
  CASE_NOT_ASSIGNED_TO_YOU: {
    title: 'You are not the assignee',
    message: 'Only the reviewer who claimed this case can record a decision. Claim the case first, or reload.',
    tone: 'warning',
  },
  SELF_REVIEW_FORBIDDEN: {
    title: 'You cannot review your own request',
    message: 'Reviewers cannot claim or decide requests they own. Another reviewer must handle this case.',
    tone: 'danger',
  },
  VALIDATION_FAILED: {
    title: 'Check the form',
    message: 'The server rejected some of the values you entered.',
    tone: 'warning',
  },
  RATE_LIMITED: {
    title: 'Too many attempts',
    message: 'Please wait a minute and try again.',
    tone: 'warning',
  },
};

/** Map any thrown value to copy suitable for an Alert. Never leaks stack traces. */
export function describeError(error: unknown): FriendlyProblem {
  if (error instanceof ApiError) {
    const known = KNOWN[error.code];
    const fieldErrors = error.problem.errors?.map((e) => ({ field: e.field, message: e.message })) ?? [];
    if (known) return { ...known, correlationId: error.correlationId, fieldErrors };
    if (error.status === 401) {
      return {
        title: 'Session expired',
        message: 'Your session has ended. Please sign in again.',
        tone: 'warning',
        correlationId: error.correlationId,
        fieldErrors,
      };
    }
    if (error.status === 403) {
      return {
        title: 'Not permitted',
        message: error.problem.detail ?? 'You do not have permission to do this.',
        tone: 'danger',
        correlationId: error.correlationId,
        fieldErrors,
      };
    }
    if (error.status === 409) {
      return {
        title: 'Conflict',
        message: error.problem.detail ?? 'The case changed in the meantime. Reload and try again.',
        tone: 'warning',
        correlationId: error.correlationId,
        fieldErrors,
      };
    }
    return {
      title: error.problem.title || 'Request failed',
      message: error.problem.detail ?? 'The server could not complete the request.',
      tone: error.status >= 500 ? 'danger' : 'warning',
      correlationId: error.correlationId,
      fieldErrors,
    };
  }
  return {
    title: 'Something went wrong',
    message: 'The request could not be completed. Check your connection and try again.',
    tone: 'danger',
    fieldErrors: [],
  };
}
