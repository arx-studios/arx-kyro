'use client';

import { Alert, Button, Dialog, MoneyText } from '@kyro/design-system';
import { ApiError, type Pledge } from '@kyro/api-client';
import { useState } from 'react';
import { errorMessage } from '@/lib/errors';
import { useCancelPledge } from './hooks';

export function CancelPledgeDialog({
  open,
  onClose,
  pledge,
  onCancelled,
}: {
  open: boolean;
  onClose: () => void;
  pledge: Pledge;
  onCancelled: (p: Pledge) => void;
}) {
  const cancel = useCancelPledge();
  const [error, setError] = useState<string | null>(null);

  async function onConfirm() {
    setError(null);
    try {
      onCancelled(await cancel.mutateAsync({ pledgeId: pledge.id, requestId: pledge.requestId }));
    } catch (e) {
      setError(
        e instanceof ApiError && e.code === 'INVALID_STATE_TRANSITION'
          ? 'This pledge is no longer active.'
          : errorMessage(e, 'We could not cancel your pledge. Please try again.'),
      );
    }
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        if (!cancel.isPending) {
          setError(null);
          onClose();
        }
      }}
      title="Cancel this pledge?"
      description="Your pledge will be withdrawn. Nothing was charged, so nothing is refunded."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={cancel.isPending}>
            Keep pledge
          </Button>
          <Button variant="danger" onClick={() => void onConfirm()} loading={cancel.isPending}>
            Cancel pledge
          </Button>
        </>
      }
    >
      <p className="text-charcoal-900">
        Pledge of <MoneyText amountMinor={pledge.amount.amountMinor} currency={pledge.amount.currency} /> (not yet paid)
      </p>
      <div aria-live="polite" className="mt-3">
        {error ? <Alert tone="danger">{error}</Alert> : null}
      </div>
    </Dialog>
  );
}
