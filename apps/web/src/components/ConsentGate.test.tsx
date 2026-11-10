import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let pathname = '/discover';
vi.mock('next/navigation', () => ({ usePathname: () => pathname }));

const GET = vi.fn();
const POST = vi.fn();
vi.mock('@/lib/api', () => ({ api: { GET: (...a: unknown[]) => GET(...a), POST: (...a: unknown[]) => POST(...a) } }));

import { ConsentGate, isAuthPath } from './ConsentGate';

const user = { id: 'u1', email: 'a@kyro.local', displayName: 'A', roles: ['DONOR'] };
const status = (reacceptRequired: boolean) => ({
  current: { termsVersion: '2026-12', privacyVersion: '2026-12' },
  acceptedTermsVersion: '2026-11',
  acceptedPrivacyVersion: '2026-11',
  reacceptRequired,
  history: [],
});
const okRes = (data: unknown) => ({ response: new Response(null, { status: 200 }), data });

function setup(me: unknown, consents: unknown) {
  GET.mockImplementation(async (path: string) =>
    path === '/api/v1/me' ? (me ? okRes(me) : { response: new Response(null, { status: 401 }) }) : okRes(consents),
  );
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <ConsentGate />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  GET.mockReset();
  POST.mockReset();
  pathname = '/discover';
});

describe('ConsentGate', () => {
  it('shows a blocking dialog when re-acceptance is required and accepts the current versions', async () => {
    const u = userEvent.setup();
    setup(user, status(true));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    POST.mockResolvedValueOnce(okRes(status(false)));
    await u.click(screen.getByRole('button', { name: 'Accept' }));
    expect(POST).toHaveBeenCalledWith('/api/v1/me/consents', {
      body: { termsVersion: '2026-12', privacyVersion: '2026-12' },
    });
    await vi.waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('stays hidden when consent is up to date', async () => {
    setup(user, status(false));
    await vi.waitFor(() => expect(GET).toHaveBeenCalledWith('/api/v1/me/consents'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('stays hidden when signed out and never asks for consents', async () => {
    setup(null, status(true));
    await vi.waitFor(() => expect(GET).toHaveBeenCalledWith('/api/v1/me'));
    expect(GET).not.toHaveBeenCalledWith('/api/v1/me/consents');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('is not shown on auth pages', async () => {
    pathname = '/login';
    setup(user, status(true));
    await vi.waitFor(() => expect(GET).toHaveBeenCalledWith('/api/v1/me'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(isAuthPath('/reset-password')).toBe(true);
    expect(isAuthPath('/recipient/dashboard')).toBe(false);
  });
});
