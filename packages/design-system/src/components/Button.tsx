import { forwardRef, type ButtonHTMLAttributes } from 'react';
import { cn, focusRing } from '../cn';
import { Spinner } from './Spinner';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /** Shows a spinner, sets aria-busy and blocks activation. */
  loading?: boolean;
}

const variants: Record<ButtonVariant, string> = {
  primary: 'bg-navy-900 text-warm-50 hover:bg-navy-800 border border-navy-900',
  secondary: 'bg-warm-50 text-navy-900 border border-navy-900 hover:bg-navy-100',
  ghost: 'bg-transparent text-navy-900 border border-transparent hover:bg-warm-100',
  danger: 'bg-danger-600 text-white hover:bg-danger-700 border border-danger-600',
};

const sizes: Record<ButtonSize, string> = {
  md: 'min-h-11 px-4 text-sm',
  lg: 'min-h-12 px-6 text-base',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading = false, disabled, className, children, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-md font-medium transition-colors motion-reduce:transition-none',
        'disabled:cursor-not-allowed disabled:opacity-60',
        focusRing,
        variants[variant],
        sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Spinner size="sm" label="Loading" /> : null}
      {children}
    </button>
  );
});
