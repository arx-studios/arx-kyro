import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { RequestCard } from '@kyro/api-client';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@kyro/api-client';

const reducedMotion = vi.hoisted(() => ({ value: true }));
vi.mock('motion/react', async (importOriginal) => {
  const actual = (await importOriginal()) as Record<string, unknown>;
  return { ...actual, useReducedMotion: () => reducedMotion.value };
});

import { SwipeDeck, type SwipeDeckProps } from './SwipeDeck';
import { keyToAction, swipeActionForOffset } from './labels';

function card(n: number): RequestCard {
  return {
    id: `00000000-0000-0000-0000-00000000000${n}`,
    title: `Fictional tuition ${n}`,
    storyExcerpt: 'A short fictional story.',
    recipientPublicName: `Student ${n}`,
    expenseType: 'TUITION',
    institutionName: 'Fictional Institute',
    institutionCity: 'Pune',
    institutionCountry: 'IN',
    courseName: null,
    amount: { amountMinor: 4500000, currency: 'INR' },
    dueDate: '2026-12-01',
    verifiedAt: '2026-11-01T10:00:00Z',
    funding: { target: { amountMinor: 4500000, currency: 'INR' }, pledgedMinor: 0, confirmedMinor: 0 },
  };
}

function setup(overrides: Partial<SwipeDeckProps> = {}) {
  const onSwipe = vi.fn().mockResolvedValue({});
  const props: SwipeDeckProps = {
    cards: [card(1), card(2), card(3), card(4), card(5)],
    mode: 'donor',
    onSwipe,
    hasMore: false,
    isFetchingMore: false,
    onNeedMore: vi.fn(),
    ...overrides,
  };
  const utils = render(<SwipeDeck {...props} />);
  return { onSwipe, props, ...utils };
}

beforeEach(() => {
  reducedMotion.value = true;
});

describe('helpers', () => {
  it('maps drag offsets with a 120px threshold', () => {
    expect(swipeActionForOffset(121)).toBe('INTERESTED');
    expect(swipeActionForOffset(-121)).toBe('PASS');
    expect(swipeActionForOffset(120)).toBeNull();
    expect(swipeActionForOffset(-50)).toBeNull();
  });
  it('maps keys', () => {
    expect(keyToAction('ArrowLeft')).toBe('PASS');
    expect(keyToAction('ArrowRight')).toBe('INTERESTED');
    expect(keyToAction('s')).toBe('SAVED');
    expect(keyToAction('x')).toBeNull();
  });
});

describe('SwipeDeck', () => {
  it('shows only privacy-safe card fields', () => {
    setup();
    expect(screen.getByRole('heading', { name: 'Fictional tuition 1' })).toBeInTheDocument();
    expect(screen.getByText(/Student 1/)).toBeInTheDocument();
    expect(screen.getByText(/Verified/)).toBeInTheDocument();
    expect(screen.queryByText(/pledged/i)).not.toBeInTheDocument();
    expect(screen.getByText('Confirmed')).toBeInTheDocument();
  });

  it('buttons work without gestures and advance the card', async () => {
    const user = userEvent.setup();
    const { onSwipe } = setup();
    await user.click(screen.getByRole('button', { name: 'Pass on this request' }));
    expect(onSwipe).toHaveBeenCalledWith('00000000-0000-0000-0000-000000000001', 'PASS');
    expect(screen.getByRole('heading', { name: 'Fictional tuition 2' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Save this request for later' }));
    expect(onSwipe).toHaveBeenLastCalledWith('00000000-0000-0000-0000-000000000002', 'SAVED');
    await user.click(screen.getByRole('button', { name: 'I am interested in this request' }));
    expect(onSwipe).toHaveBeenLastCalledWith('00000000-0000-0000-0000-000000000003', 'INTERESTED');
  });

  it('keyboard: left = PASS, right = INTERESTED, S = SAVED, and focus moves to the next card', async () => {
    const user = userEvent.setup();
    const { onSwipe } = setup();
    const first = screen.getByRole('article');
    first.focus();
    await user.keyboard('{ArrowLeft}');
    expect(onSwipe).toHaveBeenLastCalledWith('00000000-0000-0000-0000-000000000001', 'PASS');
    await waitFor(() => expect(screen.getByRole('article')).toHaveFocus());
    await user.keyboard('{ArrowRight}');
    expect(onSwipe).toHaveBeenLastCalledWith('00000000-0000-0000-0000-000000000002', 'INTERESTED');
    await user.keyboard('s');
    expect(onSwipe).toHaveBeenLastCalledWith('00000000-0000-0000-0000-000000000003', 'SAVED');
    expect(screen.getByRole('status')).toHaveTextContent('Saved Fictional tuition 3.');
  });

  it('sends only one request when the same card is actioned twice at once', () => {
    const { onSwipe } = setup();
    const region = screen.getByRole('region', { name: 'Verified requests' });
    act(() => {
      fireEvent.keyDown(region, { key: 'ArrowLeft' });
      fireEvent.keyDown(region, { key: 'ArrowLeft' });
    });
    expect(onSwipe).toHaveBeenCalledTimes(1);
  });

  it('restores the card and shows an alert when the request fails', async () => {
    const user = userEvent.setup();
    const onSwipe = vi.fn().mockRejectedValue(
      new ApiError({ type: 'x', title: 'Forbidden', status: 403, code: 'FORBIDDEN', detail: 'Donor role required.' }),
    );
    setup({ onSwipe });
    await user.click(screen.getByRole('button', { name: 'Pass on this request' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Donor role required.');
    expect(screen.getByRole('heading', { name: 'Fictional tuition 1' })).toBeInTheDocument();
  });

  it('prefetches when three or fewer cards remain', () => {
    const onNeedMore = vi.fn();
    setup({ cards: [card(1), card(2), card(3)], hasMore: true, onNeedMore });
    expect(onNeedMore).toHaveBeenCalled();
  });

  it('does not prefetch with many cards left', () => {
    const onNeedMore = vi.fn();
    setup({ hasMore: true, onNeedMore });
    expect(onNeedMore).not.toHaveBeenCalled();
  });

  it('anonymous users get sign-in links and no swipes', async () => {
    const user = userEvent.setup();
    const { onSwipe } = setup({ mode: 'anonymous' });
    const link = screen.getByRole('link', { name: /Pass on this request/ });
    expect(link).toHaveAttribute('href', '/login?next=%2Fdiscover');
    screen.getByRole('article').focus();
    await user.keyboard('{ArrowLeft}');
    expect(onSwipe).not.toHaveBeenCalled();
  });

  it('non-donor users see a read-only notice and disabled buttons', () => {
    setup({ mode: 'readonly' });
    expect(screen.getByText(/read-only/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Pass on this request' })).toBeDisabled();
  });

  it('shows an end state when the feed is exhausted', () => {
    setup({ cards: [] });
    expect(screen.getByText(/seen every verified request/)).toBeInTheDocument();
  });

  it('shows keyboard hints', () => {
    setup();
    expect(screen.getByText(/Keyboard:/)).toBeInTheDocument();
  });

  it('renders the draggable (non-reduced-motion) path too', () => {
    reducedMotion.value = false;
    setup();
    expect(screen.getByRole('article')).toBeInTheDocument();
    expect(screen.getByText(/or drag the card/)).toBeInTheDocument();
  });
});
