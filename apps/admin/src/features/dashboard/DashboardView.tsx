'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { Alert, Button, Card, DateText, Spinner, cn, focusRing } from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';

export function DashboardView() {
  const query = useQuery({
    queryKey: queryKeys.dashboard,
    queryFn: async () => unwrap(await api.GET('/api/v1/admin/dashboard')),
  });

  if (query.isPending) {
    return (
      <div className="flex justify-center p-10">
        <Spinner label="Loading dashboard" />
      </div>
    );
  }
  if (query.isError) {
    const p = describeError(query.error);
    return (
      <Alert tone={p.tone} title={p.title}>
        <p>{p.message}</p>
      </Alert>
    );
  }

  const d = query.data;
  const tiles = [
    { label: 'Unassigned cases', value: d.unassignedCases, href: '/queue' },
    { label: 'My open cases', value: d.myOpenCases, href: '/queue' },
    { label: 'Open reports', value: d.openReports, href: '/reports' },
    { label: 'Unassigned reports', value: d.unassignedReports, href: '/reports' },
    { label: 'Active holds', value: d.activeHolds, href: '/reports' },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h1 className="text-2xl font-semibold text-navy-900">Dashboard</h1>
        <Button variant="secondary" onClick={() => void query.refetch()} loading={query.isFetching}>
          Refresh
        </Button>
      </div>
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {tiles.map((t) => (
          <li key={t.label}>
            <Card className="p-4">
              <Link
                href={t.href}
                className={cn('block rounded-sm', focusRing)}
                aria-label={`${t.label}: ${t.value}. Open list`}
              >
                <span className="block text-sm font-medium text-charcoal-700">{t.label}</span>
                <span className="mt-1 block text-3xl font-semibold text-navy-900">{t.value}</span>
              </Link>
            </Card>
          </li>
        ))}
      </ul>
      <p className="text-sm text-charcoal-700">
        Updated <DateText value={d.generatedAt} withTime />. Go to the{' '}
        <Link href="/queue" className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}>
          verification queue
        </Link>{' '}
        or the{' '}
        <Link href="/reports" className={cn('rounded-sm text-navy-800 underline underline-offset-2', focusRing)}>
          reports queue
        </Link>
        .
      </p>
    </div>
  );
}
