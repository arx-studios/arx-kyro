import { z } from 'zod';

/** Password rules mirror packages/api-spec/openapi.yaml (12 to 128 characters). The server stays authoritative. */
const newPassword = z.string().min(12, 'Use at least 12 characters.').max(128, 'Use at most 128 characters.');

export const forgotPasswordSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, 'Enter your email address.')
    .max(254, 'Email is too long.')
    .pipe(z.email('Enter a valid email address.')),
});
export type ForgotPasswordValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({ newPassword, confirmPassword: z.string() })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match.',
  });
export type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;

export const changePasswordSchema = z
  .object({ currentPassword: z.string().min(1, 'Enter your current password.'), newPassword, confirmPassword: z.string() })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'The passwords do not match.',
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ['newPassword'],
    message: 'Choose a password different from your current one.',
  });
export type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
