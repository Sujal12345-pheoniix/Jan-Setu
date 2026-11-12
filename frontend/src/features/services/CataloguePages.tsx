import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Clock, FileCheck2, IndianRupee, Search } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorState,
  LinkButton,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  TextInput,
  useToast,
} from '@/components/ui';
import { applicationsApi } from '@/features/applications/api';
import { useDebounced } from '@/hooks/useDebounced';
import { useUrlState } from '@/hooks/useUrlState';
import { ApiError } from '@/api/client';
import { formatBytes, formatCurrency, formatDate, humanize } from '@/utils/format';
import { servicesApi } from './api';

const CATEGORIES = ['CERTIFICATE', 'LICENSE', 'UTILITY', 'WELFARE'];

export function useDepartmentsLookup() {
  return useQuery({
    queryKey: ['departments', 'public'],
    queryFn: ({ signal }) => servicesApi.publicDepartments(signal),
    staleTime: 5 * 60_000,
  });
}

export function ServiceCataloguePage() {
  const { values, page, set } = useUrlState(['q', 'departmentId', 'category'] as const);
  const [search, setSearch] = useState(values.q);
  const debounced = useDebounced(search);
  useEffect(() => {
    if (debounced !== values.q) set({ q: debounced });
  }, [debounced, values.q, set]);

  const departments = useDepartmentsLookup();
  const services = useQuery({
    queryKey: ['services', 'public', values, page],
    queryFn: ({ signal }) =>
      servicesApi.browse({ q: values.q, departmentId: values.departmentId, category: values.category, page, size: 12 }, signal),
    placeholderData: (prev) => prev,
  });
  const deptName = (id: string) => departments.data?.find((d) => d.id === id)?.name ?? 'Department';

  return (
    <div>
      <PageHeader title="Service catalogue" description="Browse published services, their eligibility, required documents and processing time." />
      <div className="mb-5 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-3" role="search">
        <TextInput label="Search services" placeholder="e.g. income certificate" value={search} onChange={(e) => setSearch(e.target.value)} />
        <Select
          label="Department"
          placeholder="All departments"
          value={values.departmentId}
          onChange={(e) => set({ departmentId: e.target.value })}
          options={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
        />
        <Select
          label="Category"
          placeholder="All categories"
          value={values.category}
          onChange={(e) => set({ category: e.target.value })}
          options={CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))}
        />
      </div>

      {services.isError && <ErrorState error={services.error} onRetry={() => services.refetch()} />}
      {services.isPending && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-44" />
          ))}
        </div>
      )}
      {services.data && services.data.content.length === 0 && (
        <EmptyState
          icon={<Search className="h-10 w-10" aria-hidden />}
          title="No services match your filters"
          description="Try a different search term or clear the filters."
          action={<Button variant="secondary" onClick={() => { setSearch(''); set({ q: '', departmentId: '', category: '' }); }}>Clear filters</Button>}
        />
      )}
      {services.data && services.data.content.length > 0 && (
        <>
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-live="polite">
            {services.data.content.map((s) => (
              <li key={s.id} className="flex flex-col rounded-card border border-line bg-white p-5 shadow-sm">
                <div className="flex items-start justify-between gap-2">
                  <Badge tone="info">{humanize(s.category)}</Badge>
                  <span className="text-xs text-muted">{deptName(s.departmentId)}</span>
                </div>
                <h2 className="mt-3 text-base font-semibold">
                  <Link to={`/services/${s.id}`} className="hover:text-brand-700 hover:underline">
                    {s.name}
                  </Link>
                </h2>
                <p className="mt-1 line-clamp-3 flex-1 text-sm text-muted">{s.description}</p>
                <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted">
                  <span className="inline-flex items-center gap-1"><Clock className="h-3.5 w-3.5" aria-hidden />{s.processingDays} days</span>
                  <span className="inline-flex items-center gap-1"><IndianRupee className="h-3.5 w-3.5" aria-hidden />{formatCurrency(s.feeAmount)}</span>
                </div>
              </li>
            ))}
          </ul>
          <Pagination
            page={services.data.page}
            totalPages={services.data.totalPages}
            totalElements={services.data.totalElements}
            onChange={(p) => set({ page: p })}
          />
        </>
      )}
    </div>
  );
}

