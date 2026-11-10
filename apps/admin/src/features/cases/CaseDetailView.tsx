'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, unwrap, type DecisionInput, type VerificationCaseDetail } from '@kyro/api-client';
import {
  Alert,
  Badge,
  Button,
  Card,
  CaseStatusBadge,
  DateText,
  MoneyText,
  RequestStatusBadge,
  Spinner,
  focusRing,
} from '@kyro/design-system';
import { api } from '@/lib/api';
import { DECISION_LABELS } from '@/lib/decision';
import { describeError } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';
import { DecisionForm } from './DecisionForm';
import { DocumentsPanel } from './DocumentsPanel';

const EXPENSE_LABELS: Record<string, string> = {
  TUITION: 'Tuition',
  EXAMINATION_FEE: 'Examination fee',
  COURSE_MATERIALS: 'Course materials',
  OTHER_ACADEMIC: 'Other academic',
};

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-2 sm:grid-cols-[12rem_1fr]">
      <dt className="text-sm font-medium text-charcoal-700">{label}</dt>
      <dd className="min-w-0 break-words text-sm">{children ?? '—'}</dd>
    </div>
  );
}

export function CaseDetailView({ caseId }: { caseId: string }) {
  const queryClient = useQueryClient();
  const key = queryKeys.cases.detail(caseId);

  const query = useQuery({
    queryKey: key,
    queryFn: async () =>
      unwrap(await api.GET('/api/v1/admin/verification/cases/{caseId}', { params: { path: { caseId } } })),
  });

  const apply = (detail: VerificationCaseDetail) => {
    queryClient.setQueryData(key, detail);
    void queryClient.invalidateQueries({ queryKey: queryKeys.cases.all });
  };

  const claim = useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST('/api/v1/admin/verification/cases/{caseId}/assign', { params: { path: { caseId } } }),
      ),
    onSuccess: apply,
    onError: () => void queryClient.invalidateQueries({ queryKey: key }),
  });

  async function decide(input: DecisionInput) {
    try {
      const detail = unwrap(
        await api.POST('/api/v1/admin/verification/cases/{caseId}/decision', {
          params: { path: { caseId } },
          body: input,
        }),
      );
      apply(detail);
    } catch (err) {
      // Refresh so canDecide / assignee reflect what the server now says, then let the form show it.
      void queryClient.invalidateQueries({ queryKey: key });
      throw err;
    }
  }

  if (query.isPending) {
    return (
      <div className="flex justify-center p-10">
        <Spinner label="Loading case" />
      </div>
    );
  }
  if (query.isError) {
    const p = describeError(query.error);
    return (
      <div className="space-y-4">
        <BackLink />
        <Alert tone={p.tone} title={query.error instanceof ApiError && query.error.status === 404 ? 'Case not found' : p.title}>
          <p>{p.message}</p>
        </Alert>
      </div>
    );
  }

  const c = query.data;
  const r = c.request;
  const claimProblem = claim.error ? describeError(claim.error) : null;

  return (
    <div className="space-y-8">
      <BackLink />

      <header className="space-y-2">
        <h1 className="text-2xl font-semibold text-navy-900">{r.title}</h1>
        <div className="flex flex-wrap items-center gap-2 text-sm text-charcoal-700">
          <CaseStatusBadge status={c.status} />
          <RequestStatusBadge status={r.status} />
          <span>Submission #{c.submissionNumber}</span>
          <span>
            Opened <DateText value={c.openedAt} withTime />
          </span>
          <span>Assignee: {c.assignedTo?.displayName ?? 'unassigned'}</span>
          {c.outcome ? <Badge tone="info">Outcome: {c.outcome.replace('_', ' ').toLowerCase()}</Badge> : null}
        </div>
      </header>

      {c.status === 'OPEN' ? (
        <section aria-label="Claim" className="space-y-3">
          {claimProblem ? (
            <Alert tone={claimProblem.tone} title={claimProblem.title}>
              <p>{claimProblem.message}</p>
            </Alert>
          ) : null}
          <Button onClick={() => claim.mutate()} loading={claim.isPending}>
            Claim case
          </Button>
          <p className="text-sm text-charcoal-700">Claiming assigns this case to you and moves the request to In review.</p>
        </section>
      ) : null}

      <section aria-labelledby="request-heading" className="space-y-2">
        <h2 id="request-heading" className="text-lg font-semibold text-navy-900">
          Request details
        </h2>
        <Card className="p-4">
          <dl className="divide-y divide-warm-100">
            <Row label="Amount">
              <MoneyText amountMinor={r.amount.amountMinor} currency={r.amount.currency} />
            </Row>
            <Row label="Expense type">{EXPENSE_LABELS[r.expenseType] ?? r.expenseType}</Row>
            <Row label="Institution">
              {[r.institutionName, r.institutionCity, r.institutionCountry].filter(Boolean).join(', ') || '—'}
            </Row>
            <Row label="Course">{r.courseName}</Row>
            <Row label="Due date">{r.dueDate ? <DateText value={r.dueDate} /> : '—'}</Row>
            <Row label="Fee reference (private)">{r.feeReference}</Row>
            <Row label="Story">
              <span className="whitespace-pre-wrap">{r.story}</span>
            </Row>
            <Row label="Public display name">{r.publicDisplayName}</Row>
            <Row label="Recipient name">{r.recipient.displayName}</Row>
            <Row label="Recipient email (private)">
              {r.recipient.email}{' '}
              <Badge tone={r.recipient.emailVerified ? 'success' : 'warning'}>
                {r.recipient.emailVerified ? 'Verified' : 'Unverified'}
              </Badge>
            </Row>
            <Row label="Member since">
              <DateText value={r.recipient.memberSince} />
            </Row>
          </dl>
        </Card>
      </section>

      <DocumentsPanel caseId={caseId} documents={r.documents} />

      <section aria-labelledby="dup-heading" className="space-y-2">
        <h2 id="dup-heading" className="text-lg font-semibold text-navy-900">
          Duplicate indicators
        </h2>
        {c.duplicateIndicators.length === 0 ? (
          <p className="text-sm text-charcoal-700">No duplicate indicators were found.</p>
        ) : (
          <ul className="space-y-2">
            {c.duplicateIndicators.map((d) => (
              <li key={`${d.kind}:${d.otherRequestId}`}>
                <Alert tone="warning" title={d.kind === 'DOCUMENT_HASH_MATCH' ? 'Identical document found' : 'Fee reference reused'}>
                  <p>{d.detail}</p>
                  <p className="mt-1 text-xs">
                    Other request <span className="font-mono">{d.otherRequestId}</span> ·{' '}
                    <RequestStatusBadge status={d.otherRequestStatus} />
                  </p>
                </Alert>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="history-heading" className="space-y-2">
        <h2 id="history-heading" className="text-lg font-semibold text-navy-900">
          Status history
        </h2>
        <ol className="space-y-1 text-sm">
          {r.statusHistory.map((h, i) => (
            <li key={`${h.changedAt}:${i}`}>
              <DateText value={h.changedAt} withTime /> · {h.fromStatus ? `${h.fromStatus} → ` : ''}
              {h.toStatus}
              {h.note ? <span className="text-charcoal-700"> — {h.note}</span> : null}
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="decisions-heading" className="space-y-2">
        <h2 id="decisions-heading" className="text-lg font-semibold text-navy-900">
          Prior decisions
        </h2>
        {c.decisions.length === 0 ? (
          <p className="text-sm text-charcoal-700">No decisions recorded yet.</p>
        ) : (
          <ul className="space-y-2">
            {c.decisions.map((d) => (
              <li key={d.id}>
                <Card className="p-4 text-sm">
                  <p className="font-medium">
                    {DECISION_LABELS[d.decision]} by {d.decidedBy.displayName} ·{' '}
                    <DateText value={d.createdAt} withTime />
                  </p>
                  <p className="mt-1">
                    <span className="font-medium">Rationale (internal): </span>
                    <span className="whitespace-pre-wrap">{d.rationale}</span>
                  </p>
                  {d.messageToRecipient ? (
                    <p className="mt-1">
                      <span className="font-medium">Message to recipient: </span>
                      <span className="whitespace-pre-wrap">{d.messageToRecipient}</span>
                    </p>
                  ) : null}
                </Card>
              </li>
            ))}
          </ul>
        )}
      </section>

      {c.canDecide ? (
        <section aria-labelledby="decide-heading" className="space-y-3">
          <h2 id="decide-heading" className="text-lg font-semibold text-navy-900">
            Decision
          </h2>
          <Card className="p-4">
            <DecisionForm onDecide={decide} />
          </Card>
        </section>
      ) : c.status === 'ASSIGNED' ? (
        <Alert tone="info" title="Decision not available">
          <p>Only the assigned reviewer can decide this case, and reviewers cannot decide their own requests.</p>
        </Alert>
      ) : null}
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/queue" className={`inline-flex min-h-11 items-center rounded-sm text-sm text-navy-800 underline underline-offset-2 ${focusRing}`}>
      ← Back to queue
    </Link>
  );
}
