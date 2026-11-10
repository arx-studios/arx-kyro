'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { ApiError, type CurrentUser } from '@kyro/api-client';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { errorMessage, safeNextPath } from '@/lib/errors';
import { useLogin } from './hooks';
import { loginSchema, type LoginFormValues } from './schemas';

export function defaultLanding(user: CurrentUser): string {
  if (user.roles.includes('RECIPIENT')) return '/recipient/dashboard';
  if (user.roles.includes('DONOR')) return '/discover';
  return '/';
}

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const next = params.get('next');
  const login = useLogin();
  const [formError, setFormError] = useState<{ message: string; unverified: boolean } | null>(null);

  const { register, handleSubmit, formState, getValues } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  });
  const { errors } = formState;

  async function onSubmit(values: LoginFormValues) {
    setFormError(null);
    try {
      const user = await login.mutateAsync({ email: values.email.trim(), password: values.password });
      router.replace(safeNextPath(next, defaultLanding(user)));
    } catch (err) {
      const unverified = err instanceof ApiError && err.code === 'EMAIL_NOT_VERIFIED';
      setFormError({
        message: unverified
          ? 'Please verify your email address first. Check your inbox for the verification link.'
          : err instanceof ApiError && err.code === 'INVALID_CREDENTIALS'
            ? 'Email or password is incorrect.'
            : errorMessage(err),
        unverified,
      });
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Log in">
      {formError ? (
        <Alert tone="danger">
          {formError.message}
          {formError.unverified ? (
            <>
              {' '}
              <Link
                className="font-medium underline"
                href={`/register/check-email?email=${encodeURIComponent(getValues('email').trim())}`}
              >
                Resend the verification email
              </Link>
            </>
          ) : null}
        </Alert>
      ) : null}
      <Field label="Email" required error={errors.email?.message}>
        {(p) => <Input {...p} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
      </Field>
      <Field label="Password" required error={errors.password?.message}>
        {(p) => <Input {...p} type="password" autoComplete="current-password" {...register('password')} />}
      </Field>
      <Button type="submit" size="lg" loading={formState.isSubmitting}>
        Log in
      </Button>
      <p className="text-sm text-charcoal-700">
        New to KYRO? <Link href="/register" className="font-medium text-navy-800 underline">Create an account</Link>
      </p>
    </form>
  );
}
