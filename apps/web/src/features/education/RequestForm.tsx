'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Button, Field, Input, Select, Textarea } from '@kyro/design-system';
import type { EducationRequest } from '@kyro/api-client';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyServerErrors, errorMessage, problemFieldErrors } from '@/lib/errors';
import {
  CURRENCY,
  EMPTY_REQUEST_FORM,
  EXPENSE_TYPES,
  requestFormSchema,
  requestToFormValues,
  serverFieldToFormField,
  type RequestFormValues,
} from './schemas';

export interface RequestFormProps {
  /** Existing request when editing; omit when creating. */
  initial?: EducationRequest;
  submitLabel: string;
  /** Throws on failure (an ApiError is mapped onto the fields). */
  onSubmit: (values: RequestFormValues) => Promise<unknown>;
  onSaved?: () => void;
}

export function RequestForm({ initial, submitLabel, onSubmit, onSaved }: RequestFormProps) {
  const [formError, setFormError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const { register, handleSubmit, formState, setError } = useForm<RequestFormValues>({
    resolver: zodResolver(requestFormSchema),
    defaultValues: initial ? requestToFormValues(initial) : EMPTY_REQUEST_FORM,
  });
  const { errors } = formState;

  async function submit(values: RequestFormValues) {
    setFormError(null);
    setSaved(false);
    try {
      await onSubmit(values);
      setSaved(true);
      onSaved?.();
    } catch (err) {
      const unmapped = applyServerErrors(err, setError, serverFieldToFormField);
      const total = problemFieldErrors(err).length;
      const message =
        (err as { code?: string }).code === 'STALE_VERSION'
          ? 'This request was changed elsewhere. Reload the page to see the latest version, then try again.'
          : errorMessage(err);
      setFormError(total > 0 && unmapped.length === 0 ? 'Please fix the highlighted fields.' : message);
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="flex flex-col gap-5" aria-label="Request details">
      {formError ? <Alert tone="danger">{formError}</Alert> : null}
      {saved ? <Alert tone="success">Saved.</Alert> : null}

      <Field label="Title" required hint="A short summary donors will see, for example: First-year engineering tuition." error={errors.title?.message}>
        {(p) => <Input {...p} maxLength={120} {...register('title')} />}
      </Field>
      <Field label="Type of expense" required error={errors.expenseType?.message}>
        {(p) => (
          <Select {...p} {...register('expenseType')}>
            {EXPENSE_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field
        label={`Amount (${CURRENCY})`}
        required
        hint="The exact amount on your bill, for example 12,500.50."
        error={errors.amount?.message}
      >
        {(p) => <Input {...p} inputMode="decimal" autoComplete="off" {...register('amount')} />}
      </Field>
      <Field label="Your story" hint="At least 50 characters are needed to submit. Do not include contact details." error={errors.story?.message}>
        {(p) => <Textarea {...p} rows={5} maxLength={2000} {...register('story')} />}
      </Field>
      <Field label="Institution name" error={errors.institutionName?.message}>
        {(p) => <Input {...p} maxLength={200} {...register('institutionName')} />}
      </Field>
      <div className="grid gap-5 sm:grid-cols-2">
        <Field label="Institution city" error={errors.institutionCity?.message}>
          {(p) => <Input {...p} maxLength={100} {...register('institutionCity')} />}
        </Field>
        <Field label="Institution country code" hint="2 letters, for example IN." error={errors.institutionCountry?.message}>
          {(p) => <Input {...p} maxLength={2} autoCapitalize="characters" {...register('institutionCountry')} />}
        </Field>
      </div>
      <Field label="Course" error={errors.courseName?.message}>
        {(p) => <Input {...p} maxLength={200} {...register('courseName')} />}
      </Field>
      <Field label="Fee reference" hint="Invoice or receipt number on the bill." error={errors.feeReference?.message}>
        {(p) => <Input {...p} maxLength={100} {...register('feeReference')} />}
      </Field>
      <Field label="Payment due date" error={errors.dueDate?.message}>
        {(p) => <Input {...p} type="date" {...register('dueDate')} />}
      </Field>

      <Button type="submit" size="lg" loading={formState.isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  );
}
