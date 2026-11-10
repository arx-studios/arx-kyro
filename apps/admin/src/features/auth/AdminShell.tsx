'use client';

import type { ReactNode } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Button, SandboxBanner, cn, focusRing } from '@kyro/design-system';
import { useLogout } from './useLogout';
import { useMe } from './useMe';

const NAV = [
  { href: '/queue', label: 'Verification queue' },
  { href: '/audit', label: 'Audit log' },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { user } = useMe();
  const pathname = usePathname() ?? '';
  const logout = useLogout();

  return (
    <div className="min-h-screen">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded-md focus:bg-white focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <SandboxBanner />
      <header className="border-b border-warm-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <div className="flex flex-wrap items-center gap-6">
            <p className="text-base font-semibold text-navy-900">
              KYRO <span className="font-normal text-charcoal-700">Operations — restricted</span>
            </p>
            <nav aria-label="Primary">
              <ul className="flex gap-2">
                {NAV.map((item) => {
                  const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        aria-current={active ? 'page' : undefined}
                        className={cn(
                          'inline-flex min-h-11 items-center rounded-md px-3 text-sm font-medium',
                          focusRing,
                          active ? 'bg-navy-100 text-navy-900' : 'text-charcoal-700 hover:bg-warm-100',
                        )}
                      >
                        {item.label}
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </nav>
          </div>
          <div className="flex items-center gap-3 text-sm text-charcoal-700">
            {user ? <span>{user.email}</span> : null}
            <Button variant="secondary" onClick={() => logout.mutate()} loading={logout.isPending}>
              Log out
            </Button>
          </div>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="mx-auto max-w-6xl px-4 py-6">
        {children}
      </main>
    </div>
  );
}
