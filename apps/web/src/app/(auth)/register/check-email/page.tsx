import type { Metadata } from 'next';
import { Suspense } from 'react';
import { CheckEmail } from '@/features/authentication/CheckEmail';

export const metadata: Metadata = { title: 'Check your email' };

export default function CheckEmailPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Check your email</h1>
      <Suspense fallback={null}>
        <CheckEmail />
      </Suspense>
    </>
  );
}
