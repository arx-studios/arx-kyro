import { Badge, DateText, MoneyText } from '@kyro/design-system';
import type { RequestCard } from '@kyro/api-client';
import { EXPENSE_LABELS } from './labels';

function pct(part: number, total: number): number {
  return total > 0 ? Math.min(100, Math.round((part / total) * 100)) : 0;
}

/** Funding numbers: pledged and confirmed are always shown separately. */
export function FundingSummary({ funding }: { funding: RequestCard['funding'] }) {
  const { target, pledgedMinor, confirmedMinor } = funding;
  return (
    <dl className="grid grid-cols-2 gap-3 text-sm">
      <div>
        <dt className="text-charcoal-700">Pledged</dt>
        <dd className="font-medium text-charcoal-900">
          <MoneyText amountMinor={pledgedMinor} currency={target.currency} />
          <span className="ml-1 text-charcoal-700">({pct(pledgedMinor, target.amountMinor)}%)</span>
        </dd>
      </div>
      <div>
        <dt className="text-charcoal-700">Confirmed</dt>
        <dd className="font-medium text-charcoal-900">
          <MoneyText amountMinor={confirmedMinor} currency={target.currency} />
          <span className="ml-1 text-charcoal-700">({pct(confirmedMinor, target.amountMinor)}%)</span>
        </dd>
      </div>
    </dl>
  );
}

/** The privacy-safe card body shared by the feed and the saved list. */
export function RequestSummary({
  request,
  headingId,
  headingLevel = 2,
  showExcerpt = true,
}: {
  request: RequestCard;
  headingId?: string;
  headingLevel?: 2 | 3;
  showExcerpt?: boolean;
}) {
  const Heading = headingLevel === 2 ? 'h2' : 'h3';
  const place = [request.institutionName, request.institutionCity].filter(Boolean).join(', ');
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone="success">
          Verified <DateText value={request.verifiedAt} />
        </Badge>
        <Badge tone="neutral">{EXPENSE_LABELS[request.expenseType] ?? request.expenseType}</Badge>
      </div>
      <Heading id={headingId} className="text-xl font-semibold text-navy-900">
        {request.title}
      </Heading>
      <p className="text-sm text-charcoal-700">
        {request.recipientPublicName}
        {place ? ` · ${place}` : ''}
        {request.courseName ? ` · ${request.courseName}` : ''}
      </p>
      {showExcerpt ? <p className="text-charcoal-900">{request.storyExcerpt}</p> : null}
      <p className="text-2xl font-semibold text-navy-900">
        <MoneyText amountMinor={request.amount.amountMinor} currency={request.amount.currency} />
      </p>
      {request.dueDate ? (
        <p className="text-sm text-charcoal-700">
          Due <DateText value={request.dueDate} timeZone="UTC" />
        </p>
      ) : null}
      <FundingSummary funding={request.funding} />
    </div>
  );
}
