import {
  Children,
  cloneElement,
  forwardRef,
  isValidElement,
  useId,
  type InputHTMLAttributes,
  type ReactElement,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { cn, focusRing } from '../cn';

const controlBase = cn(
  'block w-full min-h-11 rounded-md border bg-white px-3 py-2 text-base text-charcoal-900 placeholder:text-charcoal-600',
  'disabled:cursor-not-allowed disabled:bg-warm-100 disabled:opacity-70',
  focusRing,
);

function controlClass(invalid: boolean | undefined, className?: string) {
  return cn(controlBase, invalid ? 'border-danger-600' : 'border-charcoal-600', className);
}

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ invalid, className, ...rest }, ref) {
  return <input ref={ref} aria-invalid={invalid || undefined} className={controlClass(invalid, className)} {...rest} />;
});

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { invalid, className, rows = 4, ...rest },
  ref,
) {
  return (
    <textarea
      ref={ref}
      rows={rows}
      aria-invalid={invalid || undefined}
      className={controlClass(invalid, className)}
      {...rest}
    />
  );
});

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { invalid, className, children, ...rest },
  ref,
) {
  return (
    <select ref={ref} aria-invalid={invalid || undefined} className={controlClass(invalid, className)} {...rest}>
      {children}
    </select>
  );
});

/** Props injected by Field into its control so label, hint and error are programmatically associated. */
export interface FieldControlProps {
  id: string;
  'aria-describedby'?: string;
  'aria-invalid'?: true;
  'aria-required'?: true;
  invalid?: boolean;
  required?: boolean;
}

export interface FieldProps {
  label: ReactNode;
  hint?: ReactNode;
  /** Error message; when set the control is marked aria-invalid and the message is announced (role=alert). */
  error?: ReactNode;
  required?: boolean;
  /** Override the generated control id. */
  id?: string;
  className?: string;
  /** A render function receiving the aria wiring props, or a single element which is cloned with them. */
  children: ((props: FieldControlProps) => ReactNode) | ReactElement<Record<string, unknown>>;
}

export function Field({ label, hint, error, required, id, className, children }: FieldProps) {
  const generated = useId();
  const controlId = id ?? `field-${generated}`;
  const hintId = hint ? `${controlId}-hint` : undefined;
  const errorId = error ? `${controlId}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;

  const controlProps: FieldControlProps = {
    id: controlId,
    'aria-describedby': describedBy,
    'aria-invalid': error ? true : undefined,
    'aria-required': required ? true : undefined,
    invalid: error ? true : undefined,
    required: required || undefined,
  };

  let control: ReactNode;
  if (typeof children === 'function') {
    control = children(controlProps);
  } else if (isValidElement(Children.only(children))) {
    control = cloneElement(children, controlProps as unknown as Record<string, unknown>);
  }

  return (
    <div className={cn('flex flex-col gap-1.5', className)}>
      <label htmlFor={controlId} className="text-sm font-medium text-charcoal-900">
        {label}
        {required ? (
          <span aria-hidden="true" className="ml-0.5 text-danger-600">
            *
          </span>
        ) : null}
      </label>
      {hint ? (
        <p id={hintId} className="text-sm text-charcoal-600">
          {hint}
        </p>
      ) : null}
      {control}
      {error ? (
        <p id={errorId} role="alert" className="text-sm font-medium text-danger-700">
          {error}
        </p>
      ) : null}
    </div>
  );
}
