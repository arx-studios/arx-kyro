'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input, Select, Spinner } from '@kyro/design-system';
import type { RegisterRequest } from '@kyro/api-client';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyServerErrors, errorMessage, problemFieldErrors } from '@/lib/errors';
import { usePolicies, useRegister } from './hooks';
import { COUNTRIES, registerSchema, type RegisterFormValues } from './schemas';

const SERVER_FIELD_MAP: Record<string, keyof RegisterFormValues> = {
  email: 'email',
  password: 'password',
  displayName: 'displayName',
  publicDisplayName: 'publicDisplayName',
  countryCode: 'countryCode',
  roles: 'recipient',
};

export function RegisterForm() {
  const router = useRouter();
  const policies = usePolicies();
  const register_ = useRegister();
  const [formError, setFormError] = useState<string | null>(null);

  const form = useForm<RegisterFormValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: {
      email: '',
      password: '',
      displayName: '',
      publicDisplayName: '',
      countryCode: 'IN',
      recipient: false,
      donor: false,
      acceptTerms: false,
      acceptPrivacy: false,
    },
  });
  const { register, handleSubmit, formState, setError } = form;
  const { errors } = formState;

  async function onSubmit(values: RegisterFormValues) {
    setFormError(null);
    if (!policies.data) {
      setFormError('Policy versions are still loading. Please try again in a moment.');
      return;
    }
    const roles: RegisterRequest['roles'] = [];
    if (values.recipient) roles.push('RECIPIENT');
    if (values.donor) roles.push('DONOR');
    const body: RegisterRequest = {
      email: values.email.trim(),
      password: values.password,
      displayName: values.displayName.trim(),
      publicDisplayName: values.publicDisplayName.trim(),
      countryCode: values.countryCode,
      roles,
      consents: { termsVersion: policies.data.termsVersion, privacyVersion: policies.data.privacyVersion },
    };
    try {
      await register_.mutateAsync(body);
      router.push(`/register/check-email?email=${encodeURIComponent(body.email)}`);
    } catch (err) {
      const unmapped = applyServerErrors(err, setError, (f) => SERVER_FIELD_MAP[f.split('.')[0] ?? '']);
      const total = problemFieldErrors(err).length;
      setFormError(total > 0 && unmapped.length === 0 ? null : errorMessage(err));
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Create your account">
      {formError ? <Alert tone="danger">{formError}</Alert> : null}

      <Field label="Email" required error={errors.email?.message}>
        {(p) => <Input {...p} type="email" autoComplete="email" inputMode="email" {...register('email')} />}
      </Field>
      <Field label="Password" required hint="At least 12 characters." error={errors.password?.message}>
        {(p) => <Input {...p} type="password" autoComplete="new-password" {...register('password')} />}
      </Field>
      <Field label="Your name" required error={errors.displayName?.message}>
        {(p) => <Input {...p} autoComplete="name" {...register('displayName')} />}
      </Field>
      <Field
        label="Public display name"
        required
        hint="Shown to others instead of your real name. Use a first name or nickname."
        error={errors.publicDisplayName?.message}
      >
        {(p) => <Input {...p} autoComplete="nickname" {...register('publicDisplayName')} />}
      </Field>
      <Field label="Country" required error={errors.countryCode?.message}>
        {(p) => (
          <Select {...p} autoComplete="country" {...register('countryCode')}>
            {COUNTRIES.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))}
          </Select>
        )}
      </Field>

      <fieldset className="flex flex-col gap-2" aria-describedby={errors.recipient ? 'roles-error' : undefined}>
        <legend className="text-sm font-medium text-charcoal-900">
          How will you use KYRO? <span aria-hidden="true" className="text-danger-600">*</span>
        </legend>
        <label className="flex min-h-11 items-center gap-3">
          <input type="checkbox" className="size-5 accent-navy-900" {...register('recipient')} />
          <span>I need support with a verified education expense</span>
        </label>
        <label className="flex min-h-11 items-center gap-3">
          <input type="checkbox" className="size-5 accent-navy-900" {...register('donor')} />
          <span>I want to help fund verified education expenses</span>
        </label>
        {errors.recipient?.message ? (
          <p id="roles-error" role="alert" className="text-sm font-medium text-danger-700">
            {errors.recipient.message}
          </p>
        ) : null}
      </fieldset>

      <fieldset className="flex flex-col gap-2">
        <legend className="text-sm font-medium text-charcoal-900">Consents</legend>
        {policies.isPending ? <Spinner label="Loading policy versions" /> : null}
        {policies.isError ? (
          <Alert tone="warning">We could not load the current policy versions. Reload the page to try again.</Alert>
        ) : null}
        <label className="flex min-h-11 items-center gap-3">
          <input
            type="checkbox"
            className="size-5 accent-navy-900"
            aria-invalid={errors.acceptTerms ? true : undefined}
            {...register('acceptTerms')}
          />
          <span>
            I accept the <Link href="/trust#terms" className="underline">Terms of Use</Link>
            {policies.data ? ` (version ${policies.data.termsVersion})` : ''}
          </span>
        </label>
        {errors.acceptTerms?.message ? (
          <p role="alert" className="text-sm font-medium text-danger-700">{errors.acceptTerms.message}</p>
        ) : null}
        <label className="flex min-h-11 items-center gap-3">
          <input
            type="checkbox"
            className="size-5 accent-navy-900"
            aria-invalid={errors.acceptPrivacy ? true : undefined}
            {...register('acceptPrivacy')}
          />
          <span>
            I accept the <Link href="/trust#privacy" className="underline">Privacy Policy</Link>
            {policies.data ? ` (version ${policies.data.privacyVersion})` : ''}
          </span>
        </label>
        {errors.acceptPrivacy?.message ? (
          <p role="alert" className="text-sm font-medium text-danger-700">{errors.acceptPrivacy.message}</p>
        ) : null}
      </fieldset>

      <Button type="submit" size="lg" loading={formState.isSubmitting} disabled={policies.isPending}>
        Create account
      </Button>
      <p className="text-sm text-charcoal-700">
        Already registered? <Link href="/login" className="font-medium text-navy-800 underline">Log in</Link>
      </p>
    </form>
  );
}
