import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { Card, EmptyState, ErrorState, LinkButton, Skeleton, StatCard, StatusBadge } from '@/components/ui';
import { reportsApi } from '@/features/analytics/api';
import { applicationsApi } from '@/features/applications/api';
import { grievancesApi } from '@/features/grievances/api';
import { notificationsApi } from '@/features/notifications/api';
import { tasksApi } from '@/features/tasks/api';
import { formatDate, formatDateTime } from '@/utils/format';

/** One dashboard, composed from widgets the user's permissions allow (UX only; data is scoped by the API). */
export function DashboardPage() {
  const { user, can } = useAuth();
  const isCitizen = can('APPLICATION:READ_OWN');
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Welcome, {user?.user.fullName.split(' ')[0]}</h1>
        <p className="text-sm text-muted">{isCitizen ? 'Here is the latest on your applications and grievances.' : 'Here is your work overview.'}</p>
      </div>
      {isCitizen && <CitizenWidgets />}
      {can('TASK:READ') && <WorkWidgets />}
      {(can('REPORT:READ_DEPARTMENT') || can('REPORT:READ_ALL')) && <ReportWidgets />}
      {can('AUDIT:READ') && (
        <Card title="Audit">
          <p className="text-sm text-muted">Search and export audit records of security-sensitive and administrative actions.</p>
          <LinkButton className="mt-3" to="/audit" variant="secondary">Open audit log</LinkButton>
        </Card>
      )}
      {can('USER:CREATE') && (
        <Card title="Platform administration">
          <div className="flex flex-wrap gap-2">
            <LinkButton to="/admin/users" variant="secondary">Users</LinkButton>
            <LinkButton to="/admin/departments" variant="secondary">Departments</LinkButton>
            <LinkButton to="/admin/roles" variant="secondary">Roles</LinkButton>
            <LinkButton to="/admin/system" variant="secondary">System health</LinkButton>
          </div>
        </Card>
      )}
      <RecentNotifications />
    </div>
  );
}

function CitizenWidgets() {
  const apps = useQuery({ queryKey: ['applications', 'mine', 'dashboard'], queryFn: ({ signal }) => applicationsApi.list({ size: 100 }, signal) });
  const grievances = useQuery({ queryKey: ['grievances', 'mine', 'dashboard'], queryFn: ({ signal }) => grievancesApi.list({ size: 100 }, signal) });
  if (apps.isPending || grievances.isPending) return <Skeleton className="h-40" />;
  if (apps.isError) return <ErrorState error={apps.error} />;
  const list = apps.data.content;
  const pending = list.filter((a) => ['SUBMITTED', 'UNDER_REVIEW', 'RESUBMITTED', 'PENDING_APPROVAL', 'CLARIFICATION_REQUIRED'].includes(a.status));
  const action = list.filter((a) => a.status === 'CLARIFICATION_REQUIRED' || a.status === 'DRAFT');
  const completed = list.filter((a) => ['APPROVED', 'COMPLETED'].includes(a.status));
  const openGrievances = (grievances.data?.content ?? []).filter((g) => !['RESOLVED', 'CLOSED'].includes(g.status));
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard label="In progress" value={pending.length} tone="info" />
        <StatCard label="Need your action" value={action.length} tone={action.length ? 'warning' : 'neutral'} hint="Drafts and clarifications" />
        <StatCard label="Approved / completed" value={completed.length} tone="success" />
        <StatCard label="Open grievances" value={openGrievances.length} />
      </div>
      <Card title="Your applications" actions={<LinkButton size="sm" variant="secondary" to="/my/applications">View all</LinkButton>}>
        {list.length === 0 ? (
          <EmptyState title="No applications yet" action={<LinkButton to="/services">Browse services</LinkButton>} />
        ) : (
          <ul className="divide-y divide-line">
            {list.slice(0, 5).map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                <div>
                  <Link className="font-medium text-brand-700 hover:underline" to={`/my/applications/${a.id}`}>{a.serviceName}</Link>
                  <p className="text-xs text-muted">{a.referenceNumber ?? 'Draft'} · updated {formatDate(a.updatedAt)}</p>
                </div>
                <StatusBadge status={a.status} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  );
}

function WorkWidgets() {
  const mine = useQuery({ queryKey: ['tasks', 'dashboard', 'mine'], queryFn: ({ signal }) => tasksApi.list({ mine: true, size: 5 }, signal) });
  const unassigned = useQuery({ queryKey: ['tasks', 'dashboard', 'unassigned'], queryFn: ({ signal }) => tasksApi.list({ unassigned: true, size: 1 }, signal) });
  const overdue = useQuery({ queryKey: ['tasks', 'dashboard', 'overdue'], queryFn: ({ signal }) => tasksApi.list({ overdue: true, size: 1 }, signal) });
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-3">
        <StatCard label="Assigned to me" value={mine.data?.totalElements ?? '…'} tone="info" />
        <StatCard label="Unassigned in my departments" value={unassigned.data?.totalElements ?? '…'} />
        <StatCard label="Overdue (SLA breached)" value={overdue.data?.totalElements ?? '…'} tone={overdue.data?.totalElements ? 'danger' : 'success'} />
      </div>
      <Card title="My next tasks" actions={<LinkButton size="sm" variant="secondary" to="/work/tasks">Open queue</LinkButton>}>
        {mine.data?.content.length ? (
          <ul className="divide-y divide-line">
            {mine.data.content.map((t) => (
              <li key={t.taskId} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                <Link className="font-medium text-brand-700 hover:underline" to={`/work/applications/${t.applicationId}`}>{t.referenceNumber} · {t.serviceName}</Link>
                <span className={`text-sm ${t.overdue ? 'font-semibold text-red-700' : 'text-muted'}`}>due {formatDate(t.dueAt)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted">No tasks assigned to you.</p>
        )}
      </Card>
    </>
  );
}

function ReportWidgets() {
  const summary = useQuery({ queryKey: ['reports', 'summary', 'dashboard'], queryFn: ({ signal }) => reportsApi.summary({}, signal) });
  if (!summary.data) return null;
  const s = summary.data;
  return (
    <Card title="Last 30 days" actions={<LinkButton size="sm" variant="secondary" to="/reports">Reports</LinkButton>}>
      <div className="grid gap-4 sm:grid-cols-4">
        <StatCard label="Submitted" value={s.submitted} />
        <StatCard label="Decided" value={s.decided} />
        <StatCard label="SLA compliance" value={s.slaCompliancePct === undefined || s.slaCompliancePct === null ? '—' : `${s.slaCompliancePct}%`} tone="success" />
        <StatCard label="Open grievances" value={s.openGrievances} tone={s.openGrievances ? 'warning' : 'neutral'} />
      </div>
    </Card>
  );
}

function RecentNotifications() {
  const query = useQuery({ queryKey: ['notifications', 'dashboard'], queryFn: ({ signal }) => notificationsApi.list({ size: 5 }, signal) });
  return (
    <Card title="Recent notifications" actions={<LinkButton size="sm" variant="secondary" to="/notifications">All notifications</LinkButton>}>
      {query.data?.content.length ? (
        <ul className="divide-y divide-line">
          {query.data.content.map((n) => (
            <li key={n.id} className="py-2.5">
              <p className={`text-sm ${n.readAt ? '' : 'font-semibold'}`}>{n.title}</p>
              <p className="text-xs text-muted">{n.body} · {formatDateTime(n.createdAt)}</p>
            </li>
          ))}
        </ul>
      ) : (
        <p className="text-sm text-muted">No notifications yet.</p>
      )}
    </Card>
  );
}
