import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '@/auth/AuthProvider';
import { Card, DataTable, ErrorState, PageHeader, Select, Skeleton, StatCard, TextInput } from '@/components/ui';
import { useDepartmentsLookup } from '@/features/services/CataloguePages';
import type { DepartmentRow, WorkloadRow } from '@/types/api';
import { humanize } from '@/utils/format';
import { reportsApi, type RangeQuery } from './api';

function isoDay(d: Date) {
  return d.toISOString().slice(0, 10);
}

/** Aggregate reporting. All numbers are counts, rates or durations; no citizen personal data is shown. */
export function ReportsPage() {
  const { can } = useAuth();
  const today = new Date();
  const [from, setFrom] = useState(isoDay(new Date(today.getTime() - 29 * 86_400_000)));
  const [to, setTo] = useState(isoDay(today));
  const [departmentId, setDepartmentId] = useState('');
  const departments = useDepartmentsLookup();
  const range: RangeQuery = useMemo(
    () => ({
      from: new Date(`${from}T00:00:00Z`).toISOString(),
      to: new Date(new Date(`${to}T00:00:00Z`).getTime() + 86_400_000).toISOString(),
      departmentId: departmentId || undefined,
    }),
    [from, to, departmentId],
  );
  const summary = useQuery({ queryKey: ['reports', 'summary', range], queryFn: ({ signal }) => reportsApi.summary(range, signal) });
  const volume = useQuery({ queryKey: ['reports', 'volume', range], queryFn: ({ signal }) => reportsApi.volume(range, signal) });
  const perf = useQuery({ queryKey: ['reports', 'departments', range.from, range.to], queryFn: ({ signal }) => reportsApi.departments(range, signal) });
  const workload = useQuery({ queryKey: ['reports', 'workload', departmentId], queryFn: ({ signal }) => reportsApi.workload(range, signal) });
  const grievances = useQuery({ queryKey: ['reports', 'grievances', range], queryFn: ({ signal }) => reportsApi.grievances(range, signal) });
  const s = summary.data;
  const statusData = Object.entries(s?.applicationsByStatus ?? {}).map(([status, count]) => ({ status: humanize(status), count }));

  return (
    <div className="space-y-5">
      <PageHeader title="Reports" description={can('REPORT:READ_ALL') ? 'Platform-wide aggregate metrics.' : 'Aggregate metrics for your departments.'} />
      <div className="grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3" role="search">
        <TextInput label="From" type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
        <TextInput label="To" type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
        <Select label="Department" placeholder="All in scope" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} options={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))} />
      </div>
      {summary.isError && <ErrorState error={summary.error} />}
      {summary.isPending ? (
        <Skeleton className="h-24" />
      ) : s ? (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard label="Applications submitted" value={s.submitted} />
          <StatCard label="Decided (approved / rejected)" value={`${s.decided} (${s.approved} / ${s.rejected})`} />
          <StatCard label="SLA compliance" value={s.slaCompliancePct == null ? '—' : `${s.slaCompliancePct}%`} hint="Decided within processing days" tone="success" />
          <StatCard label="Avg. processing" value={s.avgProcessingDays == null ? '—' : `${s.avgProcessingDays} days`} />
          <StatCard label="Active tasks" value={s.activeTasks} />
          <StatCard label="Overdue tasks" value={s.overdueTasks} tone={s.overdueTasks ? 'danger' : 'success'} />
          <StatCard label="Open grievances" value={s.openGrievances} tone={s.openGrievances ? 'warning' : 'neutral'} />
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Application volume">
          {volume.data ? (
            <figure>
              <div className="h-64">
                <ResponsiveContainer>
                  <LineChart data={volume.data}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#d9e0e5" />
                    <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Legend />
                    <Line type="monotone" dataKey="submitted" name="Submitted" stroke="#0b5f6b" strokeWidth={2} dot={false} />
                    <Line type="monotone" dataKey="decided" name="Decided" stroke="#e19b1a" strokeWidth={2} dot={false} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
              <figcaption className="mt-2 text-xs text-muted">
                Daily totals: {volume.data.reduce((a, d) => a + d.submitted, 0)} submitted, {volume.data.reduce((a, d) => a + d.decided, 0)} decided in the period.
              </figcaption>
            </figure>
          ) : <Skeleton className="h-64" />}
        </Card>
        <Card title="Applications by current status">
          {statusData.length ? (
            <figure>
              <div className="h-64">
                <ResponsiveContainer>
                  <BarChart data={statusData} layout="vertical" margin={{ left: 40 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#d9e0e5" />
                    <XAxis type="number" allowDecimals={false} tick={{ fontSize: 11 }} />
                    <YAxis type="category" dataKey="status" tick={{ fontSize: 11 }} width={140} />
                    <Tooltip />
                    <Bar dataKey="count" name="Applications" fill="#1f818b" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <figcaption className="sr-only">{statusData.map((d) => `${d.status}: ${d.count}`).join(', ')}</figcaption>
            </figure>
          ) : <p className="text-sm text-muted">No applications yet.</p>}
        </Card>
      </div>

      <Card title="Department performance">
        {perf.isError && <ErrorState error={perf.error} />}
        {perf.data && (
          <DataTable<DepartmentRow>
            caption="Department performance"
            rows={perf.data}
            rowKey={(r) => r.departmentId}
            columns={[
              { key: 'name', header: 'Department', render: (r) => r.name },
              { key: 'submitted', header: 'Submitted', render: (r) => r.submitted },
              { key: 'approved', header: 'Approved', render: (r) => r.approved },
              { key: 'rejected', header: 'Rejected', hideOnMobile: true, render: (r) => r.rejected },
              { key: 'pending', header: 'Pending', render: (r) => r.pending },
              { key: 'overdue', header: 'Overdue', render: (r) => <span className={r.overdueTasks ? 'font-semibold text-red-700' : undefined}>{r.overdueTasks}</span> },
              { key: 'sla', header: 'SLA %', render: (r) => (r.slaCompliancePct == null ? '—' : `${r.slaCompliancePct}%`) },
              { key: 'avg', header: 'Avg days', hideOnMobile: true, render: (r) => r.avgProcessingDays ?? '—' },
              { key: 'grv', header: 'Open grievances', hideOnMobile: true, render: (r) => r.openGrievances },
            ]}
          />
        )}
      </Card>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Workload distribution">
          {workload.data && workload.data.length === 0 && <p className="text-sm text-muted">No active work.</p>}
          {workload.data && workload.data.length > 0 && (
            <DataTable<WorkloadRow>
              caption="Workload by staff member"
              rows={workload.data}
              rowKey={(r) => r.assigneeId ?? 'unassigned'}
              columns={[
                { key: 'name', header: 'Staff', render: (r) => r.assigneeName },
                { key: 'active', header: 'Active tasks', render: (r) => r.activeTasks },
                { key: 'overdue', header: 'Overdue', render: (r) => r.overdueTasks },
                { key: 'grv', header: 'Grievances', render: (r) => r.openGrievances },
              ]}
            />
          )}
        </Card>
        <Card title="Grievance trends">
          {grievances.data ? (
            <>
              <div className="h-48">
                <ResponsiveContainer>
                  <BarChart data={grievances.data.daily}>
                    <XAxis dataKey="date" tick={{ fontSize: 10 }} />
                    <YAxis allowDecimals={false} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="opened" name="Opened" fill="#e19b1a" />
                    <Bar dataKey="resolved" name="Resolved" fill="#0b5f6b" />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <ul className="mt-3 space-y-1 text-sm">
                {Object.entries(grievances.data.byCategory).map(([c, n]) => (
                  <li key={c} className="flex justify-between"><span>{humanize(c)}</span><span className="font-semibold">{n}</span></li>
                ))}
              </ul>
            </>
          ) : <Skeleton className="h-48" />}
        </Card>
      </div>
    </div>
  );
}
