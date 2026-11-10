import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '@kyro/api-client';
import { DecisionForm } from './DecisionForm';

const RATIONALE = 'Documents match the stated institution.';

async function fill(user: ReturnType<typeof userEvent.setup>, label: string, rationale = RATIONALE) {
  await user.click(screen.getByRole('radio', { name: label }));
  await user.type(screen.getByLabelText(/Internal rationale/), rationale);
}

describe('DecisionForm', () => {
  it('blocks submission without a rationale and never calls the server', async () => {
    const onDecide = vi.fn();
    const user = userEvent.setup();
    render(<DecisionForm onDecide={onDecide} />);
    await user.click(screen.getByRole('radio', { name: 'Approve' }));
    await user.click(screen.getByRole('button', { name: 'Review decision' }));
    expect(screen.getByText(/Rationale is required/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(onDecide).not.toHaveBeenCalled();
  });

  it('requires a recipient message for reject', async () => {
    const onDecide = vi.fn();
    const user = userEvent.setup();
    render(<DecisionForm onDecide={onDecide} />);
    await fill(user, 'Reject');
    await user.click(screen.getByRole('button', { name: 'Review decision' }));
    expect(screen.getByText(/message to the recipient is required/i)).toBeInTheDocument();
    expect(onDecide).not.toHaveBeenCalled();
  });

  it('confirms in a dialog, then sends the trimmed decision', async () => {
    const onDecide = vi.fn().mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(<DecisionForm onDecide={onDecide} />);
    await fill(user, 'Approve');
    await user.click(screen.getByRole('button', { name: 'Review decision' }));
    expect(onDecide).not.toHaveBeenCalled();
    await user.click(await screen.findByRole('button', { name: 'Confirm: Approve' }));
    await waitFor(() => expect(onDecide).toHaveBeenCalledWith({ decision: 'APPROVE', rationale: RATIONALE }));
  });

  it('shows a friendly alert for a self-review 403', async () => {
    const onDecide = vi.fn().mockRejectedValue(
      new ApiError({ type: 't', title: 'Forbidden', status: 403, code: 'SELF_REVIEW_FORBIDDEN' }),
    );
    const user = userEvent.setup();
    render(<DecisionForm onDecide={onDecide} />);
    await fill(user, 'Approve');
    await user.click(screen.getByRole('button', { name: 'Review decision' }));
    await user.click(await screen.findByRole('button', { name: 'Confirm: Approve' }));
    expect(await screen.findByText('You cannot review your own request')).toBeInTheDocument();
  });

  it('shows a friendly alert for a 409 not-assignee conflict', async () => {
    const onDecide = vi.fn().mockRejectedValue(
      new ApiError({ type: 't', title: 'Conflict', status: 409, code: 'CASE_NOT_ASSIGNED_TO_YOU' }),
    );
    const user = userEvent.setup();
    render(<DecisionForm onDecide={onDecide} />);
    await fill(user, 'Approve');
    await user.click(screen.getByRole('button', { name: 'Review decision' }));
    await user.click(await screen.findByRole('button', { name: 'Confirm: Approve' }));
    expect(await screen.findByText('You are not the assignee')).toBeInTheDocument();
  });
});
