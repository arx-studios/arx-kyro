import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { HoldPanel } from './HoldPanel';

const hold = {
  requestId: 'r1',
  reportId: 'p1',
  reason: 'Altered document',
  placedBy: { id: 'u1', displayName: 'Vera Verifier' },
  placedAt: '2026-11-10T10:00:00Z',
};

describe('HoldPanel', () => {
  it('hides Release hold for non-admins (VERIFIER only)', () => {
    render(<HoldPanel hold={hold} isAdmin={false} onRelease={vi.fn()} />);
    expect(screen.getByText(/hidden from discovery/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Release hold' })).not.toBeInTheDocument();
  });

  it('lets an admin release after confirming', async () => {
    const onRelease = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<HoldPanel hold={hold} isAdmin onRelease={onRelease} />);
    await user.click(screen.getByRole('button', { name: 'Release hold' }));
    expect(onRelease).not.toHaveBeenCalled();
    await user.click(await screen.findByRole('button', { name: 'Confirm release' }));
    await waitFor(() => expect(onRelease).toHaveBeenCalledTimes(1));
  });
});
