'use client';

import { Alert, Button } from '@kyro/design-system';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { errorMessage } from '@/lib/errors';
import { useResendVerification } from './hooks';

export function CheckEmail() {
  const email = useSearchParams().get('email') ?? '';
  const resend = useResendVerification();

  return (
    <div className="flex flex-col gap-4">
      <p className="text-charcoal-700">
        {email ? (
          <>
            If <strong>{email}</strong> can be registered, we have sent a verification link to it.
          </>
        ) : (
          <>We have sent a verification link if the address can be registered.</>
        )}{' '}
        Open the link to activate your account. In this sandbox, emails are captured by Mailpit at{' '}
        <span className="font-mono">localhost:8025</span>.
      </p>
      {resend.isSuccess ? <Alert tone="success">Another email is on its way if the address can be registered.</Alert> : null}
      {resend.isError ? <Alert tone="danger">{errorMessage(resend.error)}</Alert> : null}
      <div className="flex flex-wrap gap-3">
        {email ? (
          <Button variant="secondary" loading={resend.isPending} onClick={() => resend.mutate(email)}>
            Resend verification email
          </Button>
        ) : null}
        <Link href="/login" className="inline-flex min-h-11 items-center underline">
          Go to log in
        </Link>
      </div>
    </div>
  );
}
