'use client';

import { Button } from '@kyro/design-system';
import Link from 'next/link';
import { useState } from 'react';
import { ReportDialog } from './ReportDialog';

/** "Report this request": a dialog for signed-in users, a login link for anonymous visitors. */
export function ReportAction({
  requestId,
  signedIn,
  loginHref,
}: {
  requestId: string;
  signedIn: boolean;
  loginHref: string;
}) {
  const [open, setOpen] = useState(false);
  if (!signedIn) {
    return (
      <Link href={loginHref} className="inline-flex min-h-11 items-center text-sm underline">
        Log in to report this request
      </Link>
    );
  }
  return (
    <>
      <Button variant="ghost" onClick={() => setOpen(true)}>
        Report this request
      </Button>
      <ReportDialog open={open} onClose={() => setOpen(false)} requestId={requestId} />
    </>
  );
}
