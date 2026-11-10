import type { Metadata } from 'next';
import { ForgotPasswordForm } from '@/features/authentication/ForgotPasswordForm';

export const metadata: Metadata = { title: 'Forgot password' };

export default function ForgotPasswordPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Forgot your password?</h1>
      <ForgotPasswordForm />
    </>
  );
}
