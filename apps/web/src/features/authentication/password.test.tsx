import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let search = 'token=abc';
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(search),
}));

const POST = vi.fn();
vi.mock('@/lib/api', () => ({ api: { GET: vi.fn(), POST: (...a: unknown[]) => POST(...a) } }));

import { ChangePasswordForm } from './ChangePasswordForm';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { changePasswordSchema, resetPasswordSchema } from './passwordSchemas';
import { ResetPasswordForm } from './ResetPasswordForm';

function wrap(ui: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>);
}

const problem = (status: number, code: string) => ({
  response: new Response(null, { status }),
  error: { type: 'about:blank', title: code, status, code },
});

beforeEach(() => {
  POST.mockReset();
  search = 'token=abc';
});

describe('password schemas', () => {
  it('requires 12+ characters and a matching confirmation', () => {
    expect(resetPasswordSchema.safeParse({ newPassword: 'short', confirmPassword: 'short' }).success).toBe(false);
    expect(resetPasswordSchema.safeParse({ newPassword: 'long enough pass', confirmPassword: 'different one!!' }).success).toBe(false);
    expect(resetPasswordSchema.safeParse({ newPassword: 'long enough pass', confirmPassword: 'long enough pass' }).success).toBe(true);
  });
  it('change password must differ from the current one', () => {
    const r = changePasswordSchema.safeParse({ currentPassword: 'same password 1', newPassword: 'same password 1', confirmPassword: 'same password 1' });
    expect(r.success).toBe(false);
  });
});

describe('ForgotPasswordForm', () => {
  it('shows the same neutral message on success and on server errors', async () => {
    const user = userEvent.setup();
    POST.mockResolvedValueOnce(problem(500, 'INTERNAL_ERROR'));
    wrap(<ForgotPasswordForm />);
    await user.type(screen.getByLabelText(/email/i), 'nobody@kyro.local');
    await user.click(screen.getByRole('button', { name: /send reset link/i }));
    expect(await screen.findByText(/if an account exists/i)).toBeInTheDocument();
    expect(POST).toHaveBeenCalledWith('/api/v1/auth/password-reset', { body: { email: 'nobody@kyro.local' } });
  });
});

describe('ResetPasswordForm', () => {
  it('shows an invalid-link message with no token', () => {
    search = '';
    wrap(<ResetPasswordForm />);
    expect(screen.getByText(/not valid/i)).toBeInTheDocument();
  });

  it('does not call the API on invalid input', async () => {
    const user = userEvent.setup();
    wrap(<ResetPasswordForm />);
    await user.type(screen.getByLabelText(/^new password/i), 'short');
    await user.click(screen.getByRole('button', { name: /set new password/i }));
    expect(await screen.findByText(/use at least 12 characters/i)).toBeInTheDocument();
    expect(POST).not.toHaveBeenCalled();
  });

  it('maps TOKEN_INVALID and shows success otherwise', async () => {
    const user = userEvent.setup();
    POST.mockResolvedValueOnce(problem(422, 'TOKEN_INVALID'));
    wrap(<ResetPasswordForm />);
    await user.type(screen.getByLabelText(/^new password/i), 'long enough pass');
    await user.type(screen.getByLabelText(/confirm new password/i), 'long enough pass');
    await user.click(screen.getByRole('button', { name: /set new password/i }));
    expect(await screen.findByText(/invalid, expired or has already been used/i)).toBeInTheDocument();
    POST.mockResolvedValueOnce({ response: new Response(null, { status: 204 }), data: undefined });
    await user.click(screen.getByRole('button', { name: /set new password/i }));
    expect(await screen.findByText(/password updated/i)).toBeInTheDocument();
    expect(POST).toHaveBeenLastCalledWith('/api/v1/auth/password-reset/confirm', {
      body: { token: 'abc', newPassword: 'long enough pass' },
    });
  });
});

describe('ChangePasswordForm', () => {
  async function fill(user: ReturnType<typeof userEvent.setup>) {
    await user.type(screen.getByLabelText(/current password/i), 'old password 123');
    await user.type(screen.getByLabelText(/^new password/i), 'brand new password');
    await user.type(screen.getByLabelText(/confirm new password/i), 'brand new password');
    await user.click(screen.getByRole('button', { name: /change password/i }));
  }

  it('maps CURRENT_PASSWORD_INCORRECT onto the current password field', async () => {
    const user = userEvent.setup();
    POST.mockResolvedValueOnce(problem(422, 'CURRENT_PASSWORD_INCORRECT'));
    wrap(<ChangePasswordForm />);
    await fill(user);
    expect(await screen.findByText('Your current password is incorrect.')).toBeInTheDocument();
    expect(screen.getByLabelText(/current password/i)).toHaveAttribute('aria-invalid', 'true');
  });

  it('confirms and mentions other sessions being signed out', async () => {
    const user = userEvent.setup();
    POST.mockResolvedValueOnce({ response: new Response(null, { status: 204 }), data: undefined });
    wrap(<ChangePasswordForm />);
    await fill(user);
    await waitFor(() => expect(screen.getByText(/other sessions on other devices have been signed out/i)).toBeInTheDocument());
    expect(POST).toHaveBeenCalledWith('/api/v1/me/password', {
      body: { currentPassword: 'old password 123', newPassword: 'brand new password' },
    });
  });
});
