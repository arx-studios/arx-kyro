'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useInfiniteQuery } from '@tanstack/react-query';
import { unwrap, type ReportSummary } from '@kyro/api-client';
import { Alert, Badge, Button, DateText, EmptyState, Spinner, cn, focusRing } from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError } from '@/lib/problems';
import { queryKeys, type ReportQueueName } from '@/lib/queryKeys';
import { REASON_LABELS } from '@/lib/report';
import { ReportStatusBadge } from './ReportStatusBadge';

export const REPORT_TABS: { id: ReportQueueName; label: string }[] = [
  { id: 'OPEN', label: 'Open' },
  { id: 'MINE', label: 'Mine' },
  { id: 'RESOLVED', label: 'Resolved' },
  { id: 'ALL', label: 'All' },
];

export function ReportsView() {
  const [queue, setQueue] = useState<ReportQueueName>('OPEN');

  const query = useInfiniteQuery({
    queryKey: queryKeys.reports.list(queue),
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.GET('/api/v1/admin/reports', {
          params: { query: { queue, limit: 25, ...(pageParam ? { cursor: pageParam } : {}) } },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const items = query.data?.pages.flatMap((p) => p.items) ?? [];

  function onTabKeyDown(e: React.KeyboardEvent, index: number) {
    let next = index;
    if (e.key === 'ArrowRight') next = (index + 1) % REPORT_TABS.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + REPORT_TABS.length) % REPORT_TABS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = REPORT_TABS.length - 1;
    else return;
    e.preventDefault();
    setQueue(REPORT_TABS[next]!.id);
    document.getElementById(`rtab-${REPORT_TABS[next]!.id}`)?.focus();
  }

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold text-navy-900">Reports</h1>

      <div role="tablist" aria-label="Report queue" className="mb-4 flex flex-wrap gap-1 border-b border-warm-200">
        {REPORT_TABS.map((tab, i) => {
          const selected = tab.id === queue;
          return (
            <button
              key={tab.id}
              id={`rtab-${tab.id}`}
              role="tab"
              type="button"
              aria-selected={selected}
              aria-controls="reports-panel"
              tabIndex={selected ? 0 : -1}
              onClick={() => setQueue(tab.id)}
              onKeyDown={(e) => onTabKeyDown(e, i)}
              className={cn(
                'min-h-11 rounded-t-md px-4 text-sm font-medium',
                focusRing,
                selected ? 'border-b-2 border-navy-900 text-navy-900' : 'text-charcoal-700 hover:bg-warm-100',
              )}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div id="reports-panel" role="tabpanel" aria-labelledby={`rtab-${queue}`} tabIndex={0} className={focusRing}>
        {query.isPending ? (
          <div className="flex justify-center p-10">
            <Spinner label="Loading reports" />
          </div>
        ) : query.isError ? (
          <ErrorAlert error={query.error} />
        ) : items.length === 0 ? (
          <EmptyState title="No reports here" description="Nothing matches this queue right now." />
        ) : (
          <>
            <ReportTable items={items} />
            {query.hasNextPage ? (
              <div className="mt-4 flex justify-center">
                <Button variant="secondary" onClick={() => query.fetchNextPage()} loading={query.isFetchingNextPage}>
                  Load more
                </Button>
              </div>
            ) : null}
          </>
        )}
      </div>
    </div>
  );
}

function ErrorAlert({ error }: { error: unknown }) {
  const p = describeError(error);
  return (
    <Alert tone={p.tone} title={p.title}>
      <p>{p.message}</p>
    </Alert>
  );
}

function ReportTable({ items }: { items: ReportSummary[] }) {
  const th = 'px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-charcoal-700';
  const td = 'px-3 py-3 text-sm align-top';
  return (
    <div className="overflow-x-auto rounded-lg border border-warm-200 bg-white">
      <table className="w-full min-w-[52rem] border-collapse">
        <caption className="sr-only">Reports</caption>
        <thead className="border-b border-warm-200 bg-warm-100">
          <tr>
            <th scope="col" className={th}>Request</th>
            <th scope="col" className={th}>Reason</th>
            <th scope="col" className={th}>Reporter</th>
            <th scope="col" className={th}>Reported</th>
            <th scope="col" className={th}>Assignee</th>
            <th scope="col" className={th}>Status</th>
          </tr>
        </thead>
        <tbody>
          {items.map((r) => (
            <tr key={r.id} className="border-b border-warm-100 last:border-0">
              <th scope="row" className={cn(td, 'text-left font-medium')}>
                <Link
                  href={`/reports/${r.id}`}
                  className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}
                >
                  {r.requestTitle}
                </Link>
              </th>
              <td className={td}>{REASON_LABELS[r.reason] ?? r.reason}</td>
              <td className={td}>{r.reporter.displayName}</td>
              <td className={td}>
                <DateText value={r.createdAt} withTime />
              </td>
              <td className={td}>{r.assignedTo?.displayName ?? <span className="text-charcoal-600">Unassigned</span>}</td>
              <td className={td}>
                <ReportStatusBadge status={r.status} />
                {r.requestOnHold ? <Badge tone="warning" className="ml-2">On hold</Badge> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
