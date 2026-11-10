import type { Metadata } from 'next';
import { Suspense } from 'react';
import { Card, SandboxBanner } from '@kyro/design-system';
import { LoginForm } from '@/features/auth/LoginForm';

export const metadata: Metadata = { title: 'Sign in' };

export default function LoginPage() {
  return (
    <>
      <SandboxBanner />
      <main id="main" className="mx-auto max-w-md px-4 py-12">
        <h1 className="mb-1 text-2xl font-semibold text-navy-900">KYRO Operations</h1>
        <p className="mb-6 text-sm text-charcoal-700">Restricted area. Verifier and admin accounts only.</p>
        <Card className="p-6">
          <Suspense fallback={null}>
            <LoginForm />
          </Suspense>
        </Card>
      </main>
    </>
  );
}
