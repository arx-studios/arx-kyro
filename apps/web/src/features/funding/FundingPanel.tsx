'use client';

import { Alert, Badge, Button, DateText, MoneyText } from '@kyro/design-system';
import type { FundingProgress, Pledge } from '@kyro/api-client';
import Link from 'next/link';
import { useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { confirmedPercent, outstandingMinor } from './amounts';
import { CancelPledgeDialog } from './CancelPledgeDialog';
import { useFundingSummary } from './hooks';
import { PledgeDialog } from './PledgeDialog';

export type FundingMode = 'donor' | 'anonymous' | 'readonly';

/**
 * Funding numbers for a request. Outstanding need = target - confirmed. Pledges are shown on their
 * own line as non-binding intent and never move the progress bar or the outstanding amount.
 */
export function FundingFigures({
  target,
  confirmedMinor,
  pledgedMinor,
  activePledgeCount,
}: {
  target: FundingProgress['target'];
  confirmedMinor: number;
  pledgedMinor: number;
  activePledgeCount?: number;
}) {
  const { currency } = target;
  const outstanding = outstandingMinor(target.amountMinor, confirmedMinor);
  const pct = confirmedPercent(target.amountMinor, confirmedMinor);
  return (
    <div className="flex flex-col gap-3">
      <dl className="grid grid-cols-1 gap-3 text-sm min-[420px]:grid-cols-2">
        <div>
          <dt className="text-charcoal-700">Target</dt>
          <dd className="text-lg font-semibold text-navy-900">
            <MoneyText amountMinor={target.amountMinor} currency={currency} />
          </dd>
        </div>
        <div>
          <dt className="text-charcoal-700">Outstanding need</dt>
          <dd className="text-lg font-semibold text-navy-900">
            <MoneyText amountMinor={outstanding} currency={currency} />
          </dd>
        </div>
        <div>
          <dt className="text-charcoal-700">Confirmed</dt>
          <dd className="font-medium text-charcoal-900">
            <MoneyText amountMinor={confirmedMinor} currency={currency} />
            <span className="ml-1 text-charcoal-700">({pct}%)</span>
          </dd>
          {confirmedMinor === 0 ? (
            <dd className="text-xs text-charcoal-700">No payments are taken in this sandbox.</dd>
          ) : null}
        </div>
        <div>
          <dt className="text-charcoal-700">Pledged (not yet paid)</dt>
          <dd className="font-medium text-charcoal-900">
            <MoneyText amountMinor={pledgedMinor} currency={currency} />
            {activePledgeCount !== undefined ? (
              <span className="ml-1 text-charcoal-700">
                from {activePledgeCount} {activePledgeCount === 1 ? 'pledge' : 'pledges'}
              </span>
            ) : null}
          </dd>
          <dd className="text-xs text-charcoal-700">Pledges are non-binding and are not counted as received.</dd>
        </div>
      </dl>
      <div
        role="progressbar"
        aria-label="Confirmed payments as a share of the target"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={pct}
        className="h-2 w-full overflow-hidden rounded-full bg-warm-200"
      >
        <div className="h-full bg-navy-700" style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

/** The funding section of the request detail: figures plus the pledge action for the viewer. */
export function FundingPanel({
  requestId,
  funding,
  mode,
  loginHref,
}: {
  requestId: string;
  /** Funding from the public detail, shown while (or if) the summary is unavailable. */
  funding: FundingProgress;
  mode: FundingMode;
  loginHref: string;
}) {
  const summary = useFundingSummary(requestId);
  const [pledgeOpen, setPledgeOpen] = useState(false);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [message, setMessage] = useState('');

  const s = summary.data;
  const target = s?.target ?? funding.target;
  const confirmedMinor = s?.confirmedMinor ?? funding.confirmedMinor;
  const pledgedMinor = s?.pledgedMinor ?? funding.pledgedMinor;
  const viewerPledge: Pledge | null = s?.viewerPledge && s.viewerPledge.status === 'ACTIVE' ? s.viewerPledge : null;

  return (
    <div className="flex flex-col gap-4">
      <FundingFigures
        target={target}
        confirmedMinor={confirmedMinor}
        pledgedMinor={pledgedMinor}
        activePledgeCount={s?.activePledgeCount}
      />

      <div className="flex flex-col gap-2" aria-labelledby="pledge-heading" role="group">
        <h3 id="pledge-heading" className="text-base font-semibold text-navy-900">
          Pledge your support
        </h3>
        <p className="text-sm text-charcoal-900">A pledge is not a payment. No money is collected.</p>

        {mode === 'anonymous' ? (
          <p>
            <Link
              href={loginHref}
              className="inline-flex min-h-11 items-center rounded-md border border-navy-900 px-4 text-sm font-medium text-navy-900 hover:bg-navy-100"
            >
              Log in as a donor to pledge
            </Link>
          </p>
        ) : null}
        {mode === 'readonly' ? (
          <Alert tone="info">Only donor accounts can make pledges. You can still read this request.</Alert>
        ) : null}

        {mode === 'donor' && summary.isError ? (
          <Alert tone="warning">
            We could not check the current pledge limit. {errorMessage(summary.error)}{' '}
            <button type="button" className="underline" onClick={() => void summary.refetch()}>
              Try again
            </button>
          </Alert>
        ) : null}

        {mode === 'donor' && s && viewerPledge ? (
          <div className="flex flex-col gap-2 rounded-md border border-navy-700 bg-navy-100 p-4">
            <p className="flex flex-wrap items-center gap-2 text-sm text-navy-900">
              <Badge tone="info">Your pledge</Badge>
              <span className="font-semibold">
                <MoneyText amountMinor={viewerPledge.amount.amountMinor} currency={viewerPledge.amount.currency} />
              </span>
              <span>
                (not yet paid) since <DateText value={viewerPledge.createdAt} />
              </span>
            </p>
            <div>
              <Button variant="secondary" onClick={() => setCancelOpen(true)}>
                Cancel pledge
              </Button>
            </div>
          </div>
        ) : null}

        {mode === 'donor' && s && !viewerPledge ? (
          s.pledgeableMinor > 0 ? (
            <div>
              <Button size="lg" onClick={() => setPledgeOpen(true)}>
                Pledge (non-binding)
              </Button>
            </div>
          ) : (
            <Alert tone="info">No more pledges can be added to this request right now.</Alert>
          )
        ) : null}

        <div role="status" aria-live="polite" className="text-sm text-charcoal-700">
          {message}
        </div>
      </div>

      {s ? (
        <>
          <PledgeDialog
            open={pledgeOpen}
            onClose={() => setPledgeOpen(false)}
            requestId={requestId}
            currency={target.currency}
            pledgeableMinor={s.pledgeableMinor}
            onPledged={() => {
              setPledgeOpen(false);
              setMessage('Your non-binding pledge was saved. Nothing was charged.');
            }}
          />
          {viewerPledge ? (
            <CancelPledgeDialog
              open={cancelOpen}
              onClose={() => setCancelOpen(false)}
              pledge={viewerPledge}
              onCancelled={() => {
                setCancelOpen(false);
                setMessage('Your pledge was cancelled.');
              }}
            />
          ) : null}
        </>
      ) : null}
    </div>
  );
}
