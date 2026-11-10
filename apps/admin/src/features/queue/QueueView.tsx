'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useInfiniteQuery } from '@tanstack/react-query';
import { unwrap, type VerificationCaseSummary } from '@kyro/api-client';
import {
  Alert,
  Button,
  CaseStatusBadge,
  DateText,
  EmptyState,
  MoneyText,
  Spinner,
  cn,
  focusRing,
} from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError } from '@/lib/problems';
import { queryKeys, type QueueName } from '@/lib/queryKeys';

export const QUEUE_TABS: { id: QueueName; label: string }[] = [
  { id: 'UNASSIGNED', label: 'Unassigned' },
  { id: 'MINE', label: 'Mine' },
  { id: 'CLOSED', label: 'Closed' },
  { id: 'ALL', label: 'All' },
];

export function QueueView() {
  const [queue, setQueue] = useState<QueueName>('UNASSIGNED');

  const query = useInfiniteQuery({
    queryKey: queryKeys.cases.list(queue),
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.GET('/api/v1/admin/verification/cases', {
          params: { query: { queue, limit: 25, ...(pageParam ? { cursor: pageParam } : {}) } },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  const items = query.data?.pages.flatMap((p) => p.items) ?? [];

  function onTabKeyDown(e: React.KeyboardEvent, index: number) {
    let next = index;
    if (e.key === 'ArrowRight') next = (index + 1) % QUEUE_TABS.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + QUEUE_TABS.length) % QUEUE_TABS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = QUEUE_TABS.length - 1;
    else return;
    e.preventDefault();
    setQueue(QUEUE_TABS[next]!.id);
    document.getElementById(`tab-${QUEUE_TABS[next]!.id}`)?.focus();
  }

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold text-navy-900">Verification queue</h1>

      <div role="tablist" aria-label="Queue" className="mb-4 flex flex-wrap gap-1 border-b border-warm-200">
        {QUEUE_TABS.map((tab, i) => {
          const selected = tab.id === queue;
          return (
            <button
              key={tab.id}
              id={`tab-${tab.id}`}
              role="tab"
              type="button"
              aria-selected={selected}
              aria-controls="queue-panel"
              tabIndex={selected ? 0 : -1}
              onClick={() => setQueue(tab.id)}
              onKeyDown={(e) => onTabKeyDown(e, i)}
              className={cn(
                'min-h-11 rounded-t-md px-4 text-sm font-medium',
                focusRing,
                selected
                  ? 'border-b-2 border-navy-900 text-navy-900'
                  : 'text-charcoal-700 hover:bg-warm-100',
              )}
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      <div id="queue-panel" role="tabpanel" aria-labelledby={`tab-${queue}`} tabIndex={0} className={focusRing}>
        {query.isPending ? (
          <div className="flex justify-center p-10">
            <Spinner label="Loading cases" />
          </div>
        ) : query.isError ? (
          <ErrorAlert error={query.error} />
        ) : items.length === 0 ? (
          <EmptyState title="No cases here" description="Nothing matches this queue right now." />
        ) : (
          <>
            <CaseTable items={items} />
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

function CaseTable({ items }: { items: VerificationCaseSummary[] }) {
  const th = 'px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-charcoal-700';
  const td = 'px-3 py-3 text-sm align-top';
  return (
    <div className="overflow-x-auto rounded-lg border border-warm-200 bg-white">
      <table className="w-full min-w-[56rem] border-collapse">
        <caption className="sr-only">Verification cases</caption>
        <thead className="border-b border-warm-200 bg-warm-100">
          <tr>
            <th scope="col" className={th}>Request</th>
            <th scope="col" className={th}>Recipient</th>
            <th scope="col" className={th}>Amount</th>
            <th scope="col" className={th}>Submission</th>
            <th scope="col" className={th}>Opened</th>
            <th scope="col" className={th}>Assignee</th>
            <th scope="col" className={th}>Status</th>
          </tr>
        </thead>
        <tbody>
          {items.map((c) => (
            <tr key={c.id} className="border-b border-warm-100 last:border-0">
              <th scope="row" className={cn(td, 'text-left font-medium')}>
                <Link
                  href={`/cases/${c.id}`}
                  className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}
                >
                  {c.requestTitle}
                </Link>
              </th>
              <td className={td}>{c.recipientPublicName}</td>
              <td className={td}>
                <MoneyText amountMinor={c.amount.amountMinor} currency={c.amount.currency} />
              </td>
              <td className={td}>#{c.submissionNumber}</td>
              <td className={td}>
                <DateText value={c.openedAt} withTime />
              </td>
              <td className={td}>{c.assignedTo?.displayName ?? <span className="text-charcoal-600">Unassigned</span>}</td>
              <td className={td}>
                <CaseStatusBadge status={c.status} />
                {c.outcome ? <span className="ml-2 text-xs text-charcoal-700">{c.outcome.replace('_', ' ').toLowerCase()}</span> : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
