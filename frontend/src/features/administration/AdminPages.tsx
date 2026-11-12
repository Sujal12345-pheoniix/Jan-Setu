import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  DataTable,
  DescriptionList,
  EmptyState,
  ErrorState,
  FormErrorSummary,
  Modal,
  PageHeader,
  Pagination,
  ReasonDialog,
  Select,
  Skeleton,
  StatusBadge,
  TableSkeleton,
  TextInput,
  useToast,
} from '@/components/ui';
import { useUrlState } from '@/hooks/useUrlState';
import type { RoleSummary, UserResponse } from '@/types/api';
import { formatDateTime, humanize } from '@/utils/format';
import { passwordSchema } from '@/validation/schemas';
import { rolesApi, usersApi } from './api';

const errs = (e: unknown) => (e instanceof ApiError ? [e.message, ...e.fieldErrors.map((f) => `${f.field}: ${f.message}`)] : e ? ['The request failed.'] : []);

export function UserListPage() {
  const { can } = useAuth();
  const { values, page, set } = useUrlState(['q', 'status', 'userType'] as const);
  const [open, setOpen] = useState(false);
  const query = useQuery({
    queryKey: ['users', values, page],
    queryFn: ({ signal }) => usersApi.list({ q: values.q, status: values.status, userType: values.userType, page, size: 20 }, signal),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader title="Users" description="Accounts within your scope." actions={can('USER:CREATE') && <Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setOpen(true)}>New staff account</Button>} />
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3" role="search">
        <TextInput label="Search" placeholder="Name or email" defaultValue={values.q} onBlur={(e) => set({ q: e.target.value })} />
        <Select label="Status" placeholder="Any" value={values.status} onChange={(e) => set({ status: e.target.value })} options={['ACTIVE', 'SUSPENDED', 'DEACTIVATED'].map((s) => ({ value: s, label: humanize(s) }))} />
        <Select label="Type" placeholder="Any" value={values.userType} onChange={(e) => set({ userType: e.target.value })} options={[{ value: 'CITIZEN', label: 'Citizen' }, { value: 'STAFF', label: 'Staff' }]} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No users match" />}
      {query.data && query.data.content.length > 0 && (
        <>
          <DataTable<UserResponse>
            caption="Users"
            rows={query.data.content}
            rowKey={(u) => u.id}
            columns={[
              { key: 'name', header: 'Name', render: (u) => <Link className="font-medium text-brand-700 hover:underline" to={`/admin/users/${u.id}`}>{u.fullName}</Link> },
              { key: 'email', header: 'Email', hideOnMobile: true, render: (u) => u.email },
              { key: 'type', header: 'Type', render: (u) => humanize(u.userType) },
              { key: 'roles', header: 'Roles', hideOnMobile: true, render: (u) => u.roles.map((r) => <Badge key={r} className="mr-1">{humanize(r)}</Badge>) },
              { key: 'status', header: 'Status', render: (u) => <StatusBadge status={u.status} /> },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
      <CreateStaffDialog open={open} onClose={() => setOpen(false)} />
    </div>
  );
}

function CreateStaffDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const key = useRef(crypto.randomUUID());
  const roles = useQuery({ queryKey: ['roles'], queryFn: ({ signal }) => rolesApi.list(signal), enabled: open });
  const [form, setForm] = useState({ email: '', fullName: '', initialPassword: '', roles: [] as string[] });
  const [localError, setLocalError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: () => usersApi.create(form, key.current),
    onSuccess: (u) => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      key.current = crypto.randomUUID();
      setForm({ email: '', fullName: '', initialPassword: '', roles: [] });
      onClose();
      notify(`Staff account created for ${u.fullName}. They must change the initial password at first sign-in.`);
    },
  });
  const staffRoles = (roles.data?.content ?? []).filter((r) => r.code !== 'CITIZEN');
  return (
    <Modal
      open={open}
      title="New staff account"
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button
            loading={create.isPending}
            onClick={() => {
              const problem = passwordSchema.safeParse(form.initialPassword).error?.issues[0]?.message;
              setLocalError(problem ?? (form.roles.length === 0 ? 'Choose at least one role.' : null));
              if (!problem && form.roles.length > 0) create.mutate();
            }}
          >
            Create account
          </Button>
        </>
      }
    >
      <FormErrorSummary errors={[...(localError ? [localError] : []), ...errs(create.error)]} />
      <div className="grid gap-4 sm:grid-cols-2">
        <TextInput label="Full name" required value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} />
        <TextInput label="Email" type="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
      </div>
      <TextInput label="Initial password" type="password" autoComplete="new-password" required hint="Share securely; the user must change it at first sign-in." value={form.initialPassword} onChange={(e) => setForm({ ...form, initialPassword: e.target.value })} />
      <fieldset>
        <legend className="text-sm font-medium">Roles</legend>
        <div className="mt-2 grid gap-2 sm:grid-cols-2">
          {staffRoles.map((r) => (
            <Checkbox key={r.code} label={`${r.name}${r.systemRole ? '' : ' (custom)'}`} checked={form.roles.includes(r.code)} onChange={(e) => setForm({ ...form, roles: e.target.checked ? [...form.roles, r.code] : form.roles.filter((c) => c !== r.code) })} />
          ))}
        </div>
      </fieldset>
      <p className="text-xs text-muted">Add the account to a department from the department page so it gets data access.</p>
    </Modal>
  );
}

