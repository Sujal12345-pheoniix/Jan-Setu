import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Plus, Trash2 } from 'lucide-react';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  FormErrorSummary,
  LinkButton,
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
import { departmentsApi } from '@/features/departments/api';
import { useUrlState } from '@/hooks/useUrlState';
import type { FieldType, FormField, ServiceDetail, ServiceSummary } from '@/types/api';
import { formatCurrency, formatDateTime, humanize } from '@/utils/format';
import { servicesApi } from './api';

const CATEGORIES = ['CERTIFICATE', 'LICENSE', 'UTILITY', 'WELFARE', 'PERMIT', 'OTHER'];
const FIELD_TYPES: FieldType[] = ['text', 'textarea', 'number', 'date', 'email', 'phone', 'checkbox', 'select'];

function errorText(e: unknown): string[] {
  if (!(e instanceof ApiError)) return e ? ['The request failed.'] : [];
  return [e.message, ...e.fieldErrors.map((f) => `${f.field}: ${f.message}`)];
}

export function ServiceManageListPage() {
  const { can } = useAuth();
  const { values, page, set } = useUrlState(['status', 'q'] as const);
  const query = useQuery({
    queryKey: ['services', 'manage', values, page],
    queryFn: ({ signal }) => servicesApi.manageList({ status: values.status, q: values.q, page, size: 20 }, signal),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader
        title="Service catalogue"
        description="Draft, publish and maintain services for your departments. Citizens only see published versions."
        actions={can('SERVICE:CREATE') && <LinkButton to="/admin/services/new" icon={<Plus className="h-4 w-4" aria-hidden />}>New service</LinkButton>}
      />
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3" role="search">
        <TextInput label="Search" defaultValue={values.q} onBlur={(e) => set({ q: e.target.value })} />
        <Select label="Status" placeholder="Any status" value={values.status} onChange={(e) => set({ status: e.target.value })} options={['DRAFT', 'PUBLISHED', 'DEPRECATED'].map((s) => ({ value: s, label: humanize(s) }))} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No services yet" description="Create a draft service to get started." />}
      {query.data && query.data.content.length > 0 && (
        <>
          <DataTable<ServiceSummary>
            caption="Services"
            rows={query.data.content}
            rowKey={(s) => s.id}
            columns={[
              { key: 'name', header: 'Service', render: (s) => <Link className="font-medium text-brand-700 hover:underline" to={`/admin/services/${s.id}`}>{s.name}</Link> },
              { key: 'code', header: 'Code', hideOnMobile: true, render: (s) => <code className="text-xs">{s.code}</code> },
              { key: 'status', header: 'Status', render: (s) => <span className="flex flex-wrap gap-1"><StatusBadge status={s.status} />{s.hasUnpublishedChanges && s.status !== 'DRAFT' && <Badge tone="warning">Unpublished changes</Badge>}</span> },
              { key: 'version', header: 'Version', render: (s) => (s.currentVersion ? `v${s.currentVersion}` : '—') },
              { key: 'updated', header: 'Updated', hideOnMobile: true, render: (s) => formatDateTime(s.updatedAt) },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
    </div>
  );
}

export function NewServicePage() {
  const navigate = useNavigate();
  const { user, can } = useAuth();
  const { notify } = useToast();
  const key = useRef(crypto.randomUUID());
  const departments = useQuery({ queryKey: ['departments', 'mine'], queryFn: ({ signal }) => departmentsApi.list({ size: 100, status: 'ACTIVE' }, signal) });
  const [form, setForm] = useState({ departmentId: '', code: '', name: '', description: '', category: 'CERTIFICATE', processingDays: '15', feeAmount: '0', eligibilityText: '' });
  const create = useMutation({
    mutationFn: () =>
      servicesApi.create(
        { ...form, processingDays: Number(form.processingDays), feeAmount: form.feeAmount === '' ? 0 : Number(form.feeAmount), eligibilityText: form.eligibilityText || undefined, formSchema: { fields: [] } },
        key.current,
      ),
    onSuccess: (s) => {
      notify('Draft service created.');
      navigate(`/admin/services/${s.id}`);
    },
  });
  useEffect(() => {
    if (!form.departmentId && departments.data?.content.length) setForm((f) => ({ ...f, departmentId: departments.data!.content[0].id }));
  }, [departments.data, form.departmentId]);
  if (!can('SERVICE:CREATE')) return <Alert tone="error">You cannot create services.</Alert>;
  // Functional updates: never overwrite a value set concurrently (e.g. the default department loaded async).
  const update = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const value = e.target.value;
    setForm((f) => ({ ...f, [k]: value }));
  };
  return (
    <div className="max-w-2xl">
      <PageHeader title="New service" breadcrumbs={[{ label: 'Service catalogue', to: '/admin/services' }, { label: 'New' }]} />
      <Card>
        <form className="space-y-4" noValidate onSubmit={(e) => { e.preventDefault(); create.mutate(); }}>
          <FormErrorSummary errors={errorText(create.error)} />
          <Select label="Department" required value={form.departmentId} onChange={update('departmentId')} options={(departments.data?.content ?? []).map((d) => ({ value: d.id, label: d.name }))} hint={user?.permissions.includes('SCOPE:ALL_DEPARTMENTS') ? undefined : 'Only your departments are listed.'} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextInput label="Code" required hint="e.g. BIRTH_CERT" value={form.code} onChange={update('code')} />
            <Select label="Category" required value={form.category} onChange={update('category')} options={CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} />
          </div>
          <TextInput label="Name" required value={form.name} onChange={update('name')} />
          <TextArea label="Description" required value={form.description} onChange={update('description')} />
          <TextArea label="Eligibility" value={form.eligibilityText} onChange={update('eligibilityText')} />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextInput label="Processing time (days)" type="number" min={1} max={365} required value={form.processingDays} onChange={update('processingDays')} hint="Also the SLA target for the case." />
            <TextInput label="Fee (INR, illustrative)" type="number" min={0} step="0.01" value={form.feeAmount} onChange={update('feeAmount')} />
          </div>
          <div className="flex justify-end gap-2">
            <LinkButton variant="secondary" to="/admin/services">Cancel</LinkButton>
            <Button type="submit" loading={create.isPending}>Create draft</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

export function ServiceEditorPage() {
  const { id = '' } = useParams();
  const { can } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['services', 'manage', id], queryFn: ({ signal }) => servicesApi.manageGet(id, signal) });
  const [confirm, setConfirm] = useState<null | 'publish' | 'deprecate'>(null);
  const [reqOpen, setReqOpen] = useState(false);
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['services'] });
  };
  const publish = useMutation({
    mutationFn: (s: ServiceDetail) => servicesApi.publish(s.id, s.version),
    onSuccess: (r) => { refresh(); setConfirm(null); notify(`Published version ${r.version}.`); },
  });
  const deprecate = useMutation({
    mutationFn: () => servicesApi.deprecate(id),
    onSuccess: () => { refresh(); setConfirm(null); notify('Service deprecated.'); },
  });
  const removeReq = useMutation({ mutationFn: (rid: string) => servicesApi.removeRequirement(id, rid), onSuccess: refresh });

  if (query.isPending) return <Skeleton className="h-96" />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const s = query.data;
  const editable = s.status !== 'DEPRECATED' && can('SERVICE:UPDATE');
  return (
    <div>
      <PageHeader
        title={s.name}
        description={`${s.code} · ${s.currentVersion ? `published v${s.currentVersion}` : 'never published'}`}
        breadcrumbs={[{ label: 'Service catalogue', to: '/admin/services' }, { label: s.name }]}
        actions={
          <>
            <StatusBadge status={s.status} />
            {can('SERVICE:PUBLISH') && s.status !== 'DEPRECATED' && (
              <Button disabled={!s.hasUnpublishedChanges} onClick={() => setConfirm('publish')}>Publish{s.hasUnpublishedChanges ? ' changes' : 'ed'}</Button>
            )}
            {can('SERVICE:PUBLISH') && s.status === 'PUBLISHED' && <Button variant="secondary" onClick={() => setConfirm('deprecate')}>Deprecate</Button>}
          </>
        }
      />
      <FormErrorSummary errors={[...errorText(publish.error), ...errorText(deprecate.error), ...errorText(removeReq.error)]} />
      {s.hasUnpublishedChanges && s.status === 'PUBLISHED' && (
        <Alert tone="warning" className="mb-4">This service has changes citizens cannot see yet. Publish to create version {s.currentVersion + 1}.</Alert>
      )}
      <div className="grid gap-5 lg:grid-cols-2">
        <DetailsEditor service={s} editable={editable} onSaved={refresh} />
        <div className="space-y-5">
          <FormBuilder service={s} editable={editable} onSaved={refresh} />
          <Card title="Requirements" actions={editable && <Button size="sm" variant="secondary" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setReqOpen(true)}>Add</Button>}>
            {s.requirements.length === 0 ? (
              <p className="text-sm text-muted">No requirements.</p>
            ) : (
              <ul className="divide-y divide-line">
                {s.requirements.map((r) => (
                  <li key={r.id} className="flex items-start justify-between gap-2 py-2">
                    <div>
                      <p className="text-sm font-medium">{r.name} <Badge>{humanize(r.requirementType)}</Badge> {r.mandatory && <Badge tone="warning">Required</Badge>}</p>
                      <p className="text-xs text-muted"><code>{r.code}</code>{r.allowedMediaTypes && ` · ${r.allowedMediaTypes}`}</p>
                    </div>
                    {editable && (
                      <Button size="sm" variant="ghost" aria-label={`Remove ${r.name}`} icon={<Trash2 className="h-4 w-4" aria-hidden />} onClick={() => removeReq.mutate(r.id)} />
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>
      <RequirementDialog open={reqOpen} serviceId={id} onClose={() => setReqOpen(false)} onSaved={() => { setReqOpen(false); refresh(); notify('Requirement added.'); }} />
      <ConfirmDialog open={confirm === 'publish'} title="Publish this service?" message={`Version ${s.currentVersion + 1} becomes immutable and visible to citizens. Existing applications keep the version they used.`} confirmLabel="Publish" loading={publish.isPending} onConfirm={() => publish.mutate(s)} onCancel={() => setConfirm(null)} />
      <ConfirmDialog open={confirm === 'deprecate'} title="Deprecate this service?" message="Citizens will no longer be able to apply. Existing applications are not affected. This cannot be undone." confirmLabel="Deprecate" danger loading={deprecate.isPending} onConfirm={() => deprecate.mutate()} onCancel={() => setConfirm(null)} />
    </div>
  );
}

function DetailsEditor({ service, editable, onSaved }: { service: ServiceDetail; editable: boolean; onSaved: () => void }) {
  const { notify } = useToast();
  const [form, setForm] = useState({ name: service.name, description: service.description, category: service.category, eligibilityText: service.eligibilityText ?? '', processingDays: String(service.processingDays), feeAmount: String(service.feeAmount) });
  useEffect(() => {
    setForm({ name: service.name, description: service.description, category: service.category, eligibilityText: service.eligibilityText ?? '', processingDays: String(service.processingDays), feeAmount: String(service.feeAmount) });
  }, [service]);
  const save = useMutation({
    mutationFn: () => servicesApi.update(service.id, { ...form, processingDays: Number(form.processingDays), feeAmount: Number(form.feeAmount), version: service.version }),
    onSuccess: () => { onSaved(); notify('Service details saved (unpublished).'); },
  });
  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const value = e.target.value;
    setForm((f) => ({ ...f, [k]: value }));
  };
  return (
    <Card title="Details">
      <form className="space-y-4" noValidate onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <FormErrorSummary errors={errorText(save.error)} />
        <TextInput label="Name" required disabled={!editable} value={form.name} onChange={set('name')} />
        <TextArea label="Description" required disabled={!editable} value={form.description} onChange={set('description')} />
        <Select label="Category" disabled={!editable} value={form.category} onChange={set('category')} options={CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} />
        <TextArea label="Eligibility" disabled={!editable} value={form.eligibilityText} onChange={set('eligibilityText')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <TextInput label="Processing days (SLA)" type="number" min={1} max={365} disabled={!editable} value={form.processingDays} onChange={set('processingDays')} />
          <TextInput label="Fee (INR)" type="number" min={0} step="0.01" disabled={!editable} value={form.feeAmount} onChange={set('feeAmount')} hint={formatCurrency(form.feeAmount)} />
        </div>
        {editable && <Button type="submit" loading={save.isPending}>Save details</Button>}
      </form>
    </Card>
  );
}

function FormBuilder({ service, editable, onSaved }: { service: ServiceDetail; editable: boolean; onSaved: () => void }) {
  const { notify } = useToast();
  const [fields, setFields] = useState<FormField[]>(service.formSchema.fields ?? []);
  useEffect(() => setFields(service.formSchema.fields ?? []), [service]);
  const save = useMutation({
    mutationFn: () =>
      servicesApi.update(service.id, {
        formSchema: { fields: fields.map((f) => ({ ...f, options: f.type === 'select' ? f.options : undefined })) },
        version: service.version,
      }),
    onSuccess: () => { onSaved(); notify('Form saved (unpublished).'); },
  });
  const update = (i: number, patch: Partial<FormField>) => setFields((all) => all.map((f, j) => (j === i ? { ...f, ...patch } : f)));
  return (
    <Card title="Application form" actions={editable && <Button size="sm" variant="secondary" icon={<Plus className="h-4 w-4" aria-hidden />} onClick={() => setFields((f) => [...f, { key: `field${f.length + 1}`, label: 'New field', type: 'text', required: false }])}>Add field</Button>}>
      <FormErrorSummary errors={errorText(save.error)} />
      {fields.length === 0 && <p className="text-sm text-muted">No fields yet.</p>}
      <ol className="space-y-3">
        {fields.map((f, i) => (
          <li key={i} className="rounded-md border border-line p-3">
            <div className="grid gap-3 sm:grid-cols-3">
              <TextInput label="Key" disabled={!editable} value={f.key} onChange={(e) => update(i, { key: e.target.value })} />
              <TextInput label="Label" disabled={!editable} value={f.label} onChange={(e) => update(i, { label: e.target.value })} />
              <Select label="Type" disabled={!editable} value={f.type} onChange={(e) => update(i, { type: e.target.value as FieldType })} options={FIELD_TYPES.map((t) => ({ value: t, label: humanize(t) }))} />
            </div>
            {f.type === 'select' && (
              <TextInput className="mt-3" label="Options (comma separated)" disabled={!editable} value={(f.options ?? []).join(', ')} onChange={(e) => update(i, { options: e.target.value.split(',').map((o) => o.trim()).filter(Boolean) })} />
            )}
            <div className="mt-2 flex items-center justify-between">
              <Checkbox label="Required" disabled={!editable} checked={!!f.required} onChange={(e) => update(i, { required: e.target.checked })} />
              {editable && <Button size="sm" variant="ghost" className="text-red-700" onClick={() => setFields((all) => all.filter((_, j) => j !== i))}>Remove</Button>}
            </div>
          </li>
        ))}
      </ol>
      {editable && <Button className="mt-3" loading={save.isPending} onClick={() => save.mutate()}>Save form</Button>}
    </Card>
  );
}

function RequirementDialog({ open, serviceId, onClose, onSaved }: { open: boolean; serviceId: string; onClose: () => void; onSaved: () => void }) {
  const [form, setForm] = useState({ code: '', name: '', requirementType: 'DOCUMENT', mandatory: true, allowedMediaTypes: 'application/pdf,image/png,image/jpeg', maxSizeBytes: '5242880' });
  const add = useMutation({
    mutationFn: () =>
      servicesApi.addRequirement(serviceId, {
        code: form.code,
        name: form.name,
        requirementType: form.requirementType,
        mandatory: form.mandatory,
        allowedMediaTypes: form.requirementType === 'DOCUMENT' ? form.allowedMediaTypes : undefined,
        maxSizeBytes: form.requirementType === 'DOCUMENT' ? Number(form.maxSizeBytes) : undefined,
      }),
    onSuccess: () => { setForm({ ...form, code: '', name: '' }); onSaved(); },
  });
  return (
    <Modal open={open} title="Add requirement" onClose={onClose} footer={<><Button variant="secondary" onClick={onClose}>Cancel</Button><Button loading={add.isPending} onClick={() => add.mutate()}>Add requirement</Button></>}>
      <FormErrorSummary errors={errorText(add.error)} />
      <TextInput label="Code" required hint="e.g. ADDRESS_PROOF" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
      <TextInput label="Name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      <Select label="Type" value={form.requirementType} onChange={(e) => setForm({ ...form, requirementType: e.target.value })} options={['DOCUMENT', 'DECLARATION', 'INFORMATION'].map((t) => ({ value: t, label: humanize(t) }))} />
      {form.requirementType === 'DOCUMENT' && (
        <>
          <TextInput label="Allowed types" value={form.allowedMediaTypes} onChange={(e) => setForm({ ...form, allowedMediaTypes: e.target.value })} hint="Subset of application/pdf, image/png, image/jpeg" />
          <TextInput label="Maximum size (bytes)" type="number" value={form.maxSizeBytes} onChange={(e) => setForm({ ...form, maxSizeBytes: e.target.value })} />
        </>
      )}
      <Checkbox label="Mandatory" checked={form.mandatory} onChange={(e) => setForm({ ...form, mandatory: e.target.checked })} />
    </Modal>
  );
}
