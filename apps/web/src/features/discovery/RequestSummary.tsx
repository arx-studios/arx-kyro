import { Badge, DateText, MoneyText } from '@kyro/design-system';
import type { RequestCard } from '@kyro/api-client';
import { confirmedPercent } from '@/features/funding/amounts';
import { EXPENSE_LABELS } from './labels';

/** Card funding line: confirmed payments, plus pledges only when there are any (never counted as received). */
export function FundingSummary({ funding }: { funding: RequestCard['funding'] }) {
  const { target, pledgedMinor, confirmedMinor } = funding;
  return (
    <div className="flex flex-col gap-1 text-sm">
      <p className="font-medium text-charcoal-900">
        <span className="text-charcoal-700">Confirmed </span>
        <MoneyText amountMinor={confirmedMinor} currency={target.currency} />
        <span className="ml-1 text-charcoal-700">({confirmedPercent(target.amountMinor, confirmedMinor)}%)</span>
      </p>
      {pledgedMinor > 0 ? (
        <p className="text-charcoal-700">
          <MoneyText amountMinor={pledgedMinor} currency={target.currency} /> pledged (not yet paid)
        </p>
      ) : null}
    </div>
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
