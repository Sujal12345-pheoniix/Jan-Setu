import { Fragment, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, ChevronRight as Separator } from 'lucide-react';
import { cn, humanize } from '@/utils/format';
import { Button } from './Button';

export function Card({ title, actions, children, className, bodyClassName }: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section className={cn('rounded-card border border-line bg-surface shadow-sm', className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 py-3">
          {typeof title === 'string' ? <h2 className="text-base font-semibold text-ink">{title}</h2> : title}
          {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
        </header>
      )}
      <div className={cn('p-4', bodyClassName)}>{children}</div>
    </section>
  );
}

export function PageHeader({ title, description, actions, breadcrumbs }: {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  breadcrumbs?: { label: string; to?: string }[];
}) {
  return (
    <div className="mb-6 space-y-2">
      {breadcrumbs && <Breadcrumbs items={breadcrumbs} />}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-ink">{title}</h1>
          {description && <p className="mt-1 text-sm text-muted">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </div>
  );
}

export function Breadcrumbs({ items }: { items: { label: string; to?: string }[] }) {
  return (
    <nav aria-label="Breadcrumb">
      <ol className="flex flex-wrap items-center gap-1 text-sm text-muted">
        {items.map((item, i) => (
          <Fragment key={`${item.label}-${i}`}>
            {i > 0 && <Separator className="h-3.5 w-3.5" aria-hidden />}
            <li>
              {item.to ? (
                <Link className="hover:text-brand-700 hover:underline" to={item.to}>
                  {item.label}
                </Link>
              ) : (
                <span aria-current="page" className="text-ink">
                  {item.label}
                </span>
              )}
            </li>
          </Fragment>
        ))}
      </ol>
    </nav>
  );
}

const badgeTones = {
  neutral: 'bg-slate-100 text-slate-800 ring-slate-300',
  info: 'bg-brand-50 text-brand-800 ring-brand-200',
  success: 'bg-green-50 text-green-800 ring-green-300',
  warning: 'bg-amber-50 text-amber-900 ring-amber-300',
  danger: 'bg-red-50 text-red-800 ring-red-300',
} as const;

export type BadgeTone = keyof typeof badgeTones;

export function Badge({ tone = 'neutral', children, className }: { tone?: BadgeTone; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset', badgeTones[tone], className)}>
      {children}
    </span>
  );
}

const statusTones: Record<string, BadgeTone> = {
  DRAFT: 'neutral', SUBMITTED: 'info', UNDER_REVIEW: 'info', CLARIFICATION_REQUIRED: 'warning', RESUBMITTED: 'info',
  PENDING_APPROVAL: 'info', APPROVED: 'success', COMPLETED: 'success', REJECTED: 'danger', WITHDRAWN: 'neutral',
  ACKNOWLEDGED: 'info', ASSIGNED: 'info', IN_PROGRESS: 'info', RESOLVED: 'success', CLOSED: 'neutral', REOPENED: 'warning',
  ACTIVE: 'success', INACTIVE: 'neutral', SUSPENDED: 'warning', DEACTIVATED: 'danger', PUBLISHED: 'success',
  DEPRECATED: 'neutral', AVAILABLE: 'info', VERIFIED: 'success', OPEN: 'info', BLOCKED: 'warning',
  SUCCESS: 'success', FAILURE: 'danger', DENIED: 'warning',
};

/** Status badge: colour is never the only signal, the text label is always shown. */
export function StatusBadge({ status }: { status: string }) {
  return <Badge tone={statusTones[status] ?? 'neutral'}>{humanize(status)}</Badge>;
}

export function Tabs<T extends string>({ tabs, value, onChange, label }: {
  tabs: { value: T; label: string; count?: number }[];
  value: T;
  onChange: (value: T) => void;
  label: string;
}) {
  return (
    <div role="tablist" aria-label={label} className="flex flex-wrap gap-1 border-b border-line">
      {tabs.map((tab) => {
        const selected = tab.value === value;
        return (
          <button
            key={tab.value}
            role="tab"
            type="button"
            aria-selected={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(tab.value)}
            onKeyDown={(e) => {
              const index = tabs.findIndex((t) => t.value === value);
              if (e.key === 'ArrowRight') onChange(tabs[(index + 1) % tabs.length].value);
              if (e.key === 'ArrowLeft') onChange(tabs[(index - 1 + tabs.length) % tabs.length].value);
            }}
            className={cn(
              '-mb-px border-b-2 px-3 py-2 text-sm font-medium',
              selected ? 'border-brand-600 text-brand-700' : 'border-transparent text-muted hover:text-ink',
            )}
          >
            {tab.label}
            {tab.count !== undefined && <span className="ml-1.5 rounded-full bg-canvas px-1.5 text-xs">{tab.count}</span>}
          </button>
        );
      })}
    </div>
  );
}

export function Pagination({ page, totalPages, totalElements, onChange }: {
  page: number;
  totalPages: number;
  totalElements: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) {
    return <p className="mt-3 text-sm text-muted">{totalElements} result{totalElements === 1 ? '' : 's'}</p>;
  }
  return (
    <nav aria-label="Pagination" className="mt-3 flex items-center justify-between gap-2 text-sm">
      <p className="text-muted">
        Page {page + 1} of {totalPages} · {totalElements} results
      </p>
      <div className="flex gap-2">
        <Button size="sm" variant="secondary" disabled={page === 0} onClick={() => onChange(page - 1)} icon={<ChevronLeft className="h-4 w-4" aria-hidden />}>
          Previous
        </Button>
        <Button size="sm" variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>
          Next <ChevronRight className="h-4 w-4" aria-hidden />
        </Button>
      </div>
    </nav>
  );
}

export function DescriptionList({ items }: { items: { label: string; value: ReactNode }[] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
      {items.map((item) => (
        <div key={item.label}>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">{item.label}</dt>
          <dd className="mt-0.5 text-sm text-ink break-words">{item.value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

export function StatCard({ label, value, hint, tone = 'neutral' }: { label: string; value: ReactNode; hint?: string; tone?: BadgeTone }) {
  const accent = { neutral: 'border-l-slate-300', info: 'border-l-brand-500', success: 'border-l-green-600', warning: 'border-l-amber-500', danger: 'border-l-red-600' }[tone];
  return (
    <div className={cn('rounded-card border border-line border-l-4 bg-surface p-4 shadow-sm', accent)}>
      <p className="text-sm text-muted">{label}</p>
      <p className="mt-1 text-2xl font-bold text-ink">{value}</p>
      {hint && <p className="mt-1 text-xs text-muted">{hint}</p>}
    </div>
  );
}

/** Simple disclosure menu (used for row actions). Closes on Escape and outside blur. */
export function Dropdown({ label, items }: { label: string; items: { label: string; onSelect: () => void; danger?: boolean }[] }) {
  const [open, setOpen] = useState(false);
  return (
    <div
      className="relative inline-block"
      onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
      onBlur={(e) => !e.currentTarget.contains(e.relatedTarget as Node) && setOpen(false)}
    >
      <Button size="sm" variant="secondary" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        {label}
      </Button>
      {open && (
        <div role="menu" className="absolute right-0 z-20 mt-1 min-w-44 rounded-md border border-line bg-white py-1 shadow-lg">
          {items.map((item) => (
            <button
              key={item.label}
              role="menuitem"
              type="button"
              className={cn('block w-full px-3 py-2 text-left text-sm hover:bg-canvas', item.danger && 'text-red-700')}
              onClick={() => {
                setOpen(false);
                item.onSelect();
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
