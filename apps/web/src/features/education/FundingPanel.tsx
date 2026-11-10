'use client';

import { Alert, Card, MoneyText, Spinner } from '@kyro/design-system';
import type { FundingSummary } from '@kyro/api-client';
import { errorMessage } from '@/lib/errors';
import { useFundingSummary } from './useFundingSummary';

/**
 * Semantics (blueprint section 7): outstanding = target - confirmed. Pledges are non-binding, are not
 * payments, and never count as money raised, so they are shown separately.
 */
export function FundingSummaryView({ summary }: { summary: FundingSummary }) {
  const currency = summary.target.currency;
  const rows: [string, number][] = [
    ['Target', summary.target.amountMinor],
    ['Confirmed', summary.confirmedMinor],
    ['Still needed', summary.remainingMinor],
  ];
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid grid-cols-1 gap-3 min-[400px]:grid-cols-3">
        {rows.map(([label, minor]) => (
          <div key={label}>
            <dt className="text-sm text-charcoal-700">{label}</dt>
            <dd className="text-lg font-semibold text-charcoal-900">
              <MoneyText amountMinor={minor} currency={currency} />
            </dd>
          </div>
        ))}
      </dl>
      <div className="rounded-lg border border-warm-300 bg-warm-50 p-3" data-testid="pledged-box">
        <p className="font-medium text-charcoal-900">
          Pledged (not yet confirmed):{' '}
          <MoneyText amountMinor={summary.pledgedMinor} currency={currency} />
          {summary.activePledgeCount > 0
            ? ` from ${summary.activePledgeCount} ${summary.activePledgeCount === 1 ? 'donor' : 'donors'}`
            : ''}
        </p>
        <p className="mt-1 text-sm text-charcoal-700">
          Pledges are not payments. A pledge is a non-binding statement of intent and is not counted as money
          raised until it is confirmed.
        </p>
      </div>
    </div>
  );
}

export function RecipientFundingPanel({ requestId, approved }: { requestId: string; approved: boolean }) {
  const q = useFundingSummary(requestId, approved);
  if (!approved) return null;
  return (
    <section aria-labelledby="funding-h" className="flex flex-col gap-4">
      <h2 id="funding-h" className="text-xl font-semibold text-charcoal-900">
        Funding
      </h2>
      <Card className="p-4">
        {q.isPending ? (
          <Spinner label="Loading funding" />
        ) : q.isError ? (
          <Alert tone="danger">{errorMessage(q.error, 'We could not load the funding summary.')}</Alert>
        ) : (
          <FundingSummaryView summary={q.data} />
        )}
      </Card>
    </section>
  );
}
