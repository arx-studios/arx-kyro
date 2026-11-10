import { z } from 'zod';

/** Zod schemas mirroring packages/api-spec/openapi.yaml constraints. The server stays authoritative. */

export const COUNTRIES = [
  { code: 'IN', name: 'India' },
  { code: 'US', name: 'United States' },
  { code: 'GB', name: 'United Kingdom' },
  { code: 'CA', name: 'Canada' },
  { code: 'AU', name: 'Australia' },
  { code: 'SG', name: 'Singapore' },
  { code: 'AE', name: 'United Arab Emirates' },
  { code: 'DE', name: 'Germany' },
] as const;

export const registerSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.').max(254, 'Email is too long.').pipe(z.email('Enter a valid email address.')),
  password: z
    .string()
    .min(12, 'Use at least 12 characters.')
    .max(128, 'Use at most 128 characters.'),
  displayName: z.string().trim().min(2, 'Enter at least 2 characters.').max(80, 'Use at most 80 characters.'),
  publicDisplayName: z
    .string()
    .trim()
    .min(2, 'Enter at least 2 characters.')
    .max(40, 'Use at most 40 characters.'),
  countryCode: z.string().regex(/^[A-Z]{2}$/, 'Choose your country.'),
  recipient: z.boolean(),
  donor: z.boolean(),
  acceptTerms: z.boolean().refine((v) => v, 'You must accept the Terms to continue.'),
  acceptPrivacy: z.boolean().refine((v) => v, 'You must accept the Privacy Policy to continue.'),
}).refine((v) => v.recipient || v.donor, {
  path: ['recipient'],
  message: 'Choose at least one: I need support, or I want to give.',
});

export type RegisterFormValues = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.').pipe(z.email('Enter a valid email address.')),
  password: z.string().min(1, 'Enter your password.'),
});

export type LoginFormValues = z.infer<typeof loginSchema>;

export const profileSchema = z.object({
  displayName: z.string().trim().min(2, 'Enter at least 2 characters.').max(80, 'Use at most 80 characters.'),
  recipientPublicDisplayName: z.string().trim().max(40, 'Use at most 40 characters.').refine((v) => v === '' || v.length >= 2, 'Enter at least 2 characters.'),
  donorPublicDisplayName: z.string().trim().max(40, 'Use at most 40 characters.').refine((v) => v === '' || v.length >= 2, 'Enter at least 2 characters.'),
});

export type ProfileFormValues = z.infer<typeof profileSchema>;
