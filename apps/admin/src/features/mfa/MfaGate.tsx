'use client';

import { useEffect, type ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Alert, Card, SandboxBanner, Spinner } from '@kyro/design-system';
import { canAccessConsole } from '@/lib/decision';
import { mfaRedirectTarget } from '@/lib/mfa';
import { NotAuthorised } from '@/features/auth/RoleGuard';
import { useMe } from '@/features/auth/useMe';

/**
 * Frame for the MFA pages (outside the console shell). Requires a signed-in staff user and sends them
 * to the right step: setup when not enrolled, verify when enrolled, the console when nothing is needed.
 */
export function MfaGate({ step, title, children }: { step: 'setup' | 'verify'; title: string; children: ReactNode }) {
  const { user, isLoading, error } = useMe();
  const router = useRouter();
  const pathname = usePathname();

  const staff = !!user && canAccessConsole(user.roles);
  const needed = staff && user ? mfaRedirectTarget(user) : null;
  const wrongStep = needed !== null && needed !== `/mfa/${step}`;
  const nothingToDo = staff && needed === null;

  useEffect(() => {
    if (isLoading || error) return;
    if (!user) router.replace(`/login?next=${encodeURIComponent(pathname || '/')}`);
    else if (nothingToDo) router.replace('/');
    else if (wrongStep && needed) router.replace(needed);
  }, [isLoading, error, user, nothingToDo, wrongStep, needed, router, pathname]);

  let body: ReactNode;
  if (isLoading || (!error && (!user || nothingToDo || wrongStep))) {
    body = (
      <div className="flex justify-center p-10">
        <Spinner label="Checking your access" />
      </div>
    );
  } else if (error) {
    body = (
      <Alert tone="danger" title="Could not check your access">
        <p>The API is unreachable. Try again shortly.</p>
      </Alert>
    );
  } else if (!staff) {
    return <NotAuthorised email={user?.email} />;
  } else {
    body = (
      <Card className="p-6">
        <h1 className="mb-1 text-2xl font-semibold text-navy-900">{title}</h1>
        {children}
      </Card>
    );
  }

  return (
    <>
      <SandboxBanner />
      <main id="main" className="mx-auto max-w-lg px-4 py-12">
        {body}
      </main>
    </>
  );
}
