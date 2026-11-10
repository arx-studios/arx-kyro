'use client';

import { Alert, Badge, Button, Card, DateText, EmptyState, MoneyText, Spinner, type BadgeTone } from '@kyro/design-system';
import type { Pledge, PledgeStatus } from '@kyro/api-client';
import Link from 'next/link';
import { useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { CancelPledgeDialog } from './CancelPledgeDialog';
import { useMyPledges } from './hooks';

const STATUS_LABEL: Record<PledgeStatus, string> = {
  ACTIVE: 'Active',
  CANCELLED: 'Cancelled',
  LAPSED: 'Lapsed',
};
const STATUS_TONE: Record<PledgeStatus, BadgeTone> = { ACTIVE: 'info', CANCELLED: 'neutral', LAPSED: 'warning' };

export function cancelReasonText(pledge: Pledge): string | null {
  if (pledge.status === 'ACTIVE') return null;
  if (pledge.cancelReason === 'REQUEST_WITHDRAWN') return 'Cancelled because the request was withdrawn.';
  if (pledge.cancelReason === 'DONOR_CANCELLED') return 'You cancelled this pledge.';
  if (pledge.status === 'LAPSED') return 'This pledge lapsed.';
  return 'This pledge was cancelled.';
}

export function MyPledges() {
  const q = useMyPledges();
  const [toCancel, setToCancel] = useState<Pledge | null>(null);
  const [message, setMessage] = useState('');

  if (q.isPending) {
    return (
      <div className="flex justify-center p-12">
        <Spinner label="Loading your pledges" />
      </div>
    );
  }
  if (q.isError) {
    return (
      <Alert tone="danger" title="We could not load your pledges">
        {errorMessage(q.error)}{' '}
        <button type="button" className="underline" onClick={() => void q.refetch()}>
          Try again
        </button>
      </Alert>
    );
  }

  const items = q.data.pages.flatMap((p) => p.items);

  return (
    <div className="flex flex-col gap-4">
      <p className="text-charcoal-700">
        A pledge is not a payment. These are non-binding statements of intent, and no money has been collected.
      </p>
      <div role="status" aria-live="polite" className="sr-only">
        {message}
      </div>
      {items.length === 0 ? (
        <EmptyState
          title="No pledges yet"
          description="When you pledge to a request, it appears here."
          action={
            <Link href="/discover" className="underline">
              Browse verified requests
            </Link>
          }
        />
      ) : (
        <ul className="flex flex-col gap-4">
          {items.map(({ pledge, request }) => {
            const reason = cancelReasonText(pledge);
            return (
              <li key={pledge.id}>
                <Card className="flex flex-col gap-2 p-5">
                  <div className="flex flex-wrap items-center gap-2 text-sm text-charcoal-700">
                    <Badge tone={STATUS_TONE[pledge.status]}>{STATUS_LABEL[pledge.status]}</Badge>
                    <span>
                      Pledged <DateText value={pledge.createdAt} />
                    </span>
                  </div>
                  <h2 className="text-lg font-semibold text-navy-900">
                    {request ? request.title : 'Request no longer available'}
                  </h2>
                  {request ? <p className="text-sm text-charcoal-700">{request.recipientPublicName}</p> : null}
                  <p className="text-xl font-semibold text-navy-900">
                    <MoneyText amountMinor={pledge.amount.amountMinor} currency={pledge.amount.currency} />{' '}
                    <span className="text-sm font-normal text-charcoal-700">(not yet paid)</span>
                  </p>
                  {reason ? <p className="text-sm text-charcoal-700">{reason}</p> : null}
                  <div className="flex flex-wrap gap-2">
                    {request ? (
                      <Link
                        href={`/discover/${request.id}`}
                        className="inline-flex min-h-11 items-center rounded-md border border-navy-900 px-4 text-sm font-medium text-navy-900 hover:bg-navy-100"
                      >
                        View request
                      </Link>
                    ) : null}
                    {pledge.status === 'ACTIVE' ? (
                      <Button
                        variant="ghost"
                        aria-label={`Cancel pledge${request ? ` for ${request.title}` : ''}`}
                        onClick={() => setToCancel(pledge)}
                      >
                        Cancel pledge
                      </Button>
                    ) : null}
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}
      {q.hasNextPage ? (
        <Button variant="secondary" loading={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
          Load more
        </Button>
      ) : null}
      {toCancel ? (
        <CancelPledgeDialog
          open
          pledge={toCancel}
          onClose={() => setToCancel(null)}
          onCancelled={() => {
            setToCancel(null);
            setMessage('Your pledge was cancelled.');
          }}
        />
      ) : null}
    </div>
  );
}
