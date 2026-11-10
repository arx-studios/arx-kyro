'use client';

import { Alert, Spinner } from '@kyro/design-system';
import { useCallback, useMemo } from 'react';
import { useMe } from '@/features/authentication/useMe';
import { errorMessage } from '@/lib/errors';
import type { ActionMode } from './ActionButtons';
import { useFeed, useSwipe } from './hooks';
import { SwipeDeck } from './SwipeDeck';

export function DiscoverFeed() {
  const { user, isLoading: meLoading } = useMe();
  const feed = useFeed();
  const swipe = useSwipe();
  const { mutateAsync } = swipe;

  const mode: ActionMode = !user ? 'anonymous' : user.roles.includes('DONOR') ? 'donor' : 'readonly';

  const cards = useMemo(() => {
    const seen = new Set<string>();
    return (feed.data?.pages ?? [])
      .flatMap((p) => p.items)
      .filter((c) => (seen.has(c.id) ? false : (seen.add(c.id), true)));
  }, [feed.data]);

  const { fetchNextPage } = feed;
  const onNeedMore = useCallback(() => void fetchNextPage(), [fetchNextPage]);
  const onSwipe = useCallback(
    (requestId: string, action: Parameters<typeof mutateAsync>[0]['action']) =>
      mutateAsync({ requestId, action }),
    [mutateAsync],
  );

  if (feed.isPending || meLoading) {
    return (
      <div className="flex justify-center p-12">
        <Spinner label="Loading verified requests" />
      </div>
    );
  }
  if (feed.isError) {
    return (
      <Alert tone="danger" title="We could not load the feed">
        {errorMessage(feed.error)}{' '}
        <button type="button" className="underline" onClick={() => void feed.refetch()}>
          Try again
        </button>
      </Alert>
    );
  }

  return (
    <SwipeDeck
      cards={cards}
      mode={mode}
      onSwipe={onSwipe}
      hasMore={feed.hasNextPage}
      isFetchingMore={feed.isFetchingNextPage}
      onNeedMore={onNeedMore}
    />
  );
}
