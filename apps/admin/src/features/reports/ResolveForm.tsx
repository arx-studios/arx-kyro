'use client';

import { useState, type FormEvent } from 'react';
import type { ReportResolution, ReportResolutionInput } from '@kyro/api-client';
import { Alert, Button, Dialog, Field, Textarea } from '@kyro/design-system';
import { describeError, type FriendlyProblem } from '@/lib/problems';
import {
  NOTE_MAX,
  NOTE_MIN,
  RESOLUTION_LABELS,
  holdAllowed,
  validateResolution,
  type ResolutionDraft,
  type ResolutionErrors,
} from '@/lib/report';

const OPTIONS: ReportResolution[] = ['DISMISSED', 'ACTION_TAKEN'];

export interface ResolveFormProps {
  /** Resolves on success, rejects (ideally with ApiError) on failure. */
  onResolve: (input: ReportResolutionInput) => Promise<void>;
}

export function ResolveForm({ onResolve }: ResolveFormProps) {
  const [draft, setDraft] = useState<ResolutionDraft>({ resolution: '', note: '', holdRequest: false });
  const [errors, setErrors] = useState<ResolutionErrors>({});
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);

  const canHold = holdAllowed(draft.resolution);

  function onReview(e: FormEvent) {
    e.preventDefault();
    const found = validateResolution(draft);
    setErrors(found);
    setProblem(null);
    if (Object.keys(found).length === 0) setConfirming(true);
  }

  async function confirm() {
    if (!draft.resolution || pending) return;
    setPending(true);
    try {
      await onResolve({
        resolution: draft.resolution,
        note: draft.note.trim(),
        holdRequest: canHold && draft.holdRequest,
      });
    } catch (err) {
      setProblem(describeError(err));
    } finally {
      setPending(false);
      setConfirming(false);
    }
  }

  return (
    <form onSubmit={onReview} noValidate aria-label="Resolve report" className="space-y-4">
      {problem ? (
        <Alert tone={problem.tone} title={problem.title}>
          <p>{problem.message}</p>
          {problem.correlationId ? <p className="mt-1 text-xs">Reference: {problem.correlationId}</p> : null}
        </Alert>
      ) : null}

      <fieldset aria-describedby={errors.resolution ? 'resolution-error' : undefined}>
        <legend className="mb-2 text-sm font-medium text-charcoal-900">
          Resolution <span aria-hidden="true" className="text-danger-600">*</span>
        </legend>
        <div className="flex flex-wrap gap-4">
          {OPTIONS.map((opt) => (
            <label key={opt} className="flex min-h-11 cursor-pointer items-center gap-2 text-sm">
              <input
                type="radio"
                name="resolution"
                value={opt}
                checked={draft.resolution === opt}
                onChange={() =>
                  setDraft({ ...draft, resolution: opt, holdRequest: opt === 'ACTION_TAKEN' ? draft.holdRequest : false })
                }
                className="size-4 accent-navy-900"
              />
              {RESOLUTION_LABELS[opt]}
            </label>
          ))}
        </div>
        {errors.resolution ? (
          <p id="resolution-error" role="alert" className="mt-1 text-sm text-danger-700">
            {errors.resolution}
          </p>
        ) : null}
      </fieldset>

      <Field
        label="Resolution note"
        hint={`Required, ${NOTE_MIN} to ${NOTE_MAX} characters. Internal only.`}
        required
        error={errors.note}
      >
        <Textarea
          rows={4}
          maxLength={NOTE_MAX}
          value={draft.note}
          onChange={(e) => setDraft({ ...draft, note: e.target.value })}
        />
      </Field>

      <div>
        <label className="flex min-h-11 cursor-pointer items-center gap-2 text-sm aria-disabled:cursor-not-allowed">
          <input
            type="checkbox"
            checked={canHold && draft.holdRequest}
            disabled={!canHold}
            onChange={(e) => setDraft({ ...draft, holdRequest: e.target.checked })}
            aria-describedby="hold-hint"
            className="size-4 accent-navy-900"
          />
          Hide request from discovery
        </label>
        <p id="hold-hint" className="text-xs text-charcoal-700">
          Places a moderation hold. Available only when the resolution is &ldquo;Action taken&rdquo;. Only an admin
          can release a hold.
        </p>
      </div>

      <Button type="submit">Review resolution</Button>

      <Dialog
        open={confirming}
        onClose={() => (pending ? undefined : setConfirming(false))}
        title="Confirm resolution"
        description="Resolutions are recorded in the audit log and cannot be undone from this console."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirming(false)} disabled={pending}>
              Go back
            </Button>
            <Button onClick={confirm} loading={pending}>
              Confirm resolution
            </Button>
          </>
        }
      >
        <p className="text-sm">
          Resolve this report as <strong>{draft.resolution ? RESOLUTION_LABELS[draft.resolution] : ''}</strong>
          {canHold && draft.holdRequest ? ' and hide the request from discovery' : ''}.
        </p>
      </Dialog>
    </form>
  );
}
