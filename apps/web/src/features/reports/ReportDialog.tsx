'use client';

import { Alert, Button, Dialog, Field, Textarea } from '@kyro/design-system';
import { ApiError, type ReportCreate } from '@kyro/api-client';
import { useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { useCreateReport } from './hooks';
import { REASONS, validateReport, type ReasonValue } from './reasons';

export function reportErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 429 || err.code === 'RATE_LIMITED') {
      return 'You have sent several reports in a short time. Please wait a few minutes and try again.';
    }
    if (err.status === 409) return 'You have already reported this request.';
    if (err.status === 404) return 'This request is no longer available to report.';
    if (err.status === 422) return err.message || 'Please check your report and try again.';
  }
  return errorMessage(err, 'We could not send your report. Please try again.');
}

export function ReportDialog({
  open,
  onClose,
  requestId,
}: {
  open: boolean;
  onClose: () => void;
  requestId: string;
}) {
  if (!open) return null;
  return <ReportDialogBody onClose={onClose} requestId={requestId} />;
}

function ReportDialogBody({ onClose, requestId }: { onClose: () => void; requestId: string }) {
  const mutation = useCreateReport();
  const [reason, setReason] = useState<ReasonValue | ''>('');
  const [details, setDetails] = useState('');
  const [errors, setErrors] = useState<{ reason?: string; details?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (mutation.isPending) return;
    const v = validateReport(reason, details);
    setErrors(v.errors);
    setFormError(null);
    if (!v.ok) return;
    const body: ReportCreate = { requestId, reason: v.reason, ...(v.details ? { details: v.details } : {}) };
    try {
      await mutation.mutateAsync(body);
      setDone(true);
    } catch (err) {
      setFormError(reportErrorMessage(err));
    }
  }

  if (done) {
    return (
      <Dialog
        open
        onClose={onClose}
        title="Report received"
        footer={<Button onClick={onClose}>Close</Button>}
      >
        <p role="status" className="text-charcoal-900">
          Thanks. Our team will review this request.
        </p>
      </Dialog>
    );
  }

  return (
    <Dialog
      open
      onClose={() => {
        if (!mutation.isPending) onClose();
      }}
      title="Report this request"
      description="Tell us what concerns you. Reports go to the KYRO team only. The student is not told who reported."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="report-form" loading={mutation.isPending}>
            Send report
          </Button>
        </>
      }
    >
      <form id="report-form" onSubmit={(e) => void onSubmit(e)} noValidate className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-2" aria-describedby={errors.reason ? 'report-reason-error' : undefined}>
          <legend className="mb-1 text-sm font-medium text-charcoal-900">
            Reason <span aria-hidden="true" className="text-danger-600">*</span>
          </legend>
          {REASONS.map((r) => (
            <label key={r.value} className="flex min-h-11 items-center gap-3 text-sm text-charcoal-900">
              <input
                type="radio"
                name="report-reason"
                value={r.value}
                checked={reason === r.value}
                onChange={() => setReason(r.value)}
                className="size-5 shrink-0"
              />
              {r.label}
            </label>
          ))}
          {errors.reason ? (
            <p id="report-reason-error" role="alert" className="text-sm font-medium text-danger-700">
              {errors.reason}
            </p>
          ) : null}
        </fieldset>
        <Field
          label="Details"
          required={reason === 'OTHER'}
          hint={reason === 'OTHER' ? 'Please describe the concern.' : 'Optional. Do not include passwords or payment details.'}
          error={errors.details}
        >
          {(p) => (
            <Textarea {...p} maxLength={2000} value={details} onChange={(e) => setDetails(e.target.value)} />
          )}
        </Field>
        <div aria-live="polite">
          {formError ? (
            <Alert tone="danger" role="alert">
              {formError}
            </Alert>
          ) : null}
        </div>
      </form>
    </Dialog>
  );
}
