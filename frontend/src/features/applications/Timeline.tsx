import { CheckCircle2, Circle } from 'lucide-react';
import { StatusBadge } from '@/components/ui';
import { formatDateTime, humanize } from '@/utils/format';

export interface TimelineEntry {
  toStatus: string;
  title?: string;
  actorName?: string;
  reason?: string;
  note?: string;
  occurredAt: string;
}

/** Ordered status history with timestamps (server-provided; citizens see only citizen-visible entries). */
export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  if (entries.length === 0) return <p className="text-sm text-muted">No history yet.</p>;
  return (
    <ol className="relative space-y-4 border-l border-line pl-5">
      {entries.map((e, i) => {
        const last = i === entries.length - 1;
        return (
          <li key={`${e.occurredAt}-${i}`} className="relative">
            <span className="absolute -left-[27px] top-0.5 bg-white">
              {last ? <CheckCircle2 className="h-4 w-4 text-brand-600" aria-hidden /> : <Circle className="h-4 w-4 text-line" aria-hidden />}
            </span>
            <div className="flex flex-wrap items-center gap-2">
              <StatusBadge status={e.toStatus} />
              {e.title && <span className="text-sm font-medium">{humanize(e.title)}</span>}
            </div>
            <p className="mt-0.5 text-xs text-muted">
              <time dateTime={e.occurredAt}>{formatDateTime(e.occurredAt)}</time>
              {e.actorName && ` · ${e.actorName}`}
            </p>
            {(e.reason || e.note) && <p className="mt-1 whitespace-pre-line rounded bg-canvas p-2 text-sm">{e.reason ?? e.note}</p>}
          </li>
        );
      })}
    </ol>
  );
}
