import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const post = vi.hoisted(() => vi.fn());
vi.mock('@/lib/api', () => ({ api: { POST: post, GET: vi.fn() } }));

import { ReportDialog } from './ReportDialog';
import { validateReport } from './reasons';

const ID = '22222222-2222-2222-2222-222222222222';

function renderDialog() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <ReportDialog open onClose={() => {}} requestId={ID} />
    </QueryClientProvider>,
  );
}

beforeEach(() => post.mockReset());

describe('validateReport', () => {
  it('requires a reason and details for OTHER', () => {
    expect(validateReport('', '').ok).toBe(false);
    expect(validateReport('OTHER', '   ').ok).toBe(false);
    expect(validateReport('OTHER', 'text').ok).toBe(true);
    expect(validateReport('SUSPECTED_FRAUD', '').ok).toBe(true);
    expect(validateReport('SUSPECTED_FRAUD', 'x'.repeat(2001)).ok).toBe(false);
  });
});

describe('ReportDialog', () => {
  it('does not send without a reason', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByText('Choose a reason.')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('requires details for OTHER', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.click(screen.getByRole('radio', { name: 'Something else' }));
    await user.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Please describe the concern.');
    expect(post).not.toHaveBeenCalled();
  });

  it('sends the report and shows the thank-you copy', async () => {
    const user = userEvent.setup();
    post.mockResolvedValueOnce({
      data: { id: ID, status: 'OPEN', createdAt: '2026-11-10T10:00:00Z' },
      response: { ok: true, status: 202 },
    });
    renderDialog();
    await user.click(screen.getByRole('radio', { name: 'I suspect fraud' }));
    await user.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByText('Thanks. Our team will review this request.')).toBeInTheDocument();
    expect(post.mock.calls[0]![1].body).toEqual({ requestId: ID, reason: 'SUSPECTED_FRAUD' });
  });

  it('handles 409 and 429', async () => {
    const user = userEvent.setup();
    post.mockResolvedValueOnce({
      error: { type: 'x', title: 'Conflict', status: 409, code: 'REPORT_ALREADY_OPEN' },
      response: { ok: false, status: 409 },
    });
    post.mockResolvedValueOnce({
      error: { type: 'x', title: 'Too many', status: 429, code: 'RATE_LIMITED' },
      response: { ok: false, status: 429 },
    });
    renderDialog();
    await user.click(screen.getByRole('radio', { name: 'Privacy concern' }));
    await user.click(screen.getByRole('button', { name: 'Send report' }));
    expect(await screen.findByText('You have already reported this request.')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Send report' }));
    await waitFor(() => expect(screen.getByText(/several reports in a short time/)).toBeInTheDocument());
  });
});
