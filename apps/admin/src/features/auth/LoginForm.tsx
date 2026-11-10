'use client';

import { useState, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { api } from '@/lib/api';
import { safeNext } from '@/lib/decision';
import { describeError } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';

export function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const queryClient = useQueryClient();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<ReturnType<typeof describeError> | null>(null);
  const [fieldError, setFieldError] = useState<{ email?: string; password?: string }>({});

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: typeof fieldError = {};
    if (!email.trim()) next.email = 'Enter your email address.';
    if (!password) next.password = 'Enter your password.';
    setFieldError(next);
    if (next.email || next.password) return;

    setPending(true);
    setError(null);
    try {
      const user = unwrap(await api.POST('/api/v1/auth/session', { body: { email: email.trim(), password } }));
      queryClient.setQueryData(queryKeys.me, user);
      router.replace(safeNext(params?.get('next')));
    } catch (err) {
      const friendly = describeError(err);
      // Do not reveal whether the account exists.
      setError(
        friendly.title === 'Something went wrong' || friendly.title === 'Session expired'
          ? { ...friendly, title: 'Sign in failed', message: 'Email or password is incorrect.' }
          : friendly,
      );
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4" aria-label="Sign in">
      {error ? (
        <Alert tone={error.tone} title={error.title}>
          <p>{error.message}</p>
        </Alert>
      ) : null}
      <Field label="Email" required error={fieldError.email}>
        <Input
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </Field>
      <Field label="Password" required error={fieldError.password}>
        <Input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </Field>
      <Button type="submit" loading={pending} className="w-full">
        Sign in
      </Button>
    </form>
  );
}
