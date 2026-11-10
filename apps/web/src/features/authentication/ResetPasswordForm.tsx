'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { ApiError } from '@kyro/api-client';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyServerErrors, errorMessage } from '@/lib/errors';
import { useConfirmPasswordReset } from './passwordHooks';
import { resetPasswordSchema, type ResetPasswordValues } from './passwordSchemas';

function InvalidLink() {
  return (
    <Alert tone="danger" title="This reset link is not valid">
      Open the link from your email again, or{' '}
      <Link className="font-medium underline" href="/forgot-password">
        request a new one
      </Link>
      .
    </Alert>
  );
}

export function ResetPasswordForm() {
  const token = useSearchParams().get('token') ?? '';
  const confirm = useConfirmPasswordReset();
  const [done, setDone] = useState(false);
  const [formError, setFormError] = useState<{ message: string; invalidToken: boolean } | null>(null);
  const { register, handleSubmit, formState, setError } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });
  const { errors } = formState;

  if (!token) return <InvalidLink />;
  if (done) {
    return (
      <div className="flex flex-col gap-5">
        <Alert tone="success" title="Password updated">
          Your password has been changed and you were signed out everywhere. Log in with your new password.
        </Alert>
        <Link href="/login" className="inline-flex min-h-11 items-center font-medium text-navy-800 underline">
          Go to log in
        </Link>
      </div>
    );
  }

  async function onSubmit(values: ResetPasswordValues) {
    setFormError(null);
    try {
      await confirm.mutateAsync({ token, newPassword: values.newPassword });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'TOKEN_INVALID') {
        setFormError({ message: 'This reset link is invalid, expired or has already been used.', invalidToken: true });
        return;
      }
      const unmapped = applyServerErrors(err, setError, (f) => (f === 'newPassword' ? 'newPassword' : undefined));
      if (unmapped.length > 0 || !(err instanceof ApiError) || (err.problem.errors ?? []).length === 0) {
        setFormError({ message: errorMessage(err), invalidToken: false });
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Reset password">
      {formError ? (
        <Alert tone="danger">
          {formError.message}
          {formError.invalidToken ? (
            <>
              {' '}
              <Link className="font-medium underline" href="/forgot-password">
                Request a new link
              </Link>
            </>
          ) : null}
        </Alert>
      ) : null}
      <Field label="New password" required hint="At least 12 characters." error={errors.newPassword?.message}>
        {(p) => <Input {...p} type="password" autoComplete="new-password" {...register('newPassword')} />}
      </Field>
      <Field label="Confirm new password" required error={errors.confirmPassword?.message}>
        {(p) => <Input {...p} type="password" autoComplete="new-password" {...register('confirmPassword')} />}
      </Field>
      <Button type="submit" size="lg" loading={formState.isSubmitting}>
        Set new password
      </Button>
    </form>
  );
}
