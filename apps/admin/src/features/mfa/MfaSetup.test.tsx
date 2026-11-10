import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const replace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock('qrcode', () => ({ default: { toDataURL: vi.fn().mockResolvedValue('data:image/png;base64,AAAA') } }));

const post = vi.fn();
vi.mock('@/lib/api', () => ({ api: { POST: (...args: unknown[]) => post(...args) } }));

import { MfaSetup } from './MfaSetup';

const ok = (data: unknown) => ({ response: { ok: true, status: 200 }, data });

function renderSetup() {
  const client = new QueryClient();
  return render(
    <QueryClientProvider client={client}>
      <MfaSetup />
    </QueryClientProvider>,
  );
}

describe('MfaSetup', () => {
  beforeEach(() => {
    replace.mockReset();
    post.mockReset();
    post.mockImplementation(async (path: string) => {
      if (path.endsWith('/enrollment')) {
        return ok({
          secret: 'JBSWY3DPEHPK3PXP',
          otpauthUri: 'otpauth://totp/KYRO:v@kyro.local?secret=JBSWY3DPEHPK3PXP',
          issuer: 'KYRO',
          accountName: 'v@kyro.local',
        });
      }
      return ok({ codes: ['AAAAA-11111', 'BBBBB-22222'] });
    });
  });

  it('shows the local QR and manual secret, then recovery codes that must be acknowledged before continuing', async () => {
    const user = userEvent.setup();
    renderSetup();
    await user.click(screen.getByRole('button', { name: 'Begin setup' }));

    expect(await screen.findByTestId('totp-secret')).toHaveTextContent('JBSWY3DPEHPK3PXP');
    expect(await screen.findByRole('img', { name: /QR code/ })).toHaveAttribute('src', expect.stringMatching(/^data:image/));

    await user.type(screen.getByLabelText(/Authenticator code/), '123456');
    await user.click(screen.getByRole('button', { name: 'Confirm and continue' }));
    expect(post).toHaveBeenLastCalledWith('/api/v1/auth/mfa/totp/enrollment/confirm', { body: { code: '123456' } });

    expect(await screen.findByText('AAAAA-11111')).toBeInTheDocument();
    const cont = screen.getByRole('button', { name: 'Continue' });
    expect(cont).toBeDisabled();
    await user.click(cont);
    expect(replace).not.toHaveBeenCalled();

    await user.click(screen.getByRole('checkbox', { name: /I have saved these recovery codes/ }));
    expect(cont).toBeEnabled();
    await user.click(cont);
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/'));
  });

  it('rejects a malformed code without calling the server', async () => {
    const user = userEvent.setup();
    renderSetup();
    await user.click(screen.getByRole('button', { name: 'Begin setup' }));
    await user.type(await screen.findByLabelText(/Authenticator code/), '12');
    await user.click(screen.getByRole('button', { name: 'Confirm and continue' }));
    expect(screen.getByRole('alert')).toHaveTextContent(/6-digit code/);
    expect(post).toHaveBeenCalledTimes(1);
  });
});
