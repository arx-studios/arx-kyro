import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

const GET = vi.fn();
const POST = vi.fn();
vi.mock('@/lib/api', () => ({ api: { GET: (...a: unknown[]) => GET(...a), POST: (...a: unknown[]) => POST(...a) } }));

import { RegisterForm } from './RegisterForm';
import { registerSchema } from './schemas';

function renderForm() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RegisterForm />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  push.mockReset();
  GET.mockReset();
  POST.mockReset();
  GET.mockResolvedValue({
    response: new Response(null, { status: 200 }),
    data: { termsVersion: '2026-01', privacyVersion: '2026-02' },
  });
});

describe('registerSchema', () => {
  const ok = {
    email: 'a@kyro.local',
    password: 'correct horse battery',
    displayName: 'Asha Rao',
    publicDisplayName: 'Asha',
    countryCode: 'IN',
    recipient: true,
    donor: false,
    acceptTerms: true,
    acceptPrivacy: true,
  };
  it('accepts a valid payload', () => {
    expect(registerSchema.safeParse(ok).success).toBe(true);
  });
  it('mirrors the spec limits', () => {
    expect(registerSchema.safeParse({ ...ok, email: 'nope' }).success).toBe(false);
    expect(registerSchema.safeParse({ ...ok, password: 'short' }).success).toBe(false);
    expect(registerSchema.safeParse({ ...ok, publicDisplayName: 'x'.repeat(41) }).success).toBe(false);
    expect(registerSchema.safeParse({ ...ok, countryCode: 'in' }).success).toBe(false);
    expect(registerSchema.safeParse({ ...ok, recipient: false, donor: false }).success).toBe(false);
    expect(registerSchema.safeParse({ ...ok, acceptTerms: false }).success).toBe(false);
  });
});

describe('RegisterForm', () => {
  it('shows validation errors and does not call the API', async () => {
    const user = userEvent.setup();
    renderForm();
    await screen.findByText(/version 2026-01/);
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Enter your email address.')).toBeInTheDocument();
    expect(screen.getByText('Use at least 12 characters.')).toBeInTheDocument();
    expect(screen.getByText(/Choose at least one/)).toBeInTheDocument();
    expect(screen.getByText('You must accept the Terms to continue.')).toBeInTheDocument();
    expect(POST).not.toHaveBeenCalled();
  });

  it('submits with consent versions from /policies/current and moves to check-email', async () => {
    POST.mockResolvedValue({ response: new Response(null, { status: 202 }), data: { message: 'ok' } });
    const user = userEvent.setup();
    renderForm();
    await screen.findByText(/version 2026-01/);
    await user.type(screen.getByLabelText(/^Email/), 'asha@kyro.local');
    await user.type(screen.getByLabelText(/^Password/), 'correct horse battery');
    await user.type(screen.getByLabelText(/^Your name/), 'Asha Rao');
    await user.type(screen.getByLabelText(/^Public display name/), 'Asha');
    await user.click(screen.getByLabelText(/I need support/));
    await user.click(screen.getByLabelText(/Terms of Use/));
    await user.click(screen.getByLabelText(/Privacy Policy/));
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    await waitFor(() => expect(POST).toHaveBeenCalledTimes(1));
    expect(POST.mock.calls[0]![0]).toBe('/api/v1/auth/register');
    expect(POST.mock.calls[0]![1].body).toEqual({
      email: 'asha@kyro.local',
      password: 'correct horse battery',
      displayName: 'Asha Rao',
      publicDisplayName: 'Asha',
      countryCode: 'IN',
      roles: ['RECIPIENT'],
      consents: { termsVersion: '2026-01', privacyVersion: '2026-02' },
    });
    await waitFor(() => expect(push).toHaveBeenCalledWith('/register/check-email?email=asha%40kyro.local'));
  });

  it('maps server field errors onto the form', async () => {
    POST.mockResolvedValue({
      response: new Response(null, { status: 422 }),
      error: {
        type: 'x',
        title: 'Validation failed',
        status: 422,
        code: 'VALIDATION_FAILED',
        errors: [{ field: 'password', message: 'is too common' }],
      },
    });
    const user = userEvent.setup();
    renderForm();
    await screen.findByText(/version 2026-01/);
    await user.type(screen.getByLabelText(/^Email/), 'asha@kyro.local');
    await user.type(screen.getByLabelText(/^Password/), 'passwordpassword');
    await user.type(screen.getByLabelText(/^Your name/), 'Asha Rao');
    await user.type(screen.getByLabelText(/^Public display name/), 'Asha');
    await user.click(screen.getByLabelText(/I want to help/));
    await user.click(screen.getByLabelText(/Terms of Use/));
    await user.click(screen.getByLabelText(/Privacy Policy/));
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('is too common')).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
  });
});
