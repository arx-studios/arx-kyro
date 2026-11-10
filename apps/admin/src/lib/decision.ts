import type { Decision } from '@kyro/api-client';

export const RATIONALE_MIN = 10;
export const RATIONALE_MAX = 2000;
export const MESSAGE_MAX = 2000;

export interface DecisionDraft {
  decision: Decision | '';
  rationale: string;
  messageToRecipient: string;
}

export interface DecisionErrors {
  decision?: string;
  rationale?: string;
  messageToRecipient?: string;
}

export const DECISION_LABELS: Record<Decision, string> = {
  APPROVE: 'Approve',
  REQUEST_CHANGES: 'Request changes',
  REJECT: 'Reject',
};

export function messageRequired(decision: Decision | ''): boolean {
  return decision === 'REJECT' || decision === 'REQUEST_CHANGES';
}

/** Mirrors the server rules (DecisionInput). The server stays authoritative. */
export function validateDecision(draft: DecisionDraft): DecisionErrors {
  const errors: DecisionErrors = {};
  if (!draft.decision) errors.decision = 'Choose a decision.';

  const rationale = draft.rationale.trim();
  if (rationale.length < RATIONALE_MIN) {
    errors.rationale = `Rationale is required (at least ${RATIONALE_MIN} characters).`;
  } else if (rationale.length > RATIONALE_MAX) {
    errors.rationale = `Rationale must be at most ${RATIONALE_MAX} characters.`;
  }

  const message = draft.messageToRecipient.trim();
  if (messageRequired(draft.decision) && message.length === 0) {
    errors.messageToRecipient = 'A message to the recipient is required for this decision.';
  } else if (message.length > MESSAGE_MAX) {
    errors.messageToRecipient = `Message must be at most ${MESSAGE_MAX} characters.`;
  }
  return errors;
}

export function hasErrors(errors: DecisionErrors): boolean {
  return Object.keys(errors).length > 0;
}

/** Roles allowed into the console. UI convenience only: the API enforces this. */
export const CONSOLE_ROLES = ['VERIFIER', 'ADMIN'] as const;

export function canAccessConsole(roles: readonly string[] | undefined): boolean {
  return !!roles?.some((r) => (CONSOLE_ROLES as readonly string[]).includes(r));
}

/** Only same-origin relative paths are accepted as post-login targets. */
export function safeNext(next: string | null | undefined): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/queue';
  return next;
}
