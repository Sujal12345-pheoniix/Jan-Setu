import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Badge,
  Button,
  Card,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  FormErrorSummary,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  StatusBadge,
  TableSkeleton,
  TextArea,
  TextInput,
  useToast,
} from '@/components/ui';
import { usersApi } from '@/features/administration/api';
import { useUrlState } from '@/hooks/useUrlState';
import type { Department, Member } from '@/types/api';
import { formatDate, humanize } from '@/utils/format';
import { departmentsApi } from './api';

const errs = (e: unknown) => (e instanceof ApiError ? [e.message, ...e.fieldErrors.map((f) => `${f.field}: ${f.message}`)] : e ? ['The request failed.'] : []);

export function DepartmentListPage() {
  const { can } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const { values, page, set } = useUrlState(['q', 'status'] as const);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ code: '', name: '', description: '', contactEmail: '' });
  const key = useRef(crypto.randomUUID());
  const query = useQuery({
    queryKey: ['departments', 'list', values, page],
    queryFn: ({ signal }) => departmentsApi.list({ q: values.q, status: values.status, page, size: 20 }, signal),
    placeholderData: (prev) => prev,
  });
  const create = useMutation({
    mutationFn: () => departmentsApi.create({ ...form, description: form.description || undefined, contactEmail: form.contactEmail || undefined }, key.current),
    onSuccess: (d) => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
      setOpen(false);
      key.current = crypto.randomUUID();
      setForm({ code: '', name: '', description: '', contactEmail: '' });
      notify(`Department ${d.name} created.`);
    },
  });
  return (
    <div>
      <PageHeader
        title="Departments & staff"
        description={can('DEPARTMENT:CREATE') ? 'All departments on the platform.' : 'Departments you administer.'}
        actions={can('DEPARTMENT:CREATE') && <Button icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setOpen(true)}>New department</Button>}
      />
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3" role="search">
        <TextInput label="Search" defaultValue={values.q} onBlur={(e) => set({ q: e.target.value })} />
        <Select label="Status" placeholder="Any" value={values.status} onChange={(e) => set({ status: e.target.value })} options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No departments" />}
      {query.data && query.data.content.length > 0 && (
        <>
          <DataTable<Department>
            caption="Departments"
            rows={query.data.content}
            rowKey={(d) => d.id}
            columns={[
              { key: 'name', header: 'Department', render: (d) => <Link className="font-medium text-brand-700 hover:underline" to={`/admin/departments/${d.id}`}>{d.name}</Link> },
              { key: 'code', header: 'Code', render: (d) => <code className="text-xs">{d.code}</code> },
              { key: 'status', header: 'Status', render: (d) => <StatusBadge status={d.status} /> },
              { key: 'email', header: 'Contact', hideOnMobile: true, render: (d) => d.contactEmail ?? '—' },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
      <Modal open={open} title="New department" onClose={() => setOpen(false)} footer={<><Button variant="secondary" onClick={() => setOpen(false)}>Cancel</Button><Button loading={create.isPending} onClick={() => create.mutate()}>Create</Button></>}>
        <FormErrorSummary errors={errs(create.error)} />
        <TextInput label="Code" required hint="Letters, digits and underscores, e.g. TRANSPORT" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
        <TextInput label="Name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <TextArea label="Description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <TextInput label="Contact email" type="email" value={form.contactEmail} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} />
      </Modal>
    </div>
  );
}

export function DepartmentDetailPage() {
  const { id = '' } = useParams();
  const { can, user } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const dept = useQuery({ queryKey: ['departments', id], queryFn: ({ signal }) => departmentsApi.get(id, signal) });
  const [membersPage, setMembersPage] = useState(0);
  const members = useQuery({ queryKey: ['departments', id, 'members', membersPage], queryFn: ({ signal }) => departmentsApi.members(id, { page: membersPage, size: 20 }, signal) });
  const [edit, setEdit] = useState<{ name: string; description: string; contactEmail: string } | null>(null);
  const [addOpen, setAddOpen] = useState(false);
  const [remove, setRemove] = useState<Member | null>(null);
  const [toggle, setToggle] = useState(false);
  const [newMember, setNewMember] = useState({ userId: '', position: '' });
  const staffSearch = useQuery({
    queryKey: ['users', 'staff-picker'],
    queryFn: ({ signal }) => usersApi.list({ userType: 'STAFF', status: 'ACTIVE', size: 100 }, signal),
    enabled: addOpen && can('USER:READ'),
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['departments'] });
  const update = useMutation({
    mutationFn: () => departmentsApi.update(id, { ...edit, version: dept.data!.version }),
    onSuccess: () => { invalidate(); setEdit(null); notify('Department updated.'); },
  });
  const setActive = useMutation({
    mutationFn: () => departmentsApi.setActive(id, dept.data!.status !== 'ACTIVE'),
    onSuccess: () => { invalidate(); setToggle(false); notify('Department status changed.'); },
  });
  const add = useMutation({
    mutationFn: () => departmentsApi.addMember(id, newMember.userId.trim(), newMember.position || undefined),
    onSuccess: () => { invalidate(); setAddOpen(false); setNewMember({ userId: '', position: '' }); notify('Staff member added.'); },
  });
  const removeMember = useMutation({
    mutationFn: (m: Member) => departmentsApi.removeMember(id, m.userId),
    onSuccess: () => { invalidate(); setRemove(null); notify('Staff member removed.'); },
  });

  if (dept.isPending) return <Skeleton className="h-96" />;
  if (dept.isError) return <ErrorState error={dept.error} />;
  const d = dept.data;
  const canManage = can('DEPARTMENT:MANAGE_MEMBERS');
  return (
    <div>
      <PageHeader
        title={d.name}
        description={d.code}
        breadcrumbs={[{ label: 'Departments', to: '/admin/departments' }, { label: d.name }]}
        actions={
          <>
            <StatusBadge status={d.status} />
            {can('DEPARTMENT:UPDATE') && <Button variant="secondary" onClick={() => setEdit({ name: d.name, description: d.description ?? '', contactEmail: d.contactEmail ?? '' })}>Edit</Button>}
            {can('DEPARTMENT:CREATE') && <Button variant={d.status === 'ACTIVE' ? 'danger' : 'primary'} onClick={() => setToggle(true)}>{d.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>}
          </>
        }
      />
      <div className="grid gap-5 lg:grid-cols-3">
        <Card title="About" className="h-fit">
          <p className="text-sm">{d.description ?? 'No description.'}</p>
          <p className="mt-2 text-sm text-muted">Contact: {d.contactEmail ?? '—'}</p>
        </Card>
        <Card title="Staff" className="lg:col-span-2" actions={canManage && <Button size="sm" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setAddOpen(true)}>Add staff</Button>}>
          {members.isPending && <TableSkeleton rows={3} />}
          {members.data && members.data.content.length === 0 && <EmptyState title="No staff yet" />}
          {members.data && members.data.content.length > 0 && (
            <>
              <DataTable<Member>
                caption="Department staff"
                rows={members.data.content}
                rowKey={(m) => m.userId}
                columns={[
                  { key: 'name', header: 'Name', render: (m) => <span><span className="font-medium">{m.fullName}</span><span className="block text-xs text-muted">{m.email}</span></span> },
                  { key: 'roles', header: 'Roles', render: (m) => m.roles.map((r) => <Badge key={r} className="mr-1">{humanize(r)}</Badge>) },
                  { key: 'position', header: 'Position', hideOnMobile: true, render: (m) => m.position ?? '—' },
                  { key: 'joined', header: 'Joined', hideOnMobile: true, render: (m) => formatDate(m.joinedAt) },
                  { key: 'actions', header: 'Actions', render: (m) => canManage && m.userId !== user?.user.id && <Button size="sm" variant="ghost" className="text-red-700" onClick={() => setRemove(m)}>Remove</Button> },
                ]}
              />
              <Pagination page={members.data.page} totalPages={members.data.totalPages} totalElements={members.data.totalElements} onChange={setMembersPage} />
            </>
          )}
        </Card>
      </div>

      <Modal open={!!edit} title="Edit department" onClose={() => setEdit(null)} footer={<><Button variant="secondary" onClick={() => setEdit(null)}>Cancel</Button><Button loading={update.isPending} onClick={() => update.mutate()}>Save</Button></>}>
        <FormErrorSummary errors={errs(update.error)} />
        {edit && (
          <>
            <TextInput label="Name" required value={edit.name} onChange={(e) => setEdit({ ...edit, name: e.target.value })} />
            <TextArea label="Description" value={edit.description} onChange={(e) => setEdit({ ...edit, description: e.target.value })} />
            <TextInput label="Contact email" type="email" value={edit.contactEmail} onChange={(e) => setEdit({ ...edit, contactEmail: e.target.value })} />
          </>
        )}
      </Modal>
      <Modal open={addOpen} title="Add staff member" onClose={() => setAddOpen(false)} footer={<><Button variant="secondary" onClick={() => setAddOpen(false)}>Cancel</Button><Button disabled={!newMember.userId.trim()} loading={add.isPending} onClick={() => add.mutate()}>Add</Button></>}>
        <FormErrorSummary errors={errs(add.error)} />
        {can('USER:READ') && staffSearch.data && staffSearch.data.content.length > 0 ? (
          <Select label="Staff account" required placeholder="Select a staff account" value={newMember.userId} onChange={(e) => setNewMember({ ...newMember, userId: e.target.value })} options={staffSearch.data.content.map((u) => ({ value: u.id, label: `${u.fullName} (${u.email})` }))} />
        ) : (
          <TextInput label="Staff user ID" required hint="Ask a platform administrator for the account ID of the new staff member." value={newMember.userId} onChange={(e) => setNewMember({ ...newMember, userId: e.target.value })} />
        )}
        <TextInput label="Position (optional)" value={newMember.position} onChange={(e) => setNewMember({ ...newMember, position: e.target.value })} />
      </Modal>
      <ConfirmDialog open={!!remove} title="Remove staff member?" message={`${remove?.fullName} will immediately lose access to this department's cases.`} confirmLabel="Remove" danger loading={removeMember.isPending} onConfirm={() => remove && removeMember.mutate(remove)} onCancel={() => setRemove(null)} />
      <ConfirmDialog open={toggle} title={d.status === 'ACTIVE' ? 'Deactivate department?' : 'Activate department?'} message={d.status === 'ACTIVE' ? 'No new services can be published and no new grievances can be routed to an inactive department.' : 'The department becomes available again.'} confirmLabel={d.status === 'ACTIVE' ? 'Deactivate' : 'Activate'} danger={d.status === 'ACTIVE'} loading={setActive.isPending} onConfirm={() => setActive.mutate()} onCancel={() => setToggle(false)} />
    </div>
  );
}
