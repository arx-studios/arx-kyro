'use client';

import { useState, type FormEvent } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { Alert, Button, DateText, EmptyState, Field, Input, Spinner } from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function AuditView() {
  const [draft, setDraft] = useState('');
  const [entityId, setEntityId] = useState('');
  const [inputError, setInputError] = useState<string | undefined>();

  const query = useInfiniteQuery({
    queryKey: queryKeys.audit.list(entityId),
    initialPageParam: undefined as string | undefined,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.GET('/api/v1/admin/audit-events', {
          params: {
            query: { limit: 50, ...(entityId ? { entityId } : {}), ...(pageParam ? { cursor: pageParam } : {}) },
          },
        }),
      ),
    getNextPageParam: (last) => last.nextCursor ?? undefined,
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const v = draft.trim();
    if (v && !UUID.test(v)) {
      setInputError('Enter a valid entity id (UUID), or leave empty to show all events.');
      return;
    }
    setInputError(undefined);
    setEntityId(v);
  }

  const items = query.data?.pages.flatMap((p) => p.items) ?? [];
  const th = 'px-3 py-2 text-left text-xs font-semibold uppercase tracking-wide text-charcoal-700';
  const td = 'px-3 py-3 text-sm align-top';

  return (
    <div>
      <h1 className="mb-4 text-2xl font-semibold text-navy-900">Audit log</h1>
      <form onSubmit={onSubmit} noValidate className="mb-4 flex flex-wrap items-end gap-3" aria-label="Filter audit events">
        <div className="w-full max-w-md">
          <Field label="Entity id" hint="A case, request or document id" error={inputError}>
            <Input value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="00000000-0000-0000-0000-000000000000" />
          </Field>
        </div>
        <Button type="submit" variant="secondary">
          Apply filter
        </Button>
        {entityId ? (
          <Button
            variant="ghost"
            onClick={() => {
              setDraft('');
              setEntityId('');
              setInputError(undefined);
            }}
          >
            Clear filter
          </Button>
        ) : null}
      </form>

      {query.isPending ? (
        <div className="flex justify-center p-10">
          <Spinner label="Loading audit events" />
        </div>
      ) : query.isError ? (
        (() => {
          const p = describeError(query.error);
          return (
            <Alert tone={p.tone} title={p.title}>
              <p>{p.message}</p>
            </Alert>
          );
        })()
      ) : items.length === 0 ? (
        <EmptyState title="No audit events" description="No events match this filter." />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border border-warm-200 bg-white">
            <table className="w-full min-w-[56rem] border-collapse">
              <caption className="sr-only">Audit events, newest first</caption>
              <thead className="border-b border-warm-200 bg-warm-100">
                <tr>
                  <th scope="col" className={th}>When</th>
                  <th scope="col" className={th}>Actor</th>
                  <th scope="col" className={th}>Action</th>
                  <th scope="col" className={th}>Entity</th>
                  <th scope="col" className={th}>Details</th>
                </tr>
              </thead>
              <tbody>
                {items.map((ev) => (
                  <tr key={ev.id} className="border-b border-warm-100 last:border-0">
                    <td className={td}>
                      <DateText value={ev.occurredAt} withTime />
                    </td>
                    <td className={td}>{ev.actor?.displayName ?? <span className="text-charcoal-600">System</span>}</td>
                    <td className={`${td} font-mono text-xs`}>{ev.action}</td>
                    <td className={td}>
                      <div>{ev.entityType}</div>
                      {ev.entityId ? <div className="break-all font-mono text-xs text-charcoal-700">{ev.entityId}</div> : null}
                    </td>
                    <td className={td}>
                      <details>
                        <summary className="cursor-pointer text-navy-800 underline underline-offset-2">Show</summary>
                        <pre className="mt-1 max-w-md overflow-x-auto whitespace-pre-wrap text-xs">
                          {JSON.stringify(ev.details, null, 2)}
                        </pre>
                        {ev.correlationId ? (
                          <p className="mt-1 text-xs text-charcoal-700">Correlation: {ev.correlationId}</p>
                        ) : null}
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
  );
}