export function UserDetailPage() {
  const { id = '' } = useParams();
  const { can, user: me } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['users', id], queryFn: ({ signal }) => usersApi.get(id, signal) });
  const roles = useQuery({ queryKey: ['roles'], queryFn: ({ signal }) => rolesApi.list(signal), enabled: can('USER:ASSIGN_ROLE') });
  const [statusAction, setStatusAction] = useState<null | 'suspend' | 'deactivate' | 'reactivate'>(null);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
  useEffect(() => setSelectedRoles(query.data?.roles ?? []), [query.data]);
  const setStatus = useMutation({
    mutationFn: ({ action, reason }: { action: 'suspend' | 'deactivate' | 'reactivate'; reason?: string }) => usersApi.setStatus(id, action, reason),
    onSuccess: (u) => { queryClient.setQueryData(['users', id], u); queryClient.invalidateQueries({ queryKey: ['users'] }); setStatusAction(null); notify(`Account ${humanize(u.status).toLowerCase()}.`); },
  });
  const saveRoles = useMutation({
    mutationFn: () => usersApi.replaceRoles(id, selectedRoles),
    onSuccess: (u) => { queryClient.setQueryData(['users', id], u); notify('Roles updated. Changes apply on the user’s next request.'); },
  });
  if (query.isPending) return <Skeleton className="h-96" />;
  if (query.isError) return <ErrorState error={query.error} />;
  const u = query.data;
  const self = me?.user.id === u.id;
  const assignable = (roles.data?.content ?? []).filter((r: RoleSummary) => (u.userType === 'CITIZEN' ? r.code === 'CITIZEN' : r.code !== 'CITIZEN'));
  return (
    <div>
      <PageHeader title={u.fullName} description={u.email} breadcrumbs={[{ label: 'Users', to: '/admin/users' }, { label: u.fullName }]} actions={<StatusBadge status={u.status} />} />
      <FormErrorSummary errors={[...errs(setStatus.error), ...errs(saveRoles.error)]} />
      <div className="grid gap-5 lg:grid-cols-2">
        <Card title="Account">
          <DescriptionList
            items={[
              { label: 'Type', value: humanize(u.userType) },
              { label: 'Phone', value: u.phone },
              { label: 'Status reason', value: u.statusReason },
              { label: 'Last sign-in', value: formatDateTime(u.lastLoginAt) },
              { label: 'Created', value: formatDateTime(u.createdAt) },
              { label: 'Departments', value: u.departmentIds.length ? `${u.departmentIds.length} department(s)` : 'None' },
            ]}
          />
          {can('USER:DEACTIVATE') && !self && (
            <div className="mt-4 flex flex-wrap gap-2">
              {u.status === 'ACTIVE' ? (
                <>
                  <Button variant="secondary" onClick={() => setStatusAction('suspend')}>Suspend</Button>
                  <Button variant="danger" onClick={() => setStatusAction('deactivate')}>Deactivate</Button>
                </>
              ) : (
                <Button onClick={() => setStatusAction('reactivate')}>Reactivate</Button>
              )}
            </div>
          )}
          {self && <Alert tone="info" className="mt-4">You cannot change the status or roles of your own account.</Alert>}
        </Card>
        {can('USER:ASSIGN_ROLE') && !self && (
          <Card title="Roles">
            <div className="grid gap-2 sm:grid-cols-2">
              {assignable.map((r) => (
                <Checkbox key={r.code} label={r.name} checked={selectedRoles.includes(r.code)} onChange={(e) => setSelectedRoles((s) => (e.target.checked ? [...s, r.code] : s.filter((c) => c !== r.code)))} />
              ))}
            </div>
            <p className="mt-3 text-xs text-muted">You can only grant privileged permissions that you hold yourself; the server enforces this.</p>
            <Button className="mt-3" disabled={selectedRoles.length === 0} loading={saveRoles.isPending} onClick={() => saveRoles.mutate()}>Save roles</Button>
          </Card>
        )}
      </div>
      <ReasonDialog
        open={!!statusAction}
        title={`${humanize(statusAction ?? '')} account`}
        label="Reason (recorded in the audit log)"
        confirmLabel={humanize(statusAction ?? '')}
        required={statusAction !== 'reactivate'}
        danger={statusAction !== 'reactivate'}
        loading={setStatus.isPending}
        onCancel={() => setStatusAction(null)}
        onSubmit={(reason) => statusAction && setStatus.mutate({ action: statusAction, reason })}
      />
    </div>
  );
}

