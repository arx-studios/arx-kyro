'use client';

import { Alert, Button, Dialog, Field, Input, formatMoney } from '@kyro/design-system';
import { ApiError, type Pledge } from '@kyro/api-client';
import { useId, useRef, useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { validatePledgeAmount } from './amounts';
import { useCreatePledge } from './hooks';

export interface PledgeDialogProps {
  open: boolean;
  onClose: () => void;
  requestId: string;
  currency: string;
  /** Largest pledge accepted right now (from the funding summary). */
  pledgeableMinor: number;
  onPledged: (pledge: Pledge) => void;
}

function newKey(): string {
  return globalThis.crypto.randomUUID();
}

/**
 * Non-binding pledge dialog. The Idempotency-Key is created once when the dialog opens and reused
 * for every retry of the same submission; it is only replaced if the donor changes the amount
 * after an attempt (a different body must not reuse a key).
 */
export function PledgeDialog(props: PledgeDialogProps) {
  // Remount the body per open so the key, fields and errors reset together.
  if (!props.open) return null;
  return <PledgeDialogBody {...props} />;
}

function PledgeDialogBody({ onClose, requestId, currency, pledgeableMinor, onPledged }: PledgeDialogProps) {
  const mutation = useCreatePledge();
  const keyRef = useRef<{ key: string; amountMinor: number | null }>({ key: newKey(), amountMinor: null });
  const [amount, setAmount] = useState('');
  const [acknowledged, setAcknowledged] = useState(false);
  const [amountError, setAmountError] = useState<string | undefined>();
  const [ackError, setAckError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const ackId = useId();

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (mutation.isPending) return;
    const result = validatePledgeAmount(amount, currency, pledgeableMinor);
    setAmountError(result.ok ? undefined : result.message);
    setAckError(acknowledged ? undefined : 'Confirm that you understand a pledge is not a payment.');
    setFormError(null);
    if (!result.ok || !acknowledged) return;

    const slot = keyRef.current;
    if (slot.amountMinor !== null && slot.amountMinor !== result.amountMinor) slot.key = newKey();
    slot.amountMinor = result.amountMinor;

    try {
      const pledge = await mutation.mutateAsync({
        requestId,
        amountMinor: result.amountMinor,
        currency,
        idempotencyKey: slot.key,
      });
      onPledged(pledge);
    } catch (err) {
      setFormError(pledgeErrorMessage(err));
    }
  }

  return (
    <Dialog
      open
      onClose={() => {
        if (!mutation.isPending) onClose();
      }}
      title="Make a non-binding pledge"
      description="A pledge is not a payment. No money is collected in this sandbox."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Not now
          </Button>
          <Button type="submit" form="pledge-form" loading={mutation.isPending}>
            Make pledge
          </Button>
        </>
      }
    >
      <form id="pledge-form" onSubmit={(e) => void onSubmit(e)} noValidate className="flex flex-col gap-4">
        <p className="text-sm text-charcoal-700">
          A pledge only tells the student and KYRO that you intend to help. You can cancel it at any time. It does not
          reduce what the student still needs until a payment is confirmed, and you owe nothing.
        </p>
        <Field
          label="Pledge amount"
          required
          hint={`You can pledge up to ${formatMoney(pledgeableMinor, currency)}.`}
          error={amountError}
        >
          {(p) => (
            <Input
              {...p}
              inputMode="decimal"
              autoComplete="off"
              placeholder="e.g. 5,000"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
            />
          )}
        </Field>
        <div className="flex flex-col gap-1">
          <div className="flex items-start gap-3">
            <input
              id={ackId}
              type="checkbox"
              className="mt-1 size-5 shrink-0"
              checked={acknowledged}
              aria-required="true"
              aria-invalid={ackError ? true : undefined}
              aria-describedby={ackError ? `${ackId}-error` : undefined}
              onChange={(e) => setAcknowledged(e.target.checked)}
            />
            <label htmlFor={ackId} className="text-sm text-charcoal-900">
              I understand a pledge is not a payment. No money is collected.
            </label>
          </div>
          {ackError ? (
            <p id={`${ackId}-error`} role="alert" className="text-sm font-medium text-danger-700">
              {ackError}
            </p>
          ) : null}
        </div>
        <div aria-live="polite">
          {formError ? (
            <Alert tone="danger" role="alert">
              {formError}
            </Alert>
          ) : null}
        </div>
      </form>
    </Dialog>
  );
}

export function pledgeErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'ACTIVE_PLEDGE_EXISTS':
        return 'You already have an active pledge on this request. Cancel it first if you want to change the amount.';
      case 'PLEDGE_EXCEEDS_REMAINING':
        return 'The amount still available to pledge has changed. The limit shown above is now up to date. Please choose a smaller amount.';
      case 'PLEDGE_NOT_ALLOWED':
        return 'A pledge is not possible for this request. It may be your own request or past its due date.';
      case 'IDEMPOTENCY_KEY_REUSED':
        return 'This attempt conflicted with an earlier one. Close this window and try again.';
      default:
        return errorMessage(err, 'We could not save your pledge. Please try again.');
    }
  }
  return 'We could not reach KYRO. Check your connection and press Make pledge again. Retrying will not create a second pledge.';
}
