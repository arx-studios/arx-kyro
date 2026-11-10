'use client';

import { Alert, Badge, Card, DateText, EmptyState, Spinner } from '@kyro/design-system';
import { ApiError, type SwipeAction } from '@kyro/api-client';
import Link from 'next/link';
import { useState } from 'react';
import { useMe } from '@/features/authentication/useMe';
import { errorMessage } from '@/lib/errors';
import { ActionButtons, type ActionMode } from './ActionButtons';
import { usePublicRequest, useSwipe } from './hooks';
import { ACTION_PAST, EXPENSE_LABELS, loginHref } from './labels';
import { FundingPanel } from '@/features/funding/FundingPanel';
import { ReportAction } from '@/features/reports/ReportAction';

export function RequestDetailView({ id }: { id: string }) {
  const { user } = useMe();
  const q = usePublicRequest(id);
  const swipe = useSwipe();
  const [pending, setPending] = useState<SwipeAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState('');

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
        title={notFound ? 'This request is not available' : 'We could not load this request'}
        description={
          notFound
            ? 'It may have been funded, withdrawn or is no longer public.'
            : errorMessage(q.error)
        }
        action={
          <Link href="/discover" className="underline">
            Back to the feed
          </Link>
        }
      />
    );
  }

  const r = q.data;
  const detailLogin = loginHref('/discover/' + id);
  const mode: ActionMode = !user ? 'anonymous' : user.roles.includes('DONOR') ? 'donor' : 'readonly';
  const place = [r.institutionName, r.institutionCity, r.institutionCountry].filter(Boolean).join(', ');

  async function onAction(action: SwipeAction) {
    if (pending) return; // duplicate clicks while a request is in flight are ignored
    setPending(action);
    setError(null);
    try {
      await swipe.mutateAsync({ requestId: id, action });
      setMessage(`${ACTION_PAST[action]} this request.`);
    } catch (e) {
      setError(errorMessage(e, 'Your response could not be saved.'));
      setMessage('Your response could not be saved.');
    } finally {
      setPending(null);
    }
  }

  return (
    <article className="flex flex-col gap-6" aria-labelledby="request-title">
      <p>
        <Link href="/discover" className="text-sm underline">
          Back to the feed
        </Link>
      </p>
      <header className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="success">
            Verified <DateText value={r.verifiedAt} />
          </Badge>
          <Badge tone="neutral">{EXPENSE_LABELS[r.expenseType] ?? r.expenseType}</Badge>
          {r.viewerAction ? <Badge tone="info">Your response: {r.viewerAction === 'SAVED' ? 'Saved' : r.viewerAction === 'PASS' ? 'Passed' : 'Interested'}</Badge> : null}
        </div>
        <h1 id="request-title" className="text-2xl font-semibold text-navy-900">
          {r.title}
        </h1>
        <p className="text-charcoal-700">
          {r.recipientPublicName}
          {place ? ` · ${place}` : ''}
          {r.courseName ? ` · ${r.courseName}` : ''}
        </p>
      </header>

      <section aria-labelledby="story-heading">
        <h2 id="story-heading" className="mb-2 text-lg font-semibold text-navy-900">
          Story
        </h2>
        <p className="whitespace-pre-line text-charcoal-900">{r.story}</p>
      </section>

      <Card className="p-5">
        <h2 className="mb-2 text-lg font-semibold text-navy-900">How this request was verified</h2>
        <p className="text-charcoal-900">
          Reviewed by the KYRO verification team on <DateText value={r.verifiedAt} />. Supporting documents stay
          private.
        </p>
      </Card>

      <section aria-labelledby="funding-heading">
        <h2 id="funding-heading" className="mb-2 text-lg font-semibold text-navy-900">
          Funding
        </h2>
        <FundingPanel
          requestId={id}
          funding={r.funding}
          mode={mode}
          loginHref={detailLogin}
        />
        {r.dueDate ? (
          <p className="mt-3 text-sm text-charcoal-700">
            Payment due <DateText value={r.dueDate} timeZone="UTC" />
          </p>
        ) : null}
      </section>

      <section aria-labelledby="respond-heading" className="flex flex-col gap-3">
        <h2 id="respond-heading" className="text-lg font-semibold text-navy-900">
          Your response
        </h2>
        {mode === 'anonymous' ? (
          <Alert tone="info">Sign in as a donor to pass, save or show interest.</Alert>
        ) : null}
        {mode === 'readonly' ? <Alert tone="info">Your account does not have the donor role.</Alert> : null}
        {error ? <Alert tone="danger">{error}</Alert> : null}
        <ActionButtons
          mode={mode}
          loginNext={`/discover/${id}`}
          onAction={(a) => void onAction(a)}
          current={r.viewerAction ?? null}
          disabled={pending !== null}
        />
        <div role="status" aria-live="polite" className="text-sm text-charcoal-700">
          {message}
        </div>
      </section>

      <section aria-labelledby="report-heading" className="flex flex-col gap-1 border-t border-warm-200 pt-4">
        <h2 id="report-heading" className="text-sm font-semibold text-navy-900">
          Something wrong with this request?
        </h2>
        <div>
          <ReportAction requestId={id} signedIn={Boolean(user)} loginHref={detailLogin} />
        </div>
      </section>
    </article>
  );
}
