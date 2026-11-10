'use client';

import { useState, type FormEvent } from 'react';
import type { Decision, DecisionInput } from '@kyro/api-client';
import { Alert, Button, Dialog, Field, Textarea } from '@kyro/design-system';
import {
  DECISION_LABELS,
  MESSAGE_MAX,
  RATIONALE_MAX,
  hasErrors,
  messageRequired,
  validateDecision,
  type DecisionDraft,
  type DecisionErrors,
} from '@/lib/decision';
import { describeError, type FriendlyProblem } from '@/lib/problems';

const OPTIONS: Decision[] = ['APPROVE', 'REQUEST_CHANGES', 'REJECT'];

export interface DecisionFormProps {
  /** Resolves on success, rejects (ideally with ApiError) on failure. */
  onDecide: (input: DecisionInput) => Promise<void>;
}

export function DecisionForm({ onDecide }: DecisionFormProps) {
  const [draft, setDraft] = useState<DecisionDraft>({ decision: '', rationale: '', messageToRecipient: '' });
  const [errors, setErrors] = useState<DecisionErrors>({});
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);

  const needsMessage = messageRequired(draft.decision);

  function onReview(e: FormEvent) {
    e.preventDefault();
    const found = validateDecision(draft);
    setErrors(found);
    setProblem(null);
    if (!hasErrors(found)) setConfirming(true);
  }

  async function confirm() {
    if (!draft.decision || pending) return;
    setPending(true);
    try {
      const message = draft.messageToRecipient.trim();
      await onDecide({
        decision: draft.decision,
        rationale: draft.rationale.trim(),
        ...(message ? { messageToRecipient: message } : {}),
      });
    } catch (err) {
      setProblem(describeError(err));
    } finally {
      setPending(false);
      setConfirming(false);
    }
  }

  return (
    <form onSubmit={onReview} noValidate aria-label="Record a decision" className="space-y-4">
      {problem ? (
        <Alert tone={problem.tone} title={problem.title}>
          <p>{problem.message}</p>
          {problem.fieldErrors.length > 0 ? (
            <ul className="mt-1 list-disc pl-5">
              {problem.fieldErrors.map((f) => (
                <li key={`${f.field}:${f.message}`}>
                  {f.field}: {f.message}
                </li>
              ))}
            </ul>
          ) : null}
          {problem.correlationId ? <p className="mt-1 text-xs">Reference: {problem.correlationId}</p> : null}
        </Alert>
      ) : null}

      <fieldset aria-describedby={errors.decision ? 'decision-error' : undefined}>
        <legend className="mb-2 text-sm font-medium text-charcoal-900">
          Decision <span aria-hidden="true" className="text-danger-600">*</span>
        </legend>
        <div className="flex flex-wrap gap-4">
          {OPTIONS.map((opt) => (
            <label key={opt} className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
              <input
                type="radio"
                name="decision"
                value={opt}
                checked={draft.decision === opt}
                onChange={() => setDraft({ ...draft, decision: opt })}
                className="size-4 accent-navy-900"
              />
              {DECISION_LABELS[opt]}
            </label>
          ))}
        </div>
        {errors.decision ? (
          <p id="decision-error" role="alert" className="mt-1 text-sm text-danger-700">
            {errors.decision}
          </p>
        ) : null}
      </fieldset>

      <Field
        label="Internal rationale"
        hint={`Required, 10 to ${RATIONALE_MAX} characters. Internal only: never shown to the recipient.`}
        required
        error={errors.rationale}
      >
        <Textarea
          rows={4}
          maxLength={RATIONALE_MAX}
          value={draft.rationale}
          onChange={(e) => setDraft({ ...draft, rationale: e.target.value })}
        />
      </Field>

      <Field
        label="Message to recipient"
        hint={
          needsMessage
            ? `Required for this decision. Shown to the recipient. Up to ${MESSAGE_MAX} characters.`
            : `Optional. Shown to the recipient. Up to ${MESSAGE_MAX} characters.`
        }
        required={needsMessage}
        error={errors.messageToRecipient}
      >
        <Textarea
          rows={3}
          maxLength={MESSAGE_MAX}
          value={draft.messageToRecipient}
          onChange={(e) => setDraft({ ...draft, messageToRecipient: e.target.value })}
        />
      </Field>

      <Button type="submit">Review decision</Button>

      <Dialog
        open={confirming}
        onClose={() => (pending ? undefined : setConfirming(false))}
        title="Confirm decision"
        description="Decisions are recorded in the audit log and cannot be undone from this console."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirming(false)} disabled={pending}>
              Go back
            </Button>
            <Button
              variant={draft.decision === 'REJECT' ? 'danger' : 'primary'}
              onClick={confirm}
              loading={pending}
            >
              {draft.decision ? `Confirm: ${DECISION_LABELS[draft.decision]}` : 'Confirm'}
            </Button>
          </>
        }
      >
        <p className="text-sm">
          You are about to <strong>{draft.decision ? DECISION_LABELS[draft.decision].toLowerCase() : ''}</strong> this
          request.
        </p>
      </Dialog>
    </form>
  );
}
