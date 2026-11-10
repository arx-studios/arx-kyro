import type { ReactNode } from 'react';
import { AdminShell } from '@/features/auth/AdminShell';
import { RoleGuard } from '@/features/auth/RoleGuard';

export default function ConsoleLayout({ children }: { children: ReactNode }) {
  return (
    <RoleGuard>
      <AdminShell>{children}</AdminShell>
    </RoleGuard>
  );
}
