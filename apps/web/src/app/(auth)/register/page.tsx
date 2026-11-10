import type { Metadata } from 'next';
import { RegisterForm } from '@/features/authentication/RegisterForm';

export const metadata: Metadata = { title: 'Create your account' };

export default function RegisterPage() {
  return (
    <>
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Create your account</h1>
      <RegisterForm />
    </>
  );
}