export function RolesPage() {
  const { can } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const roles = useQuery({ queryKey: ['roles'], queryFn: ({ signal }) => rolesApi.list(signal) });
  const permissions = useQuery({ queryKey: ['permissions'], queryFn: ({ signal }) => rolesApi.permissions(signal) });
  const [selected, setSelected] = useState<string | null>(null);
  const role = useQuery({ queryKey: ['roles', selected], queryFn: ({ signal }) => rolesApi.get(selected!, signal), enabled: !!selected });
  const [draft, setDraft] = useState<string[]>([]);
  useEffect(() => setDraft(role.data?.permissions ?? []), [role.data]);
  const [createOpen, setCreateOpen] = useState(false);
  const [newRole, setNewRole] = useState({ code: '', name: '', description: '' });
  const save = useMutation({
    mutationFn: () => rolesApi.replacePermissions(selected!, draft, role.data!.version),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['roles'] }); notify('Role permissions saved.'); },
  });
  const create = useMutation({
    mutationFn: () => rolesApi.create({ ...newRole, permissions: [] }),
    onSuccess: (r) => { queryClient.invalidateQueries({ queryKey: ['roles'] }); setCreateOpen(false); setSelected(r.id); notify('Custom role created. Choose its permissions.'); },
  });
  const grouped = Object.entries(
    (permissions.data ?? []).reduce<Record<string, { code: string; description: string }[]>>((acc, p) => {
      (acc[p.category] ??= []).push(p);
      return acc;
    }, {}),
  );
  return (
    <div>
      <PageHeader title="Roles & permissions" description="System roles are defined by database migrations and cannot be modified here. Custom roles can be created and edited." actions={can('ROLE:CREATE') && <Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setCreateOpen(true)}>New custom role</Button>} />
      <div className="grid gap-5 lg:grid-cols-3">
        <Card title="Roles" bodyClassName="p-0">
          {roles.isPending ? <TableSkeleton rows={4} /> : (
            <ul className="divide-y divide-line">
              {(roles.data?.content ?? []).map((r) => (
                <li key={r.id}>
                  <button type="button" onClick={() => setSelected(r.id)} aria-current={selected === r.id} className={`w-full px-4 py-3 text-left hover:bg-canvas ${selected === r.id ? 'bg-brand-50' : ''}`}>
                    <span className="block text-sm font-medium">{r.name} {!r.systemRole && <Badge tone="info">Custom</Badge>}</span>
                    <span className="block text-xs text-muted">{r.description ?? r.code}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card title={role.data ? `Permissions — ${role.data.name}` : 'Permissions'} className="lg:col-span-2">
          {!selected && <p className="text-sm text-muted">Select a role to see its permissions.</p>}
          {role.isPending && selected && <Skeleton className="h-48" />}
          {role.data && (
            <>
              <FormErrorSummary errors={errs(save.error)} />
              <div className="space-y-4">
                {grouped.map(([category, perms]) => (
                  <fieldset key={category}>
                    <legend className="text-xs font-semibold uppercase tracking-wide text-muted">{humanize(category)}</legend>
                    <div className="mt-1 grid gap-1 sm:grid-cols-2">
                      {perms.map((p) => (
                        <Checkbox key={p.code} disabled={role.data.systemRole || !can('ROLE:MANAGE')} label={<span><code className="text-xs">{p.code}</code><span className="block text-xs text-muted">{p.description}</span></span>} checked={draft.includes(p.code)} onChange={(e) => setDraft((d) => (e.target.checked ? [...d, p.code] : d.filter((c) => c !== p.code)))} />
                      ))}
                    </div>
                  </fieldset>
                ))}
              </div>
              {!role.data.systemRole && can('ROLE:MANAGE') && <Button className="mt-4" loading={save.isPending} onClick={() => save.mutate()}>Save permissions</Button>}
            </>
          )}
        </Card>
      </div>
      <Modal open={createOpen} title="New custom role" onClose={() => setCreateOpen(false)} footer={<><Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button><Button loading={create.isPending} onClick={() => create.mutate()}>Create</Button></>}>
        <FormErrorSummary errors={errs(create.error)} />
        <TextInput label="Code" required hint="e.g. DISTRICT_SUPERVISOR" value={newRole.code} onChange={(e) => setNewRole({ ...newRole, code: e.target.value })} />
        <TextInput label="Name" required value={newRole.name} onChange={(e) => setNewRole({ ...newRole, name: e.target.value })} />
        <TextInput label="Description" value={newRole.description} onChange={(e) => setNewRole({ ...newRole, description: e.target.value })} />
      </Modal>
    </div>
  );
}

export function SystemHealthPage() {
  const health = useQuery({
    queryKey: ['actuator', 'health'],
    queryFn: async ({ signal }) => {
      const r = await fetch('/actuator/health', { signal, headers: { Accept: 'application/json' } });
      return (await r.json()) as { status: string; groups?: string[] };
    },
    refetchInterval: 30_000,
  });
  return (
    <div className="max-w-3xl">
      <PageHeader title="System health" description="Read-only platform status. Detailed metrics are exported to Prometheus/Grafana." />
      <div className="grid gap-4 sm:grid-cols-2">
        <Card title="API health">
          {health.isPending ? <Skeleton className="h-10" /> : health.isError ? <Alert tone="error">Health endpoint unreachable.</Alert> : (
            <p className="text-sm">Status: <StatusBadge status={health.data.status === 'UP' ? 'ACTIVE' : 'DEACTIVATED'} /> <span className="ml-1 font-semibold">{health.data.status}</span></p>
          )}
          <p className="mt-2 text-xs text-muted">Probes: {(health.data?.groups ?? []).join(', ') || '—'}</p>
        </Card>
        <Card title="Platform configuration">
          <DescriptionList
            items={[
              { label: 'Access token lifetime', value: '15 minutes' },
              { label: 'Refresh session', value: '7 days, rotated on use' },
              { label: 'Login lockout', value: '5 failures → 15 minutes' },
              { label: 'Upload limit', value: '10 MB (PDF, PNG, JPEG)' },
            ]}
          />
          <p className="mt-3 text-xs text-muted">Configuration is managed through environment variables (see docs/LOCAL_SETUP.md), not this screen.</p>
        </Card>
        <Card title="Notification templates">
          <p className="text-sm text-muted">In-app notification texts are defined in code for this release. Editable templates are planned with the e-mail delivery worker.</p>
        </Card>
        <Card title="API documentation">
          <a className="text-sm text-brand-700 hover:underline" href="/swagger-ui.html" target="_blank" rel="noreferrer">Open Swagger UI</a>
        </Card>
      </div>
    </div>
  );
}
