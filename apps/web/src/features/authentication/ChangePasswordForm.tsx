'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { ApiError } from '@kyro/api-client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyServerErrors, errorMessage, problemFieldErrors } from '@/lib/errors';
import { useChangePassword } from './passwordHooks';
import { changePasswordSchema, type ChangePasswordValues } from './passwordSchemas';

export function ChangePasswordForm() {
  const change = useChangePassword();
  const [status, setStatus] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);
  const { register, handleSubmit, formState, setError, reset } = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });
  const { errors } = formState;

  async function onSubmit(values: ChangePasswordValues) {
    setStatus(null);
    try {
      await change.mutateAsync({ currentPassword: values.currentPassword, newPassword: values.newPassword });
      reset();
      setStatus({
        tone: 'success',
        message: 'Password changed. Your other sessions on other devices have been signed out.',
      });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'CURRENT_PASSWORD_INCORRECT') {
        setError('currentPassword', { type: 'server', message: 'Your current password is incorrect.' });
        return;
      }
      const unmapped = applyServerErrors(err, setError, (f) =>
        f === 'currentPassword' || f === 'newPassword' ? f : undefined,
      );
      if (unmapped.length > 0 || problemFieldErrors(err).length === 0) {
        setStatus({ tone: 'danger', message: errorMessage(err) });
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Change password">
      {status ? <Alert tone={status.tone}>{status.message}</Alert> : null}
      <p className="text-sm text-charcoal-700">Changing your password signs you out of all your other sessions.</p>
      <Field label="Current password" required error={errors.currentPassword?.message}>
        {(p) => <Input {...p} type="password" autoComplete="current-password" {...register('currentPassword')} />}
      </Field>
      <Field label="New password" required hint="At least 12 characters." error={errors.newPassword?.message}>
        {(p) => <Input {...p} type="password" autoComplete="new-password" {...register('newPassword')} />}
      </Field>
      <Field label="Confirm new password" required error={errors.confirmPassword?.message}>
        {(p) => <Input {...p} type="password" autoComplete="new-password" {...register('confirmPassword')} />}
      </Field>
      <Button type="submit" loading={formState.isSubmitting}>
        Change password
      </Button>
    </form>
  );
}
