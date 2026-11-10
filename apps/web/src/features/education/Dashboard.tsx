'use client';

import { Card, DateText, EmptyState, MoneyText, RequestStatusBadge, Spinner, Alert } from '@kyro/design-system';
import Link from 'next/link';
import { errorMessage } from '@/lib/errors';
import { useMyRequests } from './hooks';

const newLink =
  'inline-flex min-h-11 items-center justify-center rounded-md bg-navy-900 px-5 text-base font-medium text-warm-50 hover:bg-navy-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy-700';

export function Dashboard() {
  const q = useMyRequests();

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold text-navy-900">My requests</h1>
        <Link href="/recipient/requests/new" className={newLink}>
          New request
        </Link>
      </div>

      {q.isPending ? <Spinner label="Loading your requests" /> : null}
      {q.isError ? <Alert tone="danger">{errorMessage(q.error)}</Alert> : null}
      {q.data && q.data.items.length === 0 ? (
        <EmptyState
          title="No requests yet"
          description="Start a request for a tuition or exam fee bill. You can save it as a draft and finish later."
          action={
            <Link href="/recipient/requests/new" className={newLink}>
              Create your first request
            </Link>
          }
        />
      ) : null}
      {q.data && q.data.items.length > 0 ? (
        <ul className="flex flex-col gap-3">
          {q.data.items.map((r) => (
            <li key={r.id}>
              <Card className="p-4">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <h2 className="text-lg font-semibold text-charcoal-900">
                    <Link href={`/recipient/requests/${r.id}`} className="underline">
                      {r.title}
                    </Link>
                  </h2>
                  <RequestStatusBadge status={r.status} />
                </div>
                <p className="mt-2 text-charcoal-700">
                  <MoneyText amountMinor={r.amount.amountMinor} currency={r.amount.currency} /> · {r.documentCount}{' '}
                  {r.documentCount === 1 ? 'document' : 'documents'}
                  {r.dueDate ? (
                    <>
                      {' '}
                      · due <DateText value={r.dueDate} />
                    </>
                  ) : null}
                </p>
                <p className="mt-1 text-sm text-charcoal-600">
                  Updated <DateText value={r.updatedAt} withTime />
                </p>
              </Card>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