export function ServiceDetailPage() {
  const { id = '' } = useParams();
  const { status, can } = useAuth();
  const navigate = useNavigate();
  const { notify } = useToast();
  const departments = useDepartmentsLookup();
  const service = useQuery({ queryKey: ['services', 'public', id], queryFn: ({ signal }) => servicesApi.published(id, signal) });
  const start = useMutation({
    mutationFn: () => applicationsApi.createDraft(id),
    onSuccess: (detail) => {
      notify('Draft application created. Fill in the form and upload documents.');
      navigate(`/my/applications/${detail.summary.id}`);
    },
  });

  if (service.isPending) return <Skeleton className="h-96" />;
  if (service.isError) return <ErrorState error={service.error} />;
  const s = service.data;
  const documents = s.requirements.filter((r) => r.requirementType === 'DOCUMENT');
  const declarations = s.requirements.filter((r) => r.requirementType !== 'DOCUMENT');
  const department = departments.data?.find((d) => d.id === s.departmentId)?.name;

  return (
    <div>
      <PageHeader
        title={s.name}
        description={department}
        breadcrumbs={[{ label: 'Services', to: '/services' }, { label: s.name }]}
        actions={
          status === 'authenticated' ? (
            can('APPLICATION:CREATE') ? (
              <Button onClick={() => start.mutate()} loading={start.isPending}>
                Start application
              </Button>
            ) : null
          ) : (
            <LinkButton to="/login">Sign in to apply</LinkButton>
          )
        }
      />
      {start.isError && (
        <Alert tone="error" className="mb-4">
          {start.error instanceof ApiError ? start.error.message : 'Could not start the application.'}
        </Alert>
      )}
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <Card title="About this service">
            <p className="whitespace-pre-line text-sm">{s.description}</p>
          </Card>
          <Card title="Eligibility">
            <p className="whitespace-pre-line text-sm">{s.eligibilityText ?? 'No specific eligibility criteria are listed.'}</p>
          </Card>
          <Card title="Required documents">
            {documents.length === 0 ? (
              <p className="text-sm text-muted">No documents are required.</p>
            ) : (
              <ul className="space-y-3">
                {documents.map((d) => (
                  <li key={d.code} className="flex gap-3">
                    <FileCheck2 className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" aria-hidden />
                    <div>
                      <p className="text-sm font-medium">
                        {d.name} {d.mandatory ? <Badge tone="warning">Required</Badge> : <Badge>Optional</Badge>}
                      </p>
                      <p className="text-xs text-muted">
                        {(d.allowedMediaTypes ?? 'application/pdf,image/png,image/jpeg').replace(/application\/|image\//g, '').toUpperCase()}
                        {' · max '}
                        {formatBytes(d.maxSizeBytes ?? 5 * 1024 * 1024)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          {declarations.length > 0 && (
            <Card title="Declarations and information">
              <ul className="list-disc space-y-1 pl-5 text-sm">
                {declarations.map((d) => (
                  <li key={d.code}>{d.name}</li>
                ))}
              </ul>
            </Card>
          )}
        </div>
        <Card title="At a glance" className="h-fit">
          <dl className="space-y-3 text-sm">
            <div><dt className="text-muted">Expected processing time</dt><dd className="font-semibold">{s.processingDays} days</dd></div>
            <div><dt className="text-muted">Fee</dt><dd className="font-semibold">{formatCurrency(s.feeAmount)}</dd></div>
            <div><dt className="text-muted">Form fields</dt><dd className="font-semibold">{s.formSchema.fields?.length ?? 0}</dd></div>
            <div><dt className="text-muted">Published</dt><dd>{formatDate(s.publishedAt)} (version {s.version})</dd></div>
          </dl>
          <p className="mt-4 text-xs text-muted">Fees are illustrative; no payment is collected in this simulation.</p>
        </Card>
      </div>
    </div>
  );
}
