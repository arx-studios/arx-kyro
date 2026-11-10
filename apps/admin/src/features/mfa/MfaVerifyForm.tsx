'use client';

import { useState, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useQueryClient } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import { api } from '@/lib/api';
import { safeNext } from '@/lib/decision';
import { RECOVERY_CODE_PATTERN, TOTP_PATTERN, normaliseRecoveryCode } from '@/lib/mfa';
import { describeError, type FriendlyProblem } from '@/lib/problems';
import { queryKeys } from '@/lib/queryKeys';

export function MfaVerifyForm() {
  const router = useRouter();
  const params = useSearchParams();
  const queryClient = useQueryClient();
  const [useRecovery, setUseRecovery] = useState(false);
  const [value, setValue] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);
  const [pending, setPending] = useState(false);

  function toggle() {
    setUseRecovery((v) => !v);
    setValue('');
    setFieldError(undefined);
    setProblem(null);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const code = useRecovery ? normaliseRecoveryCode(value) : value.trim();
    const valid = useRecovery ? RECOVERY_CODE_PATTERN.test(code) : TOTP_PATTERN.test(code);
    if (!valid) {
      setFieldError(
        useRecovery ? 'Enter a recovery code like ABCDE-12345.' : 'Enter the 6-digit code from your authenticator app.',
      );
      return;
    }
    setFieldError(undefined);
    setProblem(null);
    setPending(true);
    try {
      const user = unwrap(
        await api.POST('/api/v1/auth/mfa/verify', { body: useRecovery ? { recoveryCode: code } : { code } }),
      );
      queryClient.setQueryData(queryKeys.me, user);
      router.replace(safeNext(params?.get('next') ?? '/'));
    } catch (err) {
      setProblem(describeError(err));
      setValue('');
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate className="space-y-4" aria-label="Verify two-step code">
      <p className="text-sm text-charcoal-700">
        {useRecovery
          ? 'Enter one of your unused recovery codes. Each code works once.'
          : 'Enter the 6-digit code from your authenticator app to continue.'}
      </p>
      {problem ? (
        <Alert tone={problem.tone} title={problem.title}>
          <p>{problem.message}</p>
        </Alert>
      ) : null}
      <Field
        label={useRecovery ? 'Recovery code' : 'Authenticator code'}
        required
        error={fieldError}
        hint={useRecovery ? 'Format: ABCDE-12345' : undefined}
      >
        <Input
          key={useRecovery ? 'recovery' : 'totp'}
          type="text"
          inputMode={useRecovery ? 'text' : 'numeric'}
          autoComplete="one-time-code"
          autoCapitalize={useRecovery ? 'characters' : undefined}
          spellCheck={false}
          maxLength={useRecovery ? 11 : 6}
          value={value}
          onChange={(e) => setValue(e.target.value)}
        />
      </Field>
      <Button type="submit" loading={pending} className="w-full">
        Verify
      </Button>
      <p className="text-sm">
        <button type="button" onClick={toggle} className="rounded-sm text-navy-800 underline underline-offset-2">
          {useRecovery ? 'Use an authenticator code instead' : 'Use a recovery code'}
        </button>
      </p>
    </form>
  );
}
