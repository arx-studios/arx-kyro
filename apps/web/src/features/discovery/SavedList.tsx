'use client';

import { Alert, Badge, Button, Card, DateText, EmptyState, Spinner } from '@kyro/design-system';
import Link from 'next/link';
import { useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { useRemoveSaved, useSaved } from './hooks';
import { RequestSummary } from './RequestSummary';

export function SavedList() {
  const q = useSaved();
  const remove = useRemoveSaved();
  const [message, setMessage] = useState('');

  if (q.isPending) {
    return (
      <div className="flex justify-center p-12">
        <Spinner label="Loading saved requests" />
      </div>
    );
  }
  if (q.isError) {
    return (
      <Alert tone="danger" title="We could not load your saved requests">
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
      {remove.isError ? (
        <Alert tone="danger">Could not remove it: {errorMessage(remove.error)} It is back in your list.</Alert>
      ) : null}
      <div role="status" aria-live="polite" className="sr-only">
        {message}
      </div>
      {items.length === 0 ? (
        <EmptyState
          title="Nothing saved yet"
          description="Requests you save or mark as interested appear here."
          action={
            <Link href="/discover" className="underline">
              Browse verified requests
            </Link>
          }
        />
      ) : (
        <ul className="flex flex-col gap-4">
          {items.map(({ request, action, savedAt }) => (
            <li key={request.id}>
              <Card className="flex flex-col gap-3 p-5">
                <div className="flex flex-wrap items-center gap-2 text-sm text-charcoal-700">
                  <Badge tone={action === 'INTERESTED' ? 'accent' : 'info'}>
                    {action === 'INTERESTED' ? 'Interested' : 'Saved'}
                  </Badge>
                  <span>
                    <DateText value={savedAt} />
                  </span>
                </div>
                <RequestSummary request={request} headingLevel={3} showExcerpt={false} />
                <div className="flex flex-wrap gap-2">
                  <Link
                    href={`/discover/${request.id}`}
                    className="inline-flex min-h-11 items-center rounded-md border border-navy-900 px-4 text-sm font-medium text-navy-900 hover:bg-navy-100"
                  >
                    View details
                  </Link>
                  <Button
                    variant="ghost"
                    aria-label={`Remove ${request.title} from saved`}
                    disabled={remove.isPending && remove.variables === request.id}
                    onClick={() =>
                      remove.mutate(request.id, {
                        onSuccess: () => setMessage(`Removed ${request.title} from your saved list.`),
                      })
                    }
                  >
                    Remove
                  </Button>
                </div>
              </Card>
            </li>
          ))}
        </ul>
      )}
      {q.hasNextPage ? (
        <Button variant="secondary" loading={q.isFetchingNextPage} onClick={() => void q.fetchNextPage()}>
          Load more
        </Button>
      ) : null}
    </div>
  );
}
