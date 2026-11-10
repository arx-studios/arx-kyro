import type { Metadata } from 'next';
import { Suspense } from 'react';
import { LoginForm } from '@/features/authentication/LoginForm';

export const metadata: Metadata = { title: 'Log in' };

export default function LoginPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Log in</h1>
      <Suspense fallback={null}>
        <LoginForm />
      </Suspense>
    </>
  );
}
