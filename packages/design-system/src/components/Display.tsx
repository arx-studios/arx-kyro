import { forwardRef, type HTMLAttributes, type ReactNode } from 'react';
import { cn } from '../cn';
import { formatMoney } from '../money';

/* ---------- Card ---------- */
export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Card(
  { className, ...rest },
  ref,
) {
  return (
    <div
      ref={ref}
      className={cn('rounded-lg border border-warm-200 bg-white p-6 text-charcoal-900 shadow-card', className)}
      {...rest}
    />
  );
});

/* ---------- Badge ---------- */
export type BadgeTone = 'neutral' | 'info' | 'accent' | 'success' | 'warning' | 'danger';

const tones: Record<BadgeTone, string> = {
  neutral: 'bg-warm-100 text-charcoal-700 border-warm-300',
  info: 'bg-navy-100 text-navy-800 border-navy-700/30',
  accent: 'bg-champagne-100 text-champagne-700 border-champagne-400',
  success: 'bg-success-50 text-success-700 border-success-600/40',
  warning: 'bg-warning-50 text-warning-700 border-warning-700/40',
  danger: 'bg-danger-50 text-danger-700 border-danger-600/40',
};

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: BadgeTone;
}

export const Badge = forwardRef<HTMLSpanElement, BadgeProps>(function Badge(
  { tone = 'neutral', className, ...rest },
  ref,
) {
  return (
    <span
      ref={ref}
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        tones[tone],
        className,
      )}
      {...rest}
    />
  );
});

/* ---------- Status badges (text label always present; never colour alone) ---------- */
export type RequestStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'IN_REVIEW'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'FUNDED'
  | 'CLOSED';

const REQUEST_STATUS: Record<RequestStatus, { label: string; tone: BadgeTone }> = {
  DRAFT: { label: 'Draft', tone: 'neutral' },
  SUBMITTED: { label: 'Submitted', tone: 'info' },
  IN_REVIEW: { label: 'In review', tone: 'info' },
  CHANGES_REQUESTED: { label: 'Changes requested', tone: 'warning' },
  APPROVED: { label: 'Verified', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
  WITHDRAWN: { label: 'Withdrawn', tone: 'neutral' },
  FUNDED: { label: 'Funded', tone: 'success' },
  CLOSED: { label: 'Closed', tone: 'neutral' },
};

export function RequestStatusBadge({ status, className }: { status: RequestStatus; className?: string }) {
  const s = REQUEST_STATUS[status] ?? { label: status, tone: 'neutral' as BadgeTone };
  return (
    <Badge tone={s.tone} className={className}>
      {s.label}
    </Badge>
  );
}

export type CaseStatus = 'OPEN' | 'ASSIGNED' | 'CLOSED';

const CASE_STATUS: Record<CaseStatus, { label: string; tone: BadgeTone }> = {
  OPEN: { label: 'Open', tone: 'info' },
  ASSIGNED: { label: 'Assigned', tone: 'accent' },
  CLOSED: { label: 'Closed', tone: 'neutral' },
};

export function CaseStatusBadge({ status, className }: { status: CaseStatus; className?: string }) {
  const s = CASE_STATUS[status] ?? { label: status, tone: 'neutral' as BadgeTone };
  return (
    <Badge tone={s.tone} className={className}>
      {s.label}
    </Badge>
  );
}

/* ---------- Alert ---------- */
export type AlertTone = 'info' | 'success' | 'warning' | 'danger';

const alertTones: Record<AlertTone, string> = {
  info: 'bg-navy-100 border-navy-700 text-navy-900',
  success: 'bg-success-50 border-success-600 text-success-700',
  warning: 'bg-warning-50 border-warning-700 text-warning-700',
  danger: 'bg-danger-50 border-danger-600 text-danger-700',
};

export interface AlertProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  tone?: AlertTone;
  title?: ReactNode;
}

export const Alert = forwardRef<HTMLDivElement, AlertProps>(function Alert(
  { tone = 'info', title, className, children, role, ...rest },
  ref,
) {
  // danger/warning interrupt (role=alert); info/success are polite (role=status)
  const resolvedRole = role ?? (tone === 'danger' || tone === 'warning' ? 'alert' : 'status');
  return (
    <div
      ref={ref}
      role={resolvedRole}
      className={cn('rounded-md border-l-4 px-4 py-3 text-sm', alertTones[tone], className)}
      {...rest}
    >
      {title ? <p className="font-semibold">{title}</p> : null}
      {children ? <div className={title ? 'mt-1' : undefined}>{children}</div> : null}
    </div>
  );
});

/* ---------- EmptyState ---------- */
export interface EmptyStateProps extends Omit<HTMLAttributes<HTMLDivElement>, 'title'> {
  title: ReactNode;
  description?: ReactNode;
  /** Optional call to action (e.g. a Button). */
  action?: ReactNode;
}

export const EmptyState = forwardRef<HTMLDivElement, EmptyStateProps>(function EmptyState(
  { title, description, action, className, ...rest },
  ref,
) {
  return (
    <div
      ref={ref}
      className={cn(
        'flex flex-col items-center gap-3 rounded-lg border border-dashed border-warm-300 bg-warm-100 px-6 py-12 text-center',
        className,
      )}
      {...rest}
    >
      <h2 className="text-lg font-semibold text-charcoal-900">{title}</h2>
      {description ? <p className="max-w-prose text-charcoal-700">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  );
});

/* ---------- SandboxBanner ---------- */
export interface SandboxBannerProps extends HTMLAttributes<HTMLDivElement> {
  message?: ReactNode;
}

export const SandboxBanner = forwardRef<HTMLDivElement, SandboxBannerProps>(function SandboxBanner(
  { message, className, ...rest },
  ref,
) {
  return (
    <div
      ref={ref}
      role="note"
      aria-label="Sandbox notice"
      className={cn('bg-navy-900 px-4 py-2 text-center text-sm text-warm-50', className)}
      {...rest}
    >
      <strong className="font-semibold text-champagne-300">Sandbox</strong>{' '}
      {message ?? 'Demonstration environment. All data is fictional and no real money is moved.'}
    </div>
  );
});

/* ---------- MoneyText ---------- */
export interface MoneyTextProps extends HTMLAttributes<HTMLSpanElement> {
  /** Integer minor units (paise, cents). */
  amountMinor: number;
  /** ISO 4217 code. */
  currency: string;
  locale?: string;
}

export function MoneyText({ amountMinor, currency, locale, className, ...rest }: MoneyTextProps) {
  return (
    <span className={cn('tabular-nums', className)} {...rest}>
      {formatMoney(amountMinor, currency, locale)}
    </span>
  );
}

/* ---------- DateText ---------- */
export interface DateTextProps extends HTMLAttributes<HTMLTimeElement> {
  /** ISO 8601 string or Date. */
  value: string | Date;
  locale?: string;
  /** Include the time of day. Default false (date only). */
  withTime?: boolean;
  timeZone?: string;
}

export function DateText({ value, locale = 'en-IN', withTime = false, timeZone, ...rest }: DateTextProps) {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return <time {...rest}>Invalid date</time>;
  const text = new Intl.DateTimeFormat(locale, {
    dateStyle: 'medium',
    ...(withTime ? { timeStyle: 'short' as const } : {}),
    timeZone,
  }).format(date);
  return (
    <time dateTime={date.toISOString()} {...rest}>
      {text}
    </time>
  );
}
