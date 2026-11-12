import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { Link } from 'react-router-dom';
import { cn } from '@/utils/format';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'link';
type Size = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  icon?: ReactNode;
}

const variants: Record<Variant, string> = {
  primary: 'bg-brand-600 text-white hover:bg-brand-700 disabled:bg-brand-300',
  secondary: 'bg-white text-ink border border-line hover:bg-canvas disabled:text-muted',
  danger: 'bg-red-700 text-white hover:bg-red-800 disabled:bg-red-300',
  ghost: 'text-ink hover:bg-brand-50 disabled:text-muted',
  link: 'text-brand-700 underline-offset-2 hover:underline px-0',
};

const sizes: Record<Size, string> = {
  sm: 'h-8 px-3 text-sm gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-12 px-6 text-base gap-2',
};

export function buttonClasses(variant: Variant = 'primary', size: Size = 'md', className?: string): string {
  return cn(
    'inline-flex items-center justify-center rounded-md font-medium transition-colors',
    variants[variant],
    variant !== 'link' && sizes[size],
    className,
  );
}

/** A router link styled as a button (never nest <button> inside <a>). */
export function LinkButton({ to, variant = 'primary', size = 'md', className, children, icon }: {
  to: string;
  variant?: Variant;
  size?: Size;
  className?: string;
  children: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <Link to={to} className={cn(buttonClasses(variant, size, className), 'gap-2')}>
      {icon}
      {children}
    </Link>
  );
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', size = 'md', loading, icon, className, children, disabled, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cn(
        'inline-flex items-center justify-center rounded-md font-medium transition-colors disabled:cursor-not-allowed',
        variants[variant],
        variant !== 'link' && sizes[size],
        className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
});
