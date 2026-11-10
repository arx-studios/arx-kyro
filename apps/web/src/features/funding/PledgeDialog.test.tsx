import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const post = vi.hoisted(() => vi.fn());
vi.mock('@/lib/api', () => ({ api: { POST: post, GET: vi.fn() } }));

import { CancelPledgeDialog } from './CancelPledgeDialog';
import { FundingFigures } from './FundingPanel';
import { PledgeDialog } from './PledgeDialog';

const PLEDGE = {
  id: '11111111-1111-1111-1111-111111111111',
  requestId: '22222222-2222-2222-2222-222222222222',
  amount: { amountMinor: 500000, currency: 'INR' },
  status: 'ACTIVE' as const,
  nonBinding: true,
  createdAt: '2026-11-10T10:00:00Z',
};

function wrap(ui: React.ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

function renderDialog(pledgeableMinor = 1000000, onPledged = vi.fn()) {
  wrap(
    <PledgeDialog
      open
      onClose={() => {}}
      requestId={PLEDGE.requestId}
      currency="INR"
      pledgeableMinor={pledgeableMinor}
      onPledged={onPledged}
    />,
  );
  return { onPledged };
}

beforeEach(() => post.mockReset());

describe('PledgeDialog', () => {
  it('shows the non-payment language and has no payment fields', () => {
    renderDialog();
    expect(screen.getAllByText(/A pledge is not a payment/).length).toBeGreaterThan(0);
    expect(screen.queryByLabelText(/card|upi|bank|cvv/i)).not.toBeInTheDocument();
  });

  it('blocks submit without the acknowledgement checkbox', async () => {
    const user = userEvent.setup();
    renderDialog();
    await user.type(screen.getByLabelText(/Pledge amount/), '5000');
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    expect(await screen.findByText(/Confirm that you understand/)).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('blocks amounts above the pledgeable maximum and below 1', async () => {
    const user = userEvent.setup();
    renderDialog(1000000);
    await user.click(screen.getByLabelText(/I understand a pledge is not a payment/));
    await user.type(screen.getByLabelText(/Pledge amount/), '10,001');
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    expect(await screen.findByText(/more than can still be pledged/)).toBeInTheDocument();
    await user.clear(screen.getByLabelText(/Pledge amount/));
    await user.type(screen.getByLabelText(/Pledge amount/), '0.5');
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    expect(await screen.findByText(/smallest pledge/)).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('reuses the same Idempotency-Key when retrying after a network error', async () => {
    const user = userEvent.setup();
    post.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    post.mockResolvedValueOnce({ data: PLEDGE, response: { ok: true, status: 201 } });
    const { onPledged } = renderDialog();
    await user.type(screen.getByLabelText(/Pledge amount/), '5000');
    await user.click(screen.getByLabelText(/I understand a pledge is not a payment/));
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    expect(await screen.findByText(/will not create a second pledge/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    await waitFor(() => expect(onPledged).toHaveBeenCalledTimes(1));
    expect(post).toHaveBeenCalledTimes(2);
    const keys = post.mock.calls.map((c) => c[1].params.header['Idempotency-Key']);
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
    expect(post.mock.calls[0]![1].body).toEqual({
      amount: { amountMinor: 500000, currency: 'INR' },
      acknowledgeNonBinding: true,
    });
  });

  it('uses a new key only when the amount changes after an attempt', async () => {
    const user = userEvent.setup();
    post.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    post.mockResolvedValueOnce({ data: PLEDGE, response: { ok: true, status: 201 } });
    renderDialog();
    await user.type(screen.getByLabelText(/Pledge amount/), '5000');
    await user.click(screen.getByLabelText(/I understand a pledge is not a payment/));
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    await screen.findByText(/will not create a second pledge/);
    await user.clear(screen.getByLabelText(/Pledge amount/));
    await user.type(screen.getByLabelText(/Pledge amount/), '6000');
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    await waitFor(() => expect(post).toHaveBeenCalledTimes(2));
    expect(post.mock.calls[1]![1].params.header['Idempotency-Key']).not.toBe(
      post.mock.calls[0]![1].params.header['Idempotency-Key'],
    );
  });

  it('maps ACTIVE_PLEDGE_EXISTS to a friendly message', async () => {
    const user = userEvent.setup();
    post.mockResolvedValueOnce({
      error: { type: 'x', title: 'Conflict', status: 409, code: 'ACTIVE_PLEDGE_EXISTS' },
      response: { ok: false, status: 409 },
    });
    renderDialog();
    await user.type(screen.getByLabelText(/Pledge amount/), '100');
    await user.click(screen.getByLabelText(/I understand a pledge is not a payment/));
    await user.click(screen.getByRole('button', { name: 'Make pledge' }));
    expect(await screen.findByText(/already have an active pledge/)).toBeInTheDocument();
  });
});

describe('CancelPledgeDialog', () => {
  it('asks for confirmation then cancels the pledge', async () => {
    const user = userEvent.setup();
    const onCancelled = vi.fn();
    post.mockResolvedValueOnce({
      data: { ...PLEDGE, status: 'CANCELLED', cancelReason: 'DONOR_CANCELLED' },
      response: { ok: true, status: 200 },
    });
    wrap(<CancelPledgeDialog open onClose={() => {}} pledge={PLEDGE} onCancelled={onCancelled} />);
    expect(screen.getByText(/Cancel this pledge\?/)).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
    await user.click(screen.getByRole('button', { name: 'Cancel pledge' }));
    await waitFor(() => expect(onCancelled).toHaveBeenCalled());
    expect(post.mock.calls[0]![0]).toBe('/api/v1/funding/pledges/{pledgeId}/cancel');
  });

  it('Keep pledge does not call the API', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    wrap(<CancelPledgeDialog open onClose={onClose} pledge={PLEDGE} onCancelled={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Keep pledge' }));
    expect(onClose).toHaveBeenCalled();
    expect(post).not.toHaveBeenCalled();
  });
});

describe('FundingFigures', () => {
  it('shows outstanding = target - confirmed and pledged on its own line', () => {
    render(
      <FundingFigures
        target={{ amountMinor: 4500000, currency: 'INR' }}
        confirmedMinor={0}
        pledgedMinor={1500000}
        activePledgeCount={2}
      />,
    );
    expect(screen.getByText('Outstanding need').parentElement).toHaveTextContent('45,000');
    expect(screen.getByText('Pledged (not yet paid)').parentElement).toHaveTextContent('15,000');
    expect(screen.getByText('Pledged (not yet paid)').parentElement).toHaveTextContent('2 pledges');
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '0');
    expect(screen.getByText(/No payments are taken in this sandbox/)).toBeInTheDocument();
  });
});
