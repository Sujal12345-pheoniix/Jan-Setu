import type { ReactNode } from 'react';
import { AlertTriangle, CheckCircle2, Info, Inbox, XCircle } from 'lucide-react';
import { cn } from '@/utils/format';
import { ApiError } from '@/api/client';
import { Button } from './Button';

type Tone = 'info' | 'success' | 'warning' | 'error';

const tones: Record<Tone, { box: string; icon: ReactNode }> = {
  info: { box: 'border-brand-200 bg-brand-50 text-brand-900', icon: <Info className="h-5 w-5" aria-hidden /> },
  success: { box: 'border-green-300 bg-green-50 text-green-900', icon: <CheckCircle2 className="h-5 w-5" aria-hidden /> },
  warning: { box: 'border-amber-300 bg-amber-50 text-amber-900', icon: <AlertTriangle className="h-5 w-5" aria-hidden /> },
  error: { box: 'border-red-300 bg-red-50 text-red-900', icon: <XCircle className="h-5 w-5" aria-hidden /> },
};

export function Alert({ tone = 'info', title, children, className, action }: {
  tone?: Tone;
  title?: string;
  children?: ReactNode;
  className?: string;
  action?: ReactNode;
}) {
  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-md border p-3 text-sm', tones[tone].box, className)}
    >
      <span className="mt-0.5 shrink-0">{tones[tone].icon}</span>
      <div className="flex-1 space-y-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div>{children}</div>}
      </div>
      {action}
    </div>
  );
}

/** Standard error presentation for failed queries, including the request id for support. */
export function ErrorState({ error, onRetry, title = 'Something went wrong' }: {
  error: unknown;
  onRetry?: () => void;
  title?: string;
}) {
  const apiError = error instanceof ApiError ? error : null;
  const message =
    apiError?.status === 403
      ? 'You do not have permission to view this.'
      : apiError?.status === 404
        ? 'This item was not found, or you do not have access to it.'
        : (apiError?.message ?? 'An unexpected error occurred.');
  return (
    <Alert
      tone="error"
      title={title}
      action={onRetry && apiError?.status !== 403 && apiError?.status !== 404 ? (
        <Button size="sm" variant="secondary" onClick={onRetry}>
          Retry
        </Button>
      ) : undefined}
    >
      <p>{message}</p>
      {apiError?.requestId && <p className="mt-1 text-xs opacity-80">Reference: {apiError.requestId}</p>}
    </Alert>
  );
}

export function EmptyState({ title, description, action, icon }: {
  title: string;
  description?: string;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center rounded-card border border-dashed border-line bg-white px-6 py-10 text-center">
      <span className="text-brand-400">{icon ?? <Inbox className="h-10 w-10" aria-hidden />}</span>
      <h3 className="mt-3 text-base font-semibold text-ink">{title}</h3>
      {description && <p className="mt-1 max-w-md text-sm text-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded bg-line/70', className)} aria-hidden />;
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="space-y-2" role="status" aria-label="Loading">
      {Array.from({ length: rows }, (_, i) => (
        <Skeleton key={i} className="h-10 w-full" />
      ))}
      <span className="sr-only">Loading…</span>
    </div>
  );
}
