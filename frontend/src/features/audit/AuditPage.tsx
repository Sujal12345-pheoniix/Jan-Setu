import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Download } from 'lucide-react';
import { ApiError } from '@/api/client';
import { Alert, Button, DataTable, EmptyState, ErrorState, Modal, PageHeader, Pagination, Select, StatusBadge, TableSkeleton, TextInput } from '@/components/ui';
import { openBlob } from '@/features/documents/DocumentComponents';
import { useUrlState } from '@/hooks/useUrlState';
import type { AuditLogEntry } from '@/types/api';
import { formatDateTime, humanize } from '@/utils/format';
import { auditApi } from './api';

const RESOURCE_TYPES = ['USER', 'ROLE', 'DEPARTMENT', 'SERVICE', 'APPLICATION', 'DOCUMENT', 'GRIEVANCE', 'TASK', 'AUDIT_LOG'];

/** Read-only audit search for auditors. Every search and export is itself recorded in the audit log. */
export function AuditPage() {
  const { values, page, set } = useUrlState(['action', 'resourceType', 'resourceId', 'actorId', 'outcome', 'from', 'to'] as const);
  const [selected, setSelected] = useState<AuditLogEntry | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [exporting, setExporting] = useState(false);
  const filters = {
    action: values.action || undefined,
    resourceType: values.resourceType || undefined,
    resourceId: values.resourceId || undefined,
    actorId: values.actorId || undefined,
    outcome: values.outcome || undefined,
    from: values.from ? new Date(`${values.from}T00:00:00`).toISOString() : undefined,
    to: values.to ? new Date(`${values.to}T23:59:59`).toISOString() : undefined,
  };
  const query = useQuery({
    queryKey: ['audit', filters, page],
    queryFn: ({ signal }) => auditApi.search({ ...filters, page, size: 25 }, signal),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader
        title="Audit log"
        description="Security-sensitive and administrative events. Read-only."
        actions={
          <Button
            variant="secondary"
            icon={<Download className="h-4 w-4" aria-hidden />}
            loading={exporting}
            onClick={async () => {
              setExportError(null);
              setExporting(true);
              try {
                await openBlob(() => auditApi.exportCsv(filters), false);
              } catch (e) {
                setExportError(e instanceof ApiError ? e.message : 'Export failed.');
              } finally {
                setExporting(false);
              }
            }}
          >
            Export CSV
          </Button>
        }
      />
      {exportError && <Alert tone="error" className="mb-3">{exportError}</Alert>}
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3 lg:grid-cols-4" role="search">
        <TextInput label="Action" placeholder="e.g. LOGIN_FAILED" defaultValue={values.action} onBlur={(e) => set({ action: e.target.value.toUpperCase() })} />
        <Select label="Module / resource" placeholder="Any" value={values.resourceType} onChange={(e) => set({ resourceType: e.target.value })} options={RESOURCE_TYPES.map((r) => ({ value: r, label: humanize(r) }))} />
        <TextInput label="Resource ID" defaultValue={values.resourceId} onBlur={(e) => set({ resourceId: e.target.value })} />
        <TextInput label="Actor (user ID)" defaultValue={values.actorId} onBlur={(e) => set({ actorId: e.target.value })} />
        <Select label="Outcome" placeholder="Any" value={values.outcome} onChange={(e) => set({ outcome: e.target.value })} options={['SUCCESS', 'FAILURE', 'DENIED'].map((o) => ({ value: o, label: humanize(o) }))} />
        <TextInput label="From" type="date" value={values.from} onChange={(e) => set({ from: e.target.value })} />
        <TextInput label="To" type="date" value={values.to} onChange={(e) => set({ to: e.target.value })} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No audit events match" />}
      {query.data && query.data.content.length > 0 && (
        <>
          <DataTable<AuditLogEntry>
            caption="Audit events"
            rows={query.data.content}
            rowKey={(r) => r.id}
            onRowClick={setSelected}
            columns={[
              { key: 'time', header: 'Time', render: (r) => formatDateTime(r.occurredAt) },
              { key: 'action', header: 'Action', render: (r) => <button type="button" className="text-left font-medium text-brand-700 hover:underline" onClick={() => setSelected(r)}>{r.action}</button> },
              { key: 'resource', header: 'Resource', hideOnMobile: true, render: (r) => <span>{humanize(r.resourceType)} <span className="block break-all text-xs text-muted">{r.resourceId}</span></span> },
              { key: 'actor', header: 'Actor', hideOnMobile: true, render: (r) => <span className="text-xs">{humanize(r.actorType)} <span className="block break-all text-muted">{r.actorId}</span></span> },
              { key: 'outcome', header: 'Outcome', render: (r) => <StatusBadge status={r.outcome} /> },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
      <Modal open={!!selected} title={selected?.action ?? 'Event'} size="lg" onClose={() => setSelected(null)}>
        {selected && (
          <dl className="grid gap-2 text-sm sm:grid-cols-2">
            {[
              ['Time', formatDateTime(selected.occurredAt)],
              ['Outcome', selected.outcome],
              ['Actor', `${selected.actorType} ${selected.actorId ?? ''}`],
              ['Resource', `${selected.resourceType} ${selected.resourceId ?? ''}`],
              ['Request ID', selected.requestId],
              ['IP address', selected.ipAddress],
            ].map(([k, v]) => (
              <div key={k}><dt className="text-xs uppercase text-muted">{k}</dt><dd className="break-all">{v || '—'}</dd></div>
            ))}
            <div className="sm:col-span-2">
              <dt className="text-xs uppercase text-muted">Details</dt>
              <dd><pre className="mt-1 overflow-x-auto rounded bg-canvas p-2 text-xs">{JSON.stringify(selected.details, null, 2)}</pre></dd>
            </div>
          </dl>
        )}
      </Modal>
    </div>
  );
}
