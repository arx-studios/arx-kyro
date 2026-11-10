'use client';

import { Alert, Spinner } from '@kyro/design-system';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useEffect, useRef } from 'react';
import { ApiError } from '@kyro/api-client';
import { errorMessage } from '@/lib/errors';
import { useVerifyEmail } from './hooks';

export function VerifyEmail() {
  const token = useSearchParams().get('token');
  const verify = useVerifyEmail();
  const started = useRef(false);
  const { mutate } = verify;

  useEffect(() => {
    if (token && !started.current) {
      started.current = true; // the token is single-use: never POST twice (React strict mode)
      mutate(token);
    }
  }, [token, mutate]);

  const loginLink = (
    <Link href="/login" className="inline-flex min-h-11 items-center font-medium underline">
      Continue to log in
    </Link>
  );

  if (!token) {
    return <Alert tone="danger">This verification link is missing its token. Open the link from your email again.</Alert>;
  }
  if (verify.isSuccess) {
    return (
      <div className="flex flex-col gap-3">
        <Alert tone="success">Your email address is verified. You can now log in.</Alert>
        {loginLink}
      </div>
    );
  }
  if (verify.isError) {
    const invalid = verify.error instanceof ApiError && verify.error.code === 'TOKEN_INVALID';
    return (
      <div className="flex flex-col gap-3">
        <Alert tone="danger">
          {invalid
            ? 'This verification link is invalid or has expired. Request a new one from the sign-up page.'
            : errorMessage(verify.error)}
        </Alert>
        <Link href="/login" className="inline-flex min-h-11 items-center underline">
          Go to log in
        </Link>
      </div>
    );
  }
  return <Spinner label="Verifying your email address" />;
}
