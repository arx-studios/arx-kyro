'use client';

import { Alert, Card, EmptyState, Spinner } from '@kyro/design-system';
import type { RequestCard, SwipeAction } from '@kyro/api-client';
import { AnimatePresence, motion, useReducedMotion } from 'motion/react';
import Link from 'next/link';
import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { errorMessage } from '@/lib/errors';
import { ActionButtons, type ActionMode } from './ActionButtons';
import { ACTION_LABELS, ACTION_PAST, keyToAction, loginHref, swipeActionForOffset } from './labels';
import { RequestSummary } from './RequestSummary';

const PREFETCH_WHEN_REMAINING = 3;

export interface SwipeDeckProps {
  cards: RequestCard[];
  mode: ActionMode;
  /** Performs the swipe; a rejection restores the card. Must be idempotent server side. */
  onSwipe: (requestId: string, action: SwipeAction) => Promise<unknown>;
  hasMore: boolean;
  isFetchingMore: boolean;
  onNeedMore: () => void;
}

/**
 * Accessible card stack. Three equivalent input paths: touch/mouse drag (motion), always visible
 * buttons, and the keyboard (left = Pass, right = Interested, S = Save). Cards are removed
 * optimistically and restored if the request fails.
 */
export function SwipeDeck({ cards, mode, onSwipe, hasMore, isFetchingMore, onNeedMore }: SwipeDeckProps) {
  const reduced = useReducedMotion() ?? false;
  const [removed, setRemoved] = useState<ReadonlySet<string>>(new Set());
  const [announcement, setAnnouncement] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [exitDir, setExitDir] = useState(0);
  const inflight = useRef(new Set<string>());
  const focusAfterAction = useRef(false);
  const cardRef = useRef<HTMLElement>(null);
  const endRef = useRef<HTMLDivElement>(null);

  const visible = cards.filter((c) => !removed.has(c.id));
  const top = visible[0];

  useEffect(() => {
    if (visible.length <= PREFETCH_WHEN_REMAINING && hasMore && !isFetchingMore) onNeedMore();
  }, [visible.length, hasMore, isFetchingMore, onNeedMore]);

  // Focus management: after an action, move focus to the next card (or the end message).
  useEffect(() => {
    if (!focusAfterAction.current) return;
    focusAfterAction.current = false;
    (top ? cardRef.current : endRef.current)?.focus();
  }, [top?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const act = useCallback(
    (card: RequestCard, action: SwipeAction) => {
      if (mode !== 'donor') return;
      if (inflight.current.has(card.id)) return; // duplicate action for the same card
      inflight.current.add(card.id);
      focusAfterAction.current = true;
      setError(null);
      setExitDir(action === 'PASS' ? -1 : action === 'INTERESTED' ? 1 : 0);
      setRemoved((prev) => new Set(prev).add(card.id));
      setAnnouncement(`${ACTION_PAST[action]} ${card.title}.`);
      onSwipe(card.id, action)
        .catch((e: unknown) => {
          setRemoved((prev) => {
            const next = new Set(prev);
            next.delete(card.id);
            return next;
          });
          focusAfterAction.current = true;
          const msg = errorMessage(e, 'Your response could not be saved.');
          setError(`${ACTION_LABELS[action]} did not go through: ${msg} The request is back on top.`);
          setAnnouncement(`Could not ${ACTION_LABELS[action].toLowerCase()} ${card.title}. It was restored.`);
        })
        .finally(() => inflight.current.delete(card.id));
    },
    [mode, onSwipe],
  );

  function onKeyDown(e: KeyboardEvent<HTMLElement>) {
    if (e.altKey || e.ctrlKey || e.metaKey) return;
    const action = keyToAction(e.key);
    if (!action || !top) return;
    e.preventDefault();
    act(top, action);
  }

  const cardBody = top ? (
    <>
      <RequestSummary request={top} headingId={`card-title-${top.id}`} />
      <p className="mt-3">
        <Link href={`/discover/${top.id}`} className="text-sm font-medium text-navy-800 underline">
          Read the full story and how it was verified
        </Link>
      </p>
    </>
  ) : null;

  return (
    // Key events bubble from the focused card or buttons; the region itself is not a control.
    // eslint-disable-next-line jsx-a11y/no-noninteractive-element-interactions
    <section aria-label="Verified requests" onKeyDown={onKeyDown} className="flex flex-col gap-4">
      {mode === 'anonymous' ? (
        <Alert tone="info">
          You are browsing as a guest.{' '}
          <Link href={loginHref('/discover')} className="font-medium underline">
            Sign in as a donor
          </Link>{' '}
          to pass, save or show interest.
        </Alert>
      ) : null}
      {mode === 'readonly' ? (
        <Alert tone="info">
          Your account does not have the donor role, so this feed is read-only for you.
        </Alert>
      ) : null}
      {error ? <Alert tone="danger">{error}</Alert> : null}

      <div role="status" aria-live="polite" className="sr-only">
        {announcement}
      </div>

      {top ? (
        reduced ? (
          <article
            key={top.id}
            ref={cardRef}
            tabIndex={-1}
            aria-labelledby={`card-title-${top.id}`}
            className="focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-700 rounded-lg"
          >
            <Card className="p-5">{cardBody}</Card>
          </article>
        ) : (
          <AnimatePresence mode="popLayout" custom={exitDir} initial={false}>
            <motion.article
              key={top.id}
              ref={cardRef}
              tabIndex={-1}
              aria-labelledby={`card-title-${top.id}`}
              custom={exitDir}
              className="touch-pan-y rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-navy-700"
              drag={mode === 'donor' ? 'x' : false}
              dragSnapToOrigin
              dragElastic={0.6}
              whileDrag={{ rotate: 0 }}
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{
                opacity: 0,
                x: exitDir * 400,
                transition: { duration: 0.2 },
              }}
              onDragEnd={(_e, info) => {
                const action = swipeActionForOffset(info.offset.x);
                if (action) act(top, action);
              }}
            >
              <Card className="p-5">{cardBody}</Card>
            </motion.article>
          </AnimatePresence>
        )
      ) : (
        <div ref={endRef} tabIndex={-1} className="focus:outline-none">
          {hasMore || isFetchingMore ? (
            <div className="flex justify-center p-12">
              <Spinner label="Loading more requests" />
            </div>
          ) : (
            <EmptyState
              title="You have seen every verified request for now"
              description="New verified requests appear here as they are approved. Your saved requests are kept in your list."
              action={
                mode === 'donor' ? (
                  <Link href="/saved" className="underline">
                    Go to saved requests
                  </Link>
                ) : undefined
              }
            />
          )}
        </div>
      )}

      {top ? (
        <>
          <ActionButtons
            mode={mode}
            loginNext="/discover"
            onAction={(a) => act(top, a)}
          />
          <p className="text-center text-sm text-charcoal-700">
            Keyboard: <kbd className="rounded border border-warm-300 px-1">←</kbd> Pass ·{' '}
            <kbd className="rounded border border-warm-300 px-1">S</kbd> Save ·{' '}
            <kbd className="rounded border border-warm-300 px-1">→</kbd> Interested
            {reduced || mode !== 'donor' ? '' : ' · or drag the card left or right'}
          </p>
          <p className="text-center text-sm text-charcoal-700">
            {visible.length} loaded{hasMore ? '+' : ''}
          </p>
        </>
      ) : null}
    </section>
  );
}
