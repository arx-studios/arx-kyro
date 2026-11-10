'use client';

import { Button, SandboxBanner, Spinner, cn, focusRing } from '@kyro/design-system';
import type { Role } from '@kyro/api-client';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import type { ReactNode } from 'react';
import { useLogout } from '@/features/authentication/hooks';
import { useMe } from '@/features/authentication/useMe';

export interface NavItem {
  href: string;
  label: string;
  /** Visible only when signed in with one of these roles. Omit for everyone. */
  roles?: Role[];
}

/** Header navigation. WEB-2 routes (/discover, /saved) are linked here; they 404 until WEB-2 lands. */
export const NAV_ITEMS: NavItem[] = [
  { href: '/discover', label: 'Discover' },
  { href: '/saved', label: 'Saved', roles: ['DONOR'] },
  { href: '/pledges', label: 'My pledges', roles: ['DONOR'] },
  { href: '/recipient/dashboard', label: 'My requests', roles: ['RECIPIENT'] },
];

export function visibleNav(roles: Role[] | undefined): NavItem[] {
  return NAV_ITEMS.filter((i) => !i.roles || (roles ?? []).some((r) => i.roles!.includes(r)));
}

const linkClass = cn(
  'inline-flex min-h-11 items-center rounded-md px-3 text-sm font-medium text-navy-900 hover:bg-navy-100',
  focusRing,
);

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, isLoading } = useMe();
  const logout = useLogout();

  async function onLogout() {
    try {
      await logout.mutateAsync();
    } catch {
      // Session may already be gone; treat as signed out.
    }
    router.replace('/');
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:px-4 focus:py-2 focus:text-navy-900"
      >
        Skip to main content
      </a>
      <SandboxBanner message="Fictional data, no real money. Nothing here is a real request or payment." />
      <header className="border-b border-warm-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2">
          <Link href="/" className={cn('mr-2 inline-flex min-h-11 items-center text-xl font-bold tracking-tight text-navy-900', focusRing)}>
            KYRO
          </Link>
          <nav aria-label="Main" className="flex flex-1 flex-wrap items-center gap-1">
            {visibleNav(user?.roles).map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className={cn(linkClass, pathname.startsWith(item.href) && 'bg-navy-100')}
                aria-current={pathname.startsWith(item.href) ? 'page' : undefined}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <div className="flex items-center gap-1">
            {isLoading ? (
              <Spinner size="sm" label="Checking your session" />
            ) : user ? (
              <>
                <Link
                  href="/profile"
                  className={cn(linkClass, pathname === '/profile' && 'bg-navy-100')}
                  aria-current={pathname === '/profile' ? 'page' : undefined}
                >
                  Profile
                </Link>
                <Button variant="secondary" onClick={onLogout} loading={logout.isPending}>
                  Log out
                </Button>
              </>
            ) : (
              <>
                <Link href="/login" className={linkClass}>
                  Log in
                </Link>
                <Link
                  href="/register"
                  className={cn(
                    'inline-flex min-h-11 items-center rounded-md bg-navy-900 px-4 text-sm font-medium text-warm-50 hover:bg-navy-800',
                    focusRing,
                  )}
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
        {children}
      </main>
      <footer className="border-t border-warm-200 bg-warm-100">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-4 text-sm text-charcoal-700">
          <p>KYRO sandbox. Fictional data only. No payments are taken.</p>
          <Link href="/trust" className="inline-flex min-h-11 items-center underline">
            Privacy and safety
          </Link>
        </div>
      </footer>
    </div>
  );
}
