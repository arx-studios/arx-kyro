import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { FundingSummary } from '@kyro/api-client';
import { FundingSummaryView } from './FundingPanel';

const summary: FundingSummary = {
  requestId: 'r1',
  target: { amountMinor: 1000000, currency: 'INR' },
  confirmedMinor: 200000,
  pledgedMinor: 300000,
  remainingMinor: 800000,
  pledgeableMinor: 500000,
  activePledgeCount: 2,
  pledgesAreNonBinding: true,
};

describe('FundingSummaryView', () => {
  it('shows pledged separately from confirmed and says pledges are not payments', () => {
    render(<FundingSummaryView summary={summary} />);
    expect(screen.getByText(/pledges are not payments/i)).toBeInTheDocument();
    const pledged = screen.getByTestId('pledged-box');
    expect(pledged).toHaveTextContent(/pledged \(not yet confirmed\)/i);
    expect(pledged).toHaveTextContent('2 donors');
    // Outstanding uses remainingMinor (target - confirmed), not target - confirmed - pledged.
    expect(screen.getByText('Still needed').nextSibling).toHaveTextContent(/8,000\.00/);
    expect(screen.getByText('Confirmed').nextSibling).toHaveTextContent(/2,000\.00/);
  });
});
