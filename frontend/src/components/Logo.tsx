/** JanSetu wordmark: a bridge ("setu") motif. An original mark, not derived from any official emblem. */
export function Logo({ compact = false }: { compact?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2">
      <svg viewBox="0 0 32 32" className="h-8 w-8" aria-hidden>
        <rect width="32" height="32" rx="7" fill="#0b5f6b" />
        <path d="M6 21c3-6 7-9 10-9s7 3 10 9" fill="none" stroke="#f4b740" strokeWidth="2.6" strokeLinecap="round" />
        <path d="M9 21v4M16 13v12M23 21v4" stroke="#fff" strokeWidth="2.2" strokeLinecap="round" />
      </svg>
      {!compact && (
        <span className="leading-tight">
          <span className="block text-lg font-bold text-brand-700">JanSetu</span>
          <span className="block text-[11px] text-muted">Service delivery &amp; grievances · demo</span>
        </span>
      )}
    </span>
  );
}
