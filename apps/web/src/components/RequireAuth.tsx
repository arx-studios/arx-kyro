'use client';

import { EmptyState, Spinner } from '@kyro/design-system';
import type { Role } from '@kyro/api-client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useEffect, type ReactNode } from 'react';
import { useMe } from '@/features/authentication/useMe';

/**
 * Gate for signed-in pages. Anonymous users are redirected to /login?next=<path>.
 * Signed-in users without one of `roles` see a "not permitted" state. UX only: the API enforces access.
 */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { user, isLoading } = useMe();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!isLoading && !user) router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [isLoading, user, router, pathname]);

  if (isLoading || !user) {
    return (
      <div className="flex justify-center p-12">
        <Spinner label="Loading" />
      </div>
    );
  }
  if (roles && !roles.some((r) => user.roles.includes(r))) {
    return (
      <div className="mx-auto max-w-xl p-6">
        <EmptyState
          title="You do not have access to this page"
          description="Your account does not have the role needed for this page."
          action={
            <Link href="/" className="underline">
              Back to the home page
            </Link>
          }
        />
      </div>
    );
  }
  return <>{children}</>;
}
