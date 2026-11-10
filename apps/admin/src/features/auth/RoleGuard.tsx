'use client';

import { useEffect, type ReactNode } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Alert, Button, Spinner } from '@kyro/design-system';
import { canAccessConsole } from '@/lib/decision';
import { useLogout } from './useLogout';
import { useMe } from './useMe';

export function NotAuthorised({ email }: { email?: string }) {
  const logout = useLogout();
  return (
    <main id="main" className="mx-auto max-w-xl p-6">
      <Alert tone="danger" title="Not authorised">
        <p>
          {email ? `${email} does not` : 'This account does not'} have the Verifier or Admin role required for the
          operations console.
        </p>
      </Alert>
      <div className="mt-4">
        <Button variant="secondary" onClick={() => logout.mutate()} loading={logout.isPending}>
          Log out
        </Button>
      </div>
    </main>
  );
}

/**
 * Requires a signed-in VERIFIER or ADMIN. This is a convenience only: every `/admin` API call is
 * authorised server-side regardless of what this component renders.
 */
export function RoleGuard({ children }: { children: ReactNode }) {
  const { user, isLoading, error } = useMe();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !error && !user) {
      router.replace(`/login?next=${encodeURIComponent(pathname || '/queue')}`);
    }
  }, [isLoading, error, user, router, pathname]);

  if (isLoading) {
    return (
      <div className="flex justify-center p-10">
        <Spinner label="Checking your access" />
      </div>
    );
  }
  if (error) {
    return (
      <main id="main" className="mx-auto max-w-xl p-6">
        <Alert tone="danger" title="Could not check your access">
          <p>The API is unreachable. Try again shortly.</p>
        </Alert>
      </main>
    );
  }
  if (!user) {
    return (
      <div className="flex justify-center p-10">
        <Spinner label="Redirecting to sign in" />
      </div>
    );
  }
  if (!canAccessConsole(user.roles)) return <NotAuthorised email={user.email} />;
  return <>{children}</>;
}
