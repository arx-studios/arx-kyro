import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@kyro/api-client';
import { ResolveForm } from './ResolveForm';

const NOTE = 'Verified with the institution directly.';

describe('ResolveForm', () => {
  it('disables the hold checkbox for DISMISSED and enables it for ACTION_TAKEN', async () => {
    const user = userEvent.setup();
    render(<ResolveForm onResolve={vi.fn()} />);
    const hold = screen.getByRole('checkbox', { name: 'Hide request from discovery' });
    expect(hold).toBeDisabled();
    await user.click(screen.getByRole('radio', { name: /Dismiss/ }));
    expect(hold).toBeDisabled();
    await user.click(screen.getByRole('radio', { name: 'Action taken' }));
    expect(hold).toBeEnabled();
    await user.click(hold);
    expect(hold).toBeChecked();
    await user.click(screen.getByRole('radio', { name: /Dismiss/ }));
    expect(hold).not.toBeChecked();
    expect(hold).toBeDisabled();
  });

  it('requires a note of at least 10 characters', async () => {
    const onResolve = vi.fn();
    const user = userEvent.setup();
    render(<ResolveForm onResolve={onResolve} />);
    await user.click(screen.getByRole('radio', { name: /Dismiss/ }));
    await user.type(screen.getByLabelText(/Resolution note/), 'too short');
    await user.click(screen.getByRole('button', { name: 'Review resolution' }));
    expect(screen.getByText(/at least 10 characters/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onResolve).not.toHaveBeenCalled();
  });

  it('requires a resolution choice', async () => {
    const user = userEvent.setup();
    render(<ResolveForm onResolve={vi.fn()} />);
    await user.type(screen.getByLabelText(/Resolution note/), NOTE);
    await user.click(screen.getByRole('button', { name: 'Review resolution' }));
    expect(screen.getByText('Choose a resolution.')).toBeInTheDocument();
  });

  it('confirms in a dialog then sends the hold flag with ACTION_TAKEN', async () => {
    const onResolve = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<ResolveForm onResolve={onResolve} />);
    await user.click(screen.getByRole('radio', { name: 'Action taken' }));
    await user.click(screen.getByRole('checkbox', { name: 'Hide request from discovery' }));
    await user.type(screen.getByLabelText(/Resolution note/), NOTE);
    await user.click(screen.getByRole('button', { name: 'Review resolution' }));
    expect(onResolve).not.toHaveBeenCalled();
    await user.click(await screen.findByRole('button', { name: 'Confirm resolution' }));
    await waitFor(() =>
      expect(onResolve).toHaveBeenCalledWith({ resolution: 'ACTION_TAKEN', note: NOTE, holdRequest: true }),
    );
  });

  it('shows a friendly alert for a 409 not-assignee conflict', async () => {
    const onResolve = vi.fn().mockRejectedValue(
      new ApiError({ type: 't', title: 'Conflict', status: 409, code: 'REPORT_NOT_ASSIGNED_TO_YOU' }),
    );
    const user = userEvent.setup();
    render(<ResolveForm onResolve={onResolve} />);
    await user.click(screen.getByRole('radio', { name: /Dismiss/ }));
    await user.type(screen.getByLabelText(/Resolution note/), NOTE);
    await user.click(screen.getByRole('button', { name: 'Review resolution' }));
    await user.click(await screen.findByRole('button', { name: 'Confirm resolution' }));
    expect(await screen.findByText('You are not the assignee')).toBeInTheDocument();
  });
});
