import type { ReactNode } from 'react';
import { cn } from '@/utils/format';

export interface Column<T> {
  key: string;
  header: string;
  render: (row: T) => ReactNode;
  className?: string;
  /** Hidden on narrow screens; still available on the detail page. */
  hideOnMobile?: boolean;
}

/** Semantic, horizontally scrollable table with a caption for assistive technology. */
export function DataTable<T>({ columns, rows, rowKey, caption, onRowClick }: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  caption: string;
  onRowClick?: (row: T) => void;
}) {
  return (
    <div className="overflow-x-auto rounded-card border border-line bg-white">
      <table className="min-w-full divide-y divide-line text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="bg-canvas">
          <tr>
            {columns.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={cn('px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-wide text-muted', c.hideOnMobile && 'hidden md:table-cell', c.className)}
              >
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {rows.map((row) => (
            <tr
              key={rowKey(row)}
              className={cn(onRowClick && 'cursor-pointer hover:bg-brand-50/50')}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
            >
              {columns.map((c) => (
                <td key={c.key} className={cn('px-3 py-2.5 align-top', c.hideOnMobile && 'hidden md:table-cell', c.className)}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
