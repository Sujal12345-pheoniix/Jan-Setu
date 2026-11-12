import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { cn } from '@/utils/format';

const controlBase =
  'w-full rounded-md border bg-white px-3 text-sm text-ink placeholder:text-muted/70 disabled:bg-canvas disabled:text-muted';

function controlClass(invalid?: boolean) {
  return cn(controlBase, invalid ? 'border-red-600' : 'border-line hover:border-brand-300');
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
  required?: boolean;
  className?: string;
  children: (props: { id: string; describedBy?: string; invalid: boolean }) => ReactNode;
}

/** Label + control + hint + error, wired with aria-describedby / aria-invalid. */
export function Field({ label, error, hint, required, className, children }: FieldProps) {
  const id = useId();
  const hintId = hint ? `${id}-hint` : undefined;
  const errorId = error ? `${id}-error` : undefined;
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined;
  return (
    <div className={cn('space-y-1.5', className)}>
      <label htmlFor={id} className="block text-sm font-medium text-ink">
        {label}
        {required && (
          <span className="ml-0.5 text-red-700" aria-hidden>
            *
          </span>
        )}
        {required && <span className="sr-only"> (required)</span>}
      </label>
      {children({ id, describedBy, invalid: !!error })}
      {hint && (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-red-700" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { label: string; error?: string; hint?: string };

export const TextInput = forwardRef<HTMLInputElement, InputProps>(function TextInput(
  { label, error, hint, required, className, ...rest },
  ref,
) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={cn(controlClass(invalid), 'h-10')}
          {...rest}
        />
      )}
    </Field>
  );
});

type TextareaProps = TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; error?: string; hint?: string };

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaProps>(function TextArea(
  { label, error, hint, required, className, rows = 4, ...rest },
  ref,
) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {({ id, describedBy, invalid }) => (
        <textarea
          ref={ref}
          id={id}
          rows={rows}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={cn(controlClass(invalid), 'py-2')}
          {...rest}
        />
      )}
    </Field>
  );
});

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string;
  hint?: string;
  options: { value: string; label: string }[];
  placeholder?: string;
};

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, hint, required, className, options, placeholder, ...rest },
  ref,
) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={className}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          aria-required={required || undefined}
          className={cn(controlClass(invalid), 'h-10')}
          {...rest}
        >
          {placeholder !== undefined && <option value="">{placeholder}</option>}
          {options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
      )}
    </Field>
  );
});

type CheckboxProps = InputHTMLAttributes<HTMLInputElement> & { label: ReactNode; error?: string };

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { label, error, className, ...rest },
  ref,
) {
  const id = useId();
  return (
    <div className={className}>
      <div className="flex items-start gap-2">
        <input
          ref={ref}
          id={id}
          type="checkbox"
          aria-invalid={!!error || undefined}
          aria-describedby={error ? `${id}-error` : undefined}
          className="mt-0.5 h-4 w-4 rounded border-line text-brand-600 accent-brand-600"
          {...rest}
        />
        <label htmlFor={id} className="text-sm text-ink">
          {label}
        </label>
      </div>
      {error && (
        <p id={`${id}-error`} className="mt-1 text-xs font-medium text-red-700" role="alert">
          {error}
        </p>
      )}
    </div>
  );
});

/** Error summary at the top of a form; receives focus so screen-reader users hear the problems. */
export function FormErrorSummary({ errors }: { errors: string[] }) {
  if (errors.length === 0) return null;
  return (
    <div
      role="alert"
      tabIndex={-1}
      ref={(el) => el?.focus()}
      className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800"
    >
      <p className="font-semibold">Please fix the following:</p>
      <ul className="mt-1 list-disc pl-5">
        {errors.map((e) => (
          <li key={e}>{e}</li>
        ))}
      </ul>
    </div>
  );
}
