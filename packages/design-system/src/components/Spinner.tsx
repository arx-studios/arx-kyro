import { forwardRef, type HTMLAttributes } from 'react';
import { cn } from '../cn';

export interface SpinnerProps extends HTMLAttributes<HTMLSpanElement> {
  size?: 'sm' | 'md';
  /** Accessible, visually hidden label. */
  label?: string;
}

export const Spinner = forwardRef<HTMLSpanElement, SpinnerProps>(function Spinner(
  { size = 'md', label = 'Loading', className, ...rest },
  ref,
) {
  return (
    <span ref={ref} role="status" className={cn('inline-flex items-center', className)} {...rest}>
      <span
        aria-hidden="true"
        className={cn(
          'inline-block animate-spin rounded-full border-2 border-current border-t-transparent motion-reduce:animate-none',
          size === 'sm' ? 'h-4 w-4' : 'h-6 w-6',
        )}
      />
      <span className="sr-only">{label}</span>
    </span>
  );
});
