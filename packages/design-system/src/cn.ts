/** Join class names, skipping falsy values. */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(' ');
}

/** Shared visible focus ring (>= 3:1 non-text contrast on warm and navy surfaces). */
export const focusRing =
  'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-navy-700 focus-visible:ring-2 focus-visible:ring-champagne-400 focus-visible:ring-offset-2 focus-visible:ring-offset-warm-50';
