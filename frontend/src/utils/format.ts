import { clsx, type ClassValue } from 'clsx';

export function cn(...values: ClassValue[]): string {
  return clsx(values);
}

const dateFormatter = new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
const dateTimeFormatter = new Intl.DateTimeFormat('en-IN', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

/** Server timestamps are UTC ISO-8601; they are rendered in the browser's local time zone. */
export function formatDate(value?: string | null): string {
  return value ? dateFormatter.format(new Date(value)) : '—';
}

export function formatDateTime(value?: string | null): string {
  return value ? dateTimeFormatter.format(new Date(value)) : '—';
}

export function formatCurrency(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const amount = Number(value);
  if (amount === 0) return 'Free';
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount);
}

export function formatBytes(bytes?: number | null): string {
  if (!bytes) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function humanize(code?: string | null): string {
  if (!code) return '—';
  return code
    .toLowerCase()
    .split('_')
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(' ');
}

export function relativeDays(value?: string | null): string {
  if (!value) return '';
  const diffDays = Math.round((new Date(value).getTime() - Date.now()) / 86_400_000);
  if (diffDays === 0) return 'today';
  if (diffDays > 0) return `in ${diffDays} day${diffDays === 1 ? '' : 's'}`;
  return `${-diffDays} day${diffDays === -1 ? '' : 's'} ago`;
}
