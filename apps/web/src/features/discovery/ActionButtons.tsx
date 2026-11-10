'use client';

import { Button, cn, focusRing } from '@kyro/design-system';
import type { SwipeAction } from '@kyro/api-client';
import Link from 'next/link';
import { loginHref } from './labels';

export type ActionMode = 'donor' | 'anonymous' | 'readonly';

const ORDER: { action: SwipeAction; label: string; aria: string; variant: 'secondary' | 'primary' }[] = [
  { action: 'PASS', label: 'Pass', aria: 'Pass on this request', variant: 'secondary' },
  { action: 'SAVED', label: 'Save', aria: 'Save this request for later', variant: 'secondary' },
  { action: 'INTERESTED', label: 'Interested', aria: 'I am interested in this request', variant: 'primary' },
];

const linkClass = (primary: boolean) =>
  cn(
    'inline-flex min-h-11 items-center justify-center rounded-md border px-4 text-sm font-medium',
    primary
      ? 'border-navy-900 bg-navy-900 text-warm-50 hover:bg-navy-800'
      : 'border-navy-900 bg-warm-50 text-navy-900 hover:bg-navy-100',
    focusRing,
  );

/** Always-visible Pass / Save / Interested controls: the non-gesture path. */
export function ActionButtons({
  mode,
  onAction,
  loginNext,
  current,
  disabled,
}: {
  mode: ActionMode;
  onAction?: (action: SwipeAction) => void;
  loginNext: string;
  /** The donor's existing action, shown as pressed (detail page). */
  current?: SwipeAction | null;
  disabled?: boolean;
}) {
  return (
    <div role="group" aria-label="Respond to this request" className="grid grid-cols-3 gap-2">
      {ORDER.map(({ action, label, aria, variant }) =>
        mode === 'anonymous' ? (
          <Link
            key={action}
            href={loginHref(loginNext)}
            aria-label={`${aria} (sign in required)`}
            className={linkClass(variant === 'primary')}
          >
            {label}
          </Link>
        ) : (
          <Button
            key={action}
            variant={variant}
            aria-label={aria}
            aria-pressed={current ? current === action : undefined}
            disabled={mode === 'readonly' || disabled}
            onClick={() => onAction?.(action)}
          >
            {label}
          </Button>
        ),
      )}
    </div>
  );
}
