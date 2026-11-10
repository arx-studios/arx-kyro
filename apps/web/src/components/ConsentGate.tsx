'use client';

import { Alert, Button, Dialog } from '@kyro/design-system';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useAcceptCurrentPolicies, useConsentStatus } from '@/features/account/consents';
import { useMe } from '@/features/authentication/useMe';
import { errorMessage } from '@/lib/errors';

const AUTH_PATHS = ['/login', '/register', '/verify-email', '/forgot-password', '/reset-password'];

export function isAuthPath(pathname: string | null): boolean {
  if (!pathname) return false;
  return AUTH_PATHS.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

/**
 * Blocking re-acceptance prompt, rendered once from the root layout. Shown only to signed-in users, only when
 * the server reports outdated consent versions, and never on the auth pages. UI gate only: the server does not
 * yet enforce it (Phase 6).
 */
export function ConsentGate() {
  const pathname = usePathname();
  const { user } = useMe();
  const status = useConsentStatus(Boolean(user) && !isAuthPath(pathname));
  const accept = useAcceptCurrentPolicies();

  if (!user || isAuthPath(pathname) || !status.data?.reacceptRequired) return null;
  const s = status.data;

  return (
    <Dialog
      open
      onClose={() => undefined}
      closeOnBackdrop={false}
      title="Our policies have been updated"
      description="Please review and accept the current Terms of Use and Privacy Policy to keep using KYRO."
      footer={
        <Button loading={accept.isPending} onClick={() => accept.mutate(s)}>
          Accept
        </Button>
      }
    >
      <div className="flex flex-col gap-3">
        {accept.isError ? <Alert tone="danger">{errorMessage(accept.error)}</Alert> : null}
        <ul className="list-disc pl-5 text-charcoal-800">
          <li>
            <Link href="/trust#terms" className="underline" target="_blank" rel="noreferrer">
              Terms of Use
            </Link>{' '}
            (version {s.current.termsVersion})
          </li>
          <li>
            <Link href="/trust#privacy" className="underline" target="_blank" rel="noreferrer">
              Privacy Policy
            </Link>{' '}
            (version {s.current.privacyVersion})
          </li>
        </ul>
      </div>
    </Dialog>
  );
}
