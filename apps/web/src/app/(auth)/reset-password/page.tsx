import type { Metadata } from 'next';
import { Suspense } from 'react';
import { ResetPasswordForm } from '@/features/authentication/ResetPasswordForm';

export const metadata: Metadata = { title: 'Reset password' };

export default function ResetPasswordPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Choose a new password</h1>
      <Suspense fallback={null}>
        <ResetPasswordForm />
      </Suspense>
    </>
  );
}
