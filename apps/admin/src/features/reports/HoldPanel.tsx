'use client';

import { useState } from 'react';
import type { RequestHold } from '@kyro/api-client';
import { Alert, Button, DateText, Dialog } from '@kyro/design-system';
import { describeError, type FriendlyProblem } from '@/lib/problems';

export interface HoldPanelProps {
  hold: RequestHold;
  /** Release is an ADMIN-only action; hidden (not just disabled) otherwise. The API enforces it too. */
  isAdmin: boolean;
  onRelease: () => Promise<void>;
}

export function HoldPanel({ hold, isAdmin, onRelease }: HoldPanelProps) {
  const [confirming, setConfirming] = useState(false);
  const [pending, setPending] = useState(false);
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);

  async function release() {
    setPending(true);
    try {
      await onRelease();
    } catch (err) {
      setProblem(describeError(err));
    } finally {
      setPending(false);
      setConfirming(false);
    }
  }

  return (
    <section aria-labelledby="hold-heading" className="space-y-3 rounded-lg border border-warning-700/40 bg-warning-50 p-4">
      <h2 id="hold-heading" className="text-lg font-semibold text-navy-900">
        Moderation hold
      </h2>
      <p className="text-sm">
        This request is hidden from discovery. Placed by {hold.placedBy.displayName} on{' '}
        <DateText value={hold.placedAt} withTime />. Reason: {hold.reason}
      </p>
      {problem ? (
        <Alert tone={problem.tone} title={problem.title}>
          <p>{problem.message}</p>
        </Alert>
      ) : null}
      {isAdmin ? (
        <>
          <Button variant="secondary" onClick={() => setConfirming(true)}>
            Release hold
          </Button>
          <Dialog
            open={confirming}
            onClose={() => (pending ? undefined : setConfirming(false))}
            title="Release hold"
            description="The request becomes visible in discovery again. This is recorded in the audit log."
            footer={
              <>
                <Button variant="secondary" onClick={() => setConfirming(false)} disabled={pending}>
                  Go back
                </Button>
                <Button onClick={release} loading={pending}>
                  Confirm release
                </Button>
              </>
            }
          >
            <p className="text-sm">Only release the hold if the report has been fully investigated.</p>
          </Dialog>
        </>
      ) : (
        <p className="text-sm text-charcoal-700">Only an admin can release a hold.</p>
      )}
    </section>
  );
}
