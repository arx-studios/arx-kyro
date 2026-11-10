import { minorUnitDigits, parseMoneyInput } from '@kyro/design-system';
import type { EducationRequest, EducationRequestCreate, EducationRequestUpdate, ExpenseType } from '@kyro/api-client';
import { z } from 'zod';

/** Currency accepted by the slice (server rejects others with UNSUPPORTED_CURRENCY). */
export const CURRENCY = 'INR';

export const EXPENSE_TYPES: { value: ExpenseType; label: string }[] = [
  { value: 'TUITION', label: 'Tuition fee' },
  { value: 'EXAMINATION_FEE', label: 'Examination fee' },
  { value: 'COURSE_MATERIALS', label: 'Course materials' },
  { value: 'OTHER_ACADEMIC', label: 'Other academic expense' },
];

const optionalText = (max: number) => z.string().trim().max(max, `Use at most ${max} characters.`);

export const requestFormSchema = z
  .object({
    title: z.string().trim().min(5, 'Enter at least 5 characters.').max(120, 'Use at most 120 characters.'),
    expenseType: z.enum(['TUITION', 'EXAMINATION_FEE', 'COURSE_MATERIALS', 'OTHER_ACADEMIC'], {
      message: 'Choose the type of expense.',
    }),
    amount: z.string(),
    story: optionalText(2000),
    institutionName: optionalText(200),
    institutionCity: optionalText(100),
    institutionCountry: z
      .string()
      .trim()
      .refine((v) => v === '' || /^[A-Z]{2}$/.test(v), 'Use a 2-letter country code, for example IN.'),
    courseName: optionalText(200),
    feeReference: optionalText(100),
    dueDate: z
      .string()
      .trim()
      .refine((v) => v === '' || /^\d{4}-\d{2}-\d{2}$/.test(v), 'Enter a valid date.'),
  })
  .superRefine((v, ctx) => {
    const parsed = parseMoneyInput(v.amount, CURRENCY);
    if (!parsed.ok) {
      ctx.addIssue({ code: 'custom', path: ['amount'], message: parsed.message });
    } else if (parsed.amountMinor < 1) {
      ctx.addIssue({ code: 'custom', path: ['amount'], message: 'Amount must be greater than zero.' });
    }
  });

export type RequestFormValues = z.infer<typeof requestFormSchema>;

export const EMPTY_REQUEST_FORM: RequestFormValues = {
  title: '',
  expenseType: 'TUITION',
  amount: '',
  story: '',
  institutionName: '',
  institutionCity: '',
  institutionCountry: 'IN',
  courseName: '',
  feeReference: '',
  dueDate: '',
};

function optionalFields(values: RequestFormValues) {
  const out: { [K in 'story' | 'institutionName' | 'institutionCity' | 'courseName' | 'feeReference' | 'dueDate']?: string } = {};
  if (values.story) out.story = values.story;
  if (values.institutionName) out.institutionName = values.institutionName;
  if (values.institutionCity) out.institutionCity = values.institutionCity;
  if (values.courseName) out.courseName = values.courseName;
  if (values.feeReference) out.feeReference = values.feeReference;
  if (values.dueDate) out.dueDate = values.dueDate;
  return out;
}

function amountMinorOf(values: RequestFormValues): number {
  const parsed = parseMoneyInput(values.amount, CURRENCY);
  if (!parsed.ok) throw new Error(parsed.message);
  return parsed.amountMinor;
}

export function toCreatePayload(values: RequestFormValues): EducationRequestCreate {
  return {
    title: values.title,
    expenseType: values.expenseType,
    amount: { amountMinor: amountMinorOf(values), currency: CURRENCY },
    institutionCountry: values.institutionCountry || 'IN',
    ...optionalFields(values),
  };
}

export function toUpdatePayload(values: RequestFormValues, version: number): EducationRequestUpdate {
  return {
    title: values.title,
    expenseType: values.expenseType,
    amount: { amountMinor: amountMinorOf(values), currency: CURRENCY },
    institutionCountry: values.institutionCountry || 'IN',
    ...optionalFields(values),
    version,
  };
}

/** Integer minor units to the plain decimal string shown in the amount input (no floats). */
export function minorToInputString(amountMinor: number, currency: string): string {
  const digits = minorUnitDigits(currency);
  const s = String(Math.trunc(amountMinor)).padStart(digits + 1, '0');
  if (digits === 0) return s;
  const whole = s.slice(0, -digits);
  const frac = s.slice(-digits);
  return /^0+$/.test(frac) ? whole : `${whole}.${frac}`;
}

export function requestToFormValues(r: EducationRequest): RequestFormValues {
  return {
    title: r.title,
    expenseType: r.expenseType,
    amount: minorToInputString(r.amount.amountMinor, r.amount.currency),
    story: r.story ?? '',
    institutionName: r.institutionName ?? '',
    institutionCity: r.institutionCity ?? '',
    institutionCountry: r.institutionCountry ?? 'IN',
    courseName: r.courseName ?? '',
    feeReference: r.feeReference ?? '',
    dueDate: r.dueDate ?? '',
  };
}

/** Maps a server field path (e.g. `amount.amountMinor`) to a form field. */
export function serverFieldToFormField(field: string): keyof RequestFormValues | undefined {
  const head = field.split('.')[0] as string;
  if (head === 'amount') return 'amount';
  return head in EMPTY_REQUEST_FORM ? (head as keyof RequestFormValues) : undefined;
}
