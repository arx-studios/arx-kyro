'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, unwrap, type ReportDetail, type ReportResolutionInput } from '@kyro/api-client';
import { Alert, Badge, Button, DateText, RequestStatusBadge, Spinner, cn, focusRing } from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';
import { REASON_LABELS, RESOLUTION_LABELS } from '@/lib/report';
import { useMe } from '@/features/auth/useMe';
import { HoldPanel } from './HoldPanel';
import { ReportStatusBadge } from './ReportStatusBadge';
import { ResolveForm } from './ResolveForm';

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-1 py-2 sm:grid-cols-[12rem_1fr]">
      <dt className="text-sm font-medium text-charcoal-700">{label}</dt>
      <dd className="min-w-0 break-words text-sm">{children ?? '—'}</dd>
    </div>
  );
}

function BackLink() {
  return (
    <Link href="/reports" className={cn('inline-block rounded-sm text-sm text-navy-800 underline underline-offset-2', focusRing)}>
      Back to reports
    </Link>
  );
}

export function ReportDetailView({ reportId }: { reportId: string }) {
  const queryClient = useQueryClient();
  const { user } = useMe();
  const isAdmin = !!user?.roles.includes('ADMIN');
  const key = queryKeys.reports.detail(reportId);

  const query = useQuery({
    queryKey: key,
    queryFn: async () => unwrap(await api.GET('/api/v1/admin/reports/{reportId}', { params: { path: { reportId } } })),
  });

  const refreshLists = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.reports.all });
    void queryClient.invalidateQueries({ queryKey: queryKeys.dashboard });
  };
  const apply = (detail: ReportDetail) => {
    queryClient.setQueryData(key, detail);
    refreshLists();
  };

  const claim = useMutation({
    mutationFn: async () =>
      unwrap(await api.POST('/api/v1/admin/reports/{reportId}/assign', { params: { path: { reportId } } })),
    onSuccess: apply,
    onError: () => void queryClient.invalidateQueries({ queryKey: key }),
  });

  async function resolve(input: ReportResolutionInput) {
    try {
      apply(
        unwrap(
          await api.POST('/api/v1/admin/reports/{reportId}/resolve', { params: { path: { reportId } }, body: input }),
        ),
      );
    } catch (err) {
      void queryClient.invalidateQueries({ queryKey: key });
      throw err;
    }
  }

  async function releaseHold(requestId: string) {
    try {
      unwrap(
        await api.POST('/api/v1/admin/requests/{requestId}/hold/release', { params: { path: { requestId } } }),
      );
    } finally {
      await queryClient.invalidateQueries({ queryKey: key });
      refreshLists();
    }
  }

  if (query.isPending) {
    return (
      <div className="flex justify-center p-10">
        <Spinner label="Loading report" />
      </div>
    );
  }
  if (query.isError) {
    const p = describeError(query.error);
    return (
      <div className="space-y-4">
        <BackLink />
        <Alert tone={p.tone} title={query.error instanceof ApiError && query.error.status === 404 ? 'Report not found' : p.title}>
          <p>{p.message}</p>
        </Alert>
      </div>
    );
  }

  const r = query.data;
  const claimProblem = claim.error ? describeError(claim.error) : null;

  return (
    <div className="space-y-8">
      <BackLink />

      <header className="space-y-2">
        <h1 className="text-2xl font-semibold text-navy-900">{r.requestTitle}</h1>
        <div className="flex flex-wrap items-center gap-2 text-sm text-charcoal-700">
          <ReportStatusBadge status={r.status} />
          <RequestStatusBadge status={r.requestStatus} />
          {r.requestOnHold ? <Badge tone="warning">On hold</Badge> : null}
          <span>Assignee: {r.assignedTo?.displayName ?? 'unassigned'}</span>
        </div>
      </header>

      {r.status === 'OPEN' ? (
        <section aria-label="Claim" className="space-y-3">
          {claimProblem ? (
            <Alert tone={claimProblem.tone} title={claimProblem.title}>
              <p>{claimProblem.message}</p>
            </Alert>
          ) : null}
          <Button onClick={() => claim.mutate()} loading={claim.isPending}>
            Claim report
          </Button>
          <p className="text-sm text-charcoal-700">Claiming assigns this report to you so you can resolve it.</p>
        </section>
      ) : null}

      <section aria-labelledby="report-heading" className="space-y-2">
        <h2 id="report-heading" className="text-lg font-semibold text-navy-900">
          Report
        </h2>
        <dl className="divide-y divide-warm-100 rounded-lg border border-warm-200 bg-white px-4">
          <Row label="Reason">{REASON_LABELS[r.reason] ?? r.reason}</Row>
          <Row label="Details">{r.details ? <span className="whitespace-pre-wrap">{r.details}</span> : 'None provided'}</Row>
          <Row label="Reporter">{r.reporter.displayName}</Row>
          <Row label="Reported">
            <DateText value={r.createdAt} withTime />
          </Row>
          <Row label="Request">
            {r.latestCaseId ? (
              <Link
                href={`/cases/${r.latestCaseId}`}
                className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}
              >
                Open verification case
              </Link>
            ) : (
              'No verification case yet'
            )}
          </Row>
          {r.status === 'RESOLVED' ? (
            <>
              <Row label="Resolution">{r.resolution ? RESOLUTION_LABELS[r.resolution] : '—'}</Row>
              <Row label="Resolution note">
                {r.resolutionNote ? <span className="whitespace-pre-wrap">{r.resolutionNote}</span> : '—'}
              </Row>
              <Row label="Resolved by">{r.resolvedBy?.displayName ?? '—'}</Row>
              <Row label="Resolved">{r.resolvedAt ? <DateText value={r.resolvedAt} withTime /> : '—'}</Row>
            </>
          ) : null}
        </dl>
      </section>

      {r.hold ? <HoldPanel hold={r.hold} isAdmin={isAdmin} onRelease={() => releaseHold(r.requestId)} /> : null}

      {r.otherReports.length > 0 ? (
        <section aria-labelledby="other-heading" className="space-y-2">
          <h2 id="other-heading" className="text-lg font-semibold text-navy-900">
            Other reports on this request
          </h2>
          <ul className="divide-y divide-warm-100 rounded-lg border border-warm-200 bg-white">
            {r.otherReports.map((o) => (
              <li key={o.id} className="flex flex-wrap items-center gap-3 px-4 py-3 text-sm">
                <Link
                  href={`/reports/${o.id}`}
                  className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}
                >
                  {REASON_LABELS[o.reason] ?? o.reason}
                </Link>
                <ReportStatusBadge status={o.status} />
                <span className="text-charcoal-700">
                  <DateText value={o.createdAt} withTime />
                </span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {r.canResolve ? (
        <section aria-labelledby="resolve-heading" className="space-y-3">
          <h2 id="resolve-heading" className="text-lg font-semibold text-navy-900">
            Resolve report
          </h2>
          <ResolveForm onResolve={resolve} />
        </section>
      ) : null}
    </div>
  );
}
