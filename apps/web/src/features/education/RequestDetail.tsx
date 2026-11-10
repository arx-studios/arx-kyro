'use client';

import {
  Alert,
  Button,
  Card,
  DateText,
  Dialog,
  EmptyState,
  MoneyText,
  RequestStatusBadge,
  Spinner,
} from '@kyro/design-system';
import { ApiError, type EducationRequest, type FieldError, type RequestDocument } from '@kyro/api-client';
import Link from 'next/link';
import { useState } from 'react';
import { errorMessage, problemFieldErrors } from '@/lib/errors';
import { DocumentUploader } from './DocumentUploader';
import {
  useDeleteDocument,
  useRequest,
  useSubmitRequest,
  useUpdateRequest,
  useWithdrawRequest,
} from './hooks';
import { RecipientFundingPanel } from './FundingPanel';
import { RequestForm } from './RequestForm';
import { DOCUMENT_TYPES } from './upload';
import { EXPENSE_TYPES, toUpdatePayload } from './schemas';

const WITHDRAWABLE = ['DRAFT', 'SUBMITTED', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED'];

const FIELD_LABELS: Record<string, string> = {
  title: 'Title',
  story: 'Story (at least 50 characters)',
  expenseType: 'Type of expense',
  institutionName: 'Institution name',
  institutionCity: 'Institution city',
  courseName: 'Course',
  feeReference: 'Fee reference',
  amount: 'Amount',
  dueDate: 'Payment due date',
  documents: 'Supporting document',
};

function labelFor(fe: FieldError): string {
  const label = FIELD_LABELS[fe.field.split('.')[0] ?? ''] ?? fe.field;
  return `${label}: ${fe.message}`;
}

export function DocumentList({
  documents,
  editable,
  requestId,
}: {
  documents: RequestDocument[];
  editable: boolean;
  requestId: string;
}) {
  const del = useDeleteDocument(requestId);
  const visible = documents.filter((d) => d.status !== 'DELETED');
  if (visible.length === 0) {
    return <p className="text-charcoal-700">No documents uploaded yet.</p>;
  }
  return (
    <>
      {del.isError ? <Alert tone="danger">{errorMessage(del.error)}</Alert> : null}
      <ul className="divide-y divide-warm-200 rounded-lg border border-warm-300 bg-white">
        {visible.map((d) => (
          <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 p-3">
            <div>
              <p className="break-all font-medium text-charcoal-900">{d.originalFileName}</p>
              <p className="text-sm text-charcoal-700">
                {DOCUMENT_TYPES.find((t) => t.value === d.documentType)?.label ?? d.documentType} ·{' '}
                {Math.max(1, Math.round(d.sizeBytes / 1024))} KB ·{' '}
                {d.status === 'UPLOADED' ? 'Uploaded' : d.status === 'PENDING_UPLOAD' ? 'Upload not finished' : d.status === 'REJECTED' ? 'Rejected' : d.status}
              </p>
            </div>
            {editable ? (
              <Button
                variant="ghost"
                aria-label={`Delete ${d.originalFileName}`}
                loading={del.isPending && del.variables === d.id}
                onClick={() => del.mutate(d.id)}
              >
                Delete
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
    </>
  );
}

export function StatusTimeline({ request }: { request: EducationRequest }) {
  const entries = [...request.statusHistory].sort((a, b) => a.changedAt.localeCompare(b.changedAt));
  return (
    <ol className="flex flex-col gap-3 border-l-2 border-warm-300 pl-4" aria-label="Status history">
      {entries.map((e, i) => (
        <li key={`${e.changedAt}-${i}`}>
          <div className="flex flex-wrap items-center gap-2">
            <RequestStatusBadge status={e.toStatus} />
            <DateText value={e.changedAt} withTime className="text-sm text-charcoal-700" />
          </div>
          {e.note ? <p className="mt-1 text-charcoal-800">{e.note}</p> : null}
        </li>
      ))}
    </ol>
  );
}

function ReadOnlySummary({ request }: { request: EducationRequest }) {
  const rows: [string, React.ReactNode][] = [
    ['Type of expense', EXPENSE_TYPES.find((t) => t.value === request.expenseType)?.label ?? request.expenseType],
    ['Amount', <MoneyText key="a" amountMinor={request.amount.amountMinor} currency={request.amount.currency} />],
    ['Institution', [request.institutionName, request.institutionCity].filter(Boolean).join(', ') || 'Not provided'],
    ['Course', request.courseName || 'Not provided'],
    ['Fee reference', request.feeReference || 'Not provided'],
    ['Payment due date', request.dueDate ? <DateText key="d" value={request.dueDate} /> : 'Not provided'],
  ];
  return (
    <Card className="p-4">
      <dl className="grid gap-3 sm:grid-cols-2">
        {rows.map(([k, v]) => (
          <div key={k}>
            <dt className="text-sm text-charcoal-700">{k}</dt>
            <dd className="font-medium text-charcoal-900">{v}</dd>
          </div>
        ))}
      </dl>
      {request.story ? <p className="mt-4 whitespace-pre-line text-charcoal-800">{request.story}</p> : null}
    </Card>
  );
}

export function RequestDetail({ id }: { id: string }) {
  const q = useRequest(id);
  const update = useUpdateRequest(id);
  const submit = useSubmitRequest(id);
  const withdraw = useWithdrawRequest(id);
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);
  const [incomplete, setIncomplete] = useState<FieldError[] | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  if (q.isPending) {
    return (
      <div className="flex justify-center p-12">
        <Spinner label="Loading request" />
      </div>
    );
  }
  if (q.isError) {
    const notFound = q.error instanceof ApiError && q.error.status === 404;
    return (
      <EmptyState
        title={notFound ? 'Request not found' : 'We could not load this request'}
        description={notFound ? 'It may have been removed, or it belongs to another account.' : errorMessage(q.error)}
        action={
          <Link href="/recipient/dashboard" className="underline">
            Back to my requests
          </Link>
        }
      />
    );
  }

  const r = q.data;

  async function onSubmitRequest() {
    setIncomplete(null);
    setSubmitError(null);
    try {
      await submit.mutateAsync();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'REQUEST_INCOMPLETE') {
        setIncomplete(problemFieldErrors(err));
      } else {
        setSubmitError(errorMessage(err));
      }
    }
  }

  async function onWithdraw() {
    try {
      await withdraw.mutateAsync();
      setConfirmWithdraw(false);
    } catch {
      // error shown inside the dialog
    }
  }

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <Link href="/recipient/dashboard" className="inline-flex min-h-11 items-center text-sm underline">
          Back to my requests
        </Link>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-3xl font-bold text-navy-900">{r.title}</h1>
          <RequestStatusBadge status={r.status} />
        </div>
        <p className="text-charcoal-700">
          Shown to donors as <strong>{r.publicDisplayName}</strong>
        </p>
      </header>

      {r.reviewerFeedback ? (
        <Alert tone="warning" title="Message from the reviewer">
          {r.reviewerFeedback}
        </Alert>
      ) : null}
      {r.status === 'APPROVED' ? (
        <Alert tone="success" title="Verified">
          Your request has been verified and is visible to donors. It can no longer be edited.
        </Alert>
      ) : null}

      <RecipientFundingPanel requestId={r.id} approved={r.status === 'APPROVED'} />

      <section aria-labelledby="details-h" className="flex flex-col gap-4">
        <h2 id="details-h" className="text-xl font-semibold text-charcoal-900">
          Details
        </h2>
        {r.editable ? (
          <RequestForm
            key={r.version}
            initial={r}
            submitLabel="Save changes"
            onSubmit={(values) => update.mutateAsync(toUpdatePayload(values, r.version, r))}
          />
        ) : (
          <ReadOnlySummary request={r} />
        )}
      </section>

      <section aria-labelledby="docs-h" className="flex flex-col gap-4">
        <h2 id="docs-h" className="text-xl font-semibold text-charcoal-900">
          Supporting documents
        </h2>
        <DocumentList documents={r.documents} editable={r.editable} requestId={r.id} />
        {r.editable ? <DocumentUploader requestId={r.id} /> : null}
      </section>

      {r.editable ? (
        <section aria-labelledby="submit-h" className="flex flex-col gap-3">
          <h2 id="submit-h" className="text-xl font-semibold text-charcoal-900">
            Submit for verification
          </h2>
          <p className="text-charcoal-700">
            Save your changes first. A KYRO reviewer will check your documents; you cannot edit the request while it is
            in review.
          </p>
          {incomplete ? (
            <Alert tone="danger" title="Your request is not complete yet">
              <ul className="list-disc pl-5">
                {incomplete.length > 0 ? (
                  incomplete.map((fe, i) => <li key={`${fe.field}-${i}`}>{labelFor(fe)}</li>)
                ) : (
                  <li>Some required information is missing.</li>
                )}
              </ul>
            </Alert>
          ) : null}
          {submitError ? <Alert tone="danger">{submitError}</Alert> : null}
          <div>
            <Button size="lg" onClick={onSubmitRequest} loading={submit.isPending}>
              Submit for verification
            </Button>
          </div>
        </section>
      ) : null}

      <section aria-labelledby="history-h" className="flex flex-col gap-3">
        <h2 id="history-h" className="text-xl font-semibold text-charcoal-900">
          Status
        </h2>
        <StatusTimeline request={r} />
      </section>

      {WITHDRAWABLE.includes(r.status) ? (
        <section aria-labelledby="withdraw-h" className="flex flex-col gap-3">
          <h2 id="withdraw-h" className="text-xl font-semibold text-charcoal-900">
            Withdraw this request
          </h2>
          <p className="text-charcoal-700">Withdrawing removes the request from review and from donors. This cannot be undone.</p>
          <div>
            <Button variant="danger" onClick={() => setConfirmWithdraw(true)}>
              Withdraw request
            </Button>
          </div>
        </section>
      ) : null}

      <Dialog
        open={confirmWithdraw}
        onClose={() => setConfirmWithdraw(false)}
        title="Withdraw this request?"
        description="It will be removed from review and from donors. This cannot be undone."
        footer={
          <>
            <Button variant="secondary" onClick={() => setConfirmWithdraw(false)}>
              Keep request
            </Button>
            <Button variant="danger" loading={withdraw.isPending} onClick={onWithdraw}>
              Yes, withdraw
            </Button>
          </>
        }
      >
        {withdraw.isError ? <Alert tone="danger">{errorMessage(withdraw.error)}</Alert> : null}
      </Dialog>
    </div>
  );
}
