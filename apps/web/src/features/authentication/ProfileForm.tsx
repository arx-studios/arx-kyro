'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input } from '@kyro/design-system';
import type { CurrentUser, UpdateProfileRequest } from '@kyro/api-client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyServerErrors, errorMessage, problemFieldErrors } from '@/lib/errors';
import { useUpdateProfile } from './hooks';
import { profileSchema, type ProfileFormValues } from './schemas';

export function ProfileForm({ user }: { user: CurrentUser }) {
  const update = useUpdateProfile();
  const [status, setStatus] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);
  const { register, handleSubmit, formState, setError } = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      displayName: user.displayName,
      recipientPublicDisplayName: user.recipientProfile?.publicDisplayName ?? '',
      donorPublicDisplayName: user.donorProfile?.publicDisplayName ?? '',
    },
  });
  const { errors } = formState;

  async function onSubmit(values: ProfileFormValues) {
    setStatus(null);
    const body: UpdateProfileRequest = { displayName: values.displayName.trim() };
    if (user.recipientProfile && values.recipientPublicDisplayName) {
      body.recipientPublicDisplayName = values.recipientPublicDisplayName.trim();
    }
    if (user.donorProfile && values.donorPublicDisplayName) {
      body.donorPublicDisplayName = values.donorPublicDisplayName.trim();
    }
    try {
      await update.mutateAsync(body);
      setStatus({ tone: 'success', message: 'Profile saved.' });
    } catch (err) {
      const unmapped = applyServerErrors(err, setError, (f) =>
        f === 'displayName' || f === 'recipientPublicDisplayName' || f === 'donorPublicDisplayName' ? f : undefined,
      );
      if (unmapped.length > 0 || problemFieldErrors(err).length === 0) {
        setStatus({ tone: 'danger', message: errorMessage(err) });
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-5" aria-label="Edit profile">
      {status ? <Alert tone={status.tone}>{status.message}</Alert> : null}
      <Field label="Email" hint="Your email address cannot be changed here.">
        {(p) => <Input {...p} value={user.email} readOnly disabled />}
      </Field>
      <Field label="Your name" required error={errors.displayName?.message}>
        {(p) => <Input {...p} autoComplete="name" {...register('displayName')} />}
      </Field>
      {user.recipientProfile ? (
        <Field
          label="Public display name (as a recipient)"
          hint="Donors see this instead of your real name."
          error={errors.recipientPublicDisplayName?.message}
        >
          {(p) => <Input {...p} {...register('recipientPublicDisplayName')} />}
        </Field>
      ) : null}
      {user.donorProfile ? (
        <Field label="Public display name (as a donor)" error={errors.donorPublicDisplayName?.message}>
          {(p) => <Input {...p} {...register('donorPublicDisplayName')} />}
        </Field>
      ) : null}
      <Button type="submit" loading={formState.isSubmitting}>
        Save profile
      </Button>
    </form>
  );
}
