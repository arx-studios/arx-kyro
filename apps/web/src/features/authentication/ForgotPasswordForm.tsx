'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { ApiError } from '@kyro/api-client';
import Link from 'next/link';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useRequestPasswordReset } from './passwordHooks';
import { forgotPasswordSchema, type ForgotPasswordValues } from './passwordSchemas';

/** Always shows the same neutral confirmation, so the page cannot be used to discover accounts. */
export function ForgotPasswordForm() {
  const request = useRequestPasswordReset();
  const [done, setDone] = useState(false);
  const [rateLimited, setRateLimited] = useState(false);
  const { register, handleSubmit, formState } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: '' },
  });

  async function onSubmit(values: ForgotPasswordValues) {
    setRateLimited(false);
    try {
      await request.mutateAsync(values.email.trim());
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'RATE_LIMITED') setRateLimited(true);
      else setDone(true); // never reveal anything about the account, even on unexpected errors
    }
  }

  if (done) {
    return (
      <div className="flex flex-col gap-5">
        <Alert tone="success" title="Check your email">
          If an account exists for that address, we sent a link to reset your password. The link works once and expires soon.
        </Alert>
        <Link href="/login" className="inline-flex min-h-11 items-center font-medium text-navy-800 underline">
          Back to log in
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Forgot password">
      <p className="text-charcoal-700">Enter your email address and we will send you a link to choose a new password.</p>
      {rateLimited ? <Alert tone="danger">Too many attempts. Please wait a moment and try again.</Alert> : null}
      <Field label="Email" required error={formState.errors.email?.message}>
        {(p) => <Input {...p} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
      </Field>
      <Button type="submit" size="lg" loading={formState.isSubmitting}>
        Send reset link
      </Button>
      <Link href="/login" className="inline-flex min-h-11 items-center text-sm font-medium text-navy-800 underline">
        Back to log in
      </Link>
    </form>
  );
}
