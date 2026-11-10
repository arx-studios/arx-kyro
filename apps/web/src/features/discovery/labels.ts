import type { RequestCard, SwipeAction } from '@kyro/api-client';

export const EXPENSE_LABELS: Record<RequestCard['expenseType'], string> = {
  TUITION: 'Tuition fee',
  EXAMINATION_FEE: 'Examination fee',
  COURSE_MATERIALS: 'Course materials',
  OTHER_ACADEMIC: 'Other academic expense',
};

export const ACTION_LABELS: Record<SwipeAction, string> = {
  PASS: 'Pass',
  SAVED: 'Save',
  INTERESTED: 'Interested',
};

export const ACTION_PAST: Record<SwipeAction, string> = {
  PASS: 'Passed on',
  SAVED: 'Saved',
  INTERESTED: 'Marked interested in',
};

/** Drag distance (px) beyond which a release counts as a swipe. */
export const SWIPE_THRESHOLD = 120;

export function swipeActionForOffset(x: number): SwipeAction | null {
  if (x > SWIPE_THRESHOLD) return 'INTERESTED';
  if (x < -SWIPE_THRESHOLD) return 'PASS';
  return null;
}

export function keyToAction(key: string): SwipeAction | null {
  if (key === 'ArrowLeft') return 'PASS';
  if (key === 'ArrowRight') return 'INTERESTED';
  if (key === 's' || key === 'S') return 'SAVED';
  return null;
}

export function loginHref(next: string): string {
  return `/login?next=${encodeURIComponent(next)}`;
}
