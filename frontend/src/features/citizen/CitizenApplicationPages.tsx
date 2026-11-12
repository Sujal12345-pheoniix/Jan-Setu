import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useParams } from 'react-router-dom';
import { CheckCircle2, FilePlus2 } from 'lucide-react';
import { ApiError } from '@/api/client';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  DescriptionList,
  EmptyState,
  ErrorState,
  FormErrorSummary,
  LinkButton,
  PageHeader,
  Pagination,
  Skeleton,
  StatusBadge,
  TableSkeleton,
  Tabs,
  TextArea,
  useToast,
} from '@/components/ui';
import { DocumentCard, DocumentUploader } from '@/features/documents/DocumentComponents';
import { useUrlState } from '@/hooks/useUrlState';
import type { ApplicationDetail, ApplicationSummary, RequirementSnapshot } from '@/types/api';
import { formatDate, formatDateTime, relativeDays } from '@/utils/format';
import { applicationsApi } from '../applications/api';
import { AnswerList, buildFormSchema, DynamicForm, toFormValues, toPayload, type FormValues } from '../applications/DynamicForm';
import { Timeline } from '../applications/Timeline';

const GROUPS = {
  all: undefined,
  draft: 'DRAFT',
  action: 'CLARIFICATION_REQUIRED',
  approved: 'APPROVED',
  completed: 'COMPLETED',
  rejected: 'REJECTED',
} as const;
type Group = keyof typeof GROUPS;

export function MyApplicationsPage() {
  const { values, page, set } = useUrlState(['tab'] as const);
  const tab = (values.tab || 'all') as Group;
  const list = useQuery({
    queryKey: ['applications', 'mine', tab, page],
    queryFn: ({ signal }) => applicationsApi.list({ status: GROUPS[tab], page, size: 20 }, signal),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader
        title="My applications"
        description="Drafts, submitted applications and decisions."
        actions={<LinkButton to="/services" icon={<FilePlus2 className="h-4 w-4" aria-hidden />}>New application</LinkButton>}
      />
      <Tabs<Group>
        label="Filter applications"
        value={tab}
        onChange={(t) => set({ tab: t === 'all' ? '' : t })}
        tabs={[
          { value: 'all', label: 'All' },
          { value: 'draft', label: 'Drafts' },
          { value: 'action', label: 'Action needed' },
          { value: 'approved', label: 'Approved' },
          { value: 'completed', label: 'Completed' },
          { value: 'rejected', label: 'Rejected' },
        ]}
      />
      <div className="mt-4">
        {list.isPending && <TableSkeleton />}
        {list.isError && <ErrorState error={list.error} onRetry={() => list.refetch()} />}
        {list.data && list.data.content.length === 0 && (
          <EmptyState
            title="No applications here yet"
            description="Browse the catalogue to start a new application."
            action={<LinkButton to="/services">Browse services</LinkButton>}
          />
        )}
        {list.data && list.data.content.length > 0 && (
          <>
            <ApplicationTable rows={list.data.content} linkBase="/my/applications" />
            <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onChange={(p) => set({ page: p })} />
          </>
        )}
      </div>
    </div>
  );
}

export function ApplicationTable({ rows, linkBase, staff }: { rows: ApplicationSummary[]; linkBase: string; staff?: boolean }) {
  return (
    <DataTable
      caption="Applications"
      rows={rows}
      rowKey={(r) => r.id}
      columns={[
        {
          key: 'ref',
          header: 'Reference',
          render: (r) => (
            <Link className="font-medium text-brand-700 hover:underline" to={`${linkBase}/${r.id}`}>
              {r.referenceNumber ?? 'Draft'}
            </Link>
          ),
        },
        { key: 'service', header: 'Service', render: (r) => r.serviceName },
        ...(staff ? [{ key: 'applicant', header: 'Applicant', render: (r: ApplicationSummary) => r.applicantName ?? '—', hideOnMobile: true }] : []),
        { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
        ...(staff
          ? [{ key: 'assignee', header: 'Assignee', render: (r: ApplicationSummary) => r.assigneeName ?? <span className="text-muted">Unassigned</span>, hideOnMobile: true }]
          : []),
        {
          key: 'due',
          header: staff ? 'Due' : 'Updated',
          hideOnMobile: true,
          render: (r) =>
            staff && r.dueAt ? (
              <span className={r.overdue ? 'font-semibold text-red-700' : undefined}>
                {formatDate(r.dueAt)} {r.overdue && <Badge tone="danger">Overdue</Badge>}
              </span>
            ) : (
              formatDate(r.updatedAt)
            ),
        },
      ]}
    />
  );
}

/** Citizen workspace: edit a draft (form → documents → review & submit) or track a submitted application. */
export function CitizenApplicationPage() {
  const { id = '' } = useParams();
  const query = useQuery({ queryKey: ['applications', id], queryFn: ({ signal }) => applicationsApi.get(id, signal) });
  if (query.isPending) return <Skeleton className="h-96" />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const app = query.data;
  return (
    <div>
      <PageHeader
        title={app.service.name}
        description={app.summary.referenceNumber ? `Reference ${app.summary.referenceNumber}` : 'Draft application — not yet submitted'}
        breadcrumbs={[{ label: 'My applications', to: '/my/applications' }, { label: app.summary.referenceNumber ?? 'Draft' }]}
        actions={<StatusBadge status={app.summary.status} />}
      />
      {app.summary.status === 'DRAFT' ? <DraftWizard app={app} /> : <TrackingView app={app} />}
    </div>
  );
}

function DraftWizard({ app }: { app: ApplicationDetail }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [step, setStep] = useState<0 | 1 | 2>(0);
  const [serverErrors, setServerErrors] = useState<string[]>([]);
  const [confirmSubmit, setConfirmSubmit] = useState(false);
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);
  const [submitted, setSubmitted] = useState<string | null>(null);
  const idempotencyKey = useRef(crypto.randomUUID());
  const fields = useMemo(() => app.service.formSchema.fields ?? [], [app.service.formSchema]);
  const declarations = app.service.requirements.filter((r) => r.requirementType === 'DECLARATION');
  const documentRequirements = app.service.requirements.filter((r) => r.requirementType === 'DOCUMENT');
  const [accepted, setAccepted] = useState<string[]>(
    Array.isArray(app.formData._declarations) ? (app.formData._declarations as string[]) : [],
  );
  const [forSubmit, setForSubmit] = useState(false);
  const form = useForm<FormValues>({
    resolver: zodResolver(buildFormSchema(fields, forSubmit)),
    defaultValues: toFormValues(fields, app.formData),
  });

  const refresh = (detail?: ApplicationDetail) => {
    if (detail) queryClient.setQueryData(['applications', app.summary.id], detail);
    queryClient.invalidateQueries({ queryKey: ['applications', 'mine'] });
  };

  const save = useMutation({
    mutationFn: (values: FormValues) => applicationsApi.saveDraft(app.summary.id, toPayload(fields, values, accepted), app.summary.version),
    onSuccess: (detail) => {
      setServerErrors([]);
      refresh(detail);
    },
    onError: (e) => {
      if (e instanceof ApiError) {
        e.fieldErrors.forEach((fe) => {
          const key = fe.field.replace(/^formData\./, '');
          if (fields.some((f) => f.key === key)) form.setError(key, { message: fe.message });
        });
        setServerErrors(e.status === 409 ? ['This draft was changed elsewhere. Reload the page to continue.'] : [e.message]);
      }
    },
  });

  const submit = useMutation({
    mutationFn: () => applicationsApi.submit(app.summary.id, idempotencyKey.current),
    onSuccess: (result) => {
      setSubmitted(result.referenceNumber);
      queryClient.invalidateQueries({ queryKey: ['applications'] });
      notify(`Application submitted. Reference ${result.referenceNumber}.`);
    },
    onError: (e) => {
      setConfirmSubmit(false);
      setServerErrors(e instanceof ApiError ? [e.message, ...e.fieldErrors.map((f) => `${f.field.replace('formData.', '')}: ${f.message}`)] : ['Submission failed.']);
    },
  });

  const withdraw = useMutation({
    mutationFn: () => applicationsApi.withdraw(app.summary.id),
    onSuccess: (detail) => {
      refresh(detail);
      notify('Draft withdrawn.');
    },
  });

  useEffect(() => {
    setForSubmit(step === 2);
  }, [step]);

  /** Saves the draft; errors are already surfaced by the mutation's onError, so callers only need the outcome. */
  const trySave = async (values: FormValues): Promise<boolean> => {
    try {
      await save.mutateAsync(values);
      return true;
    } catch {
      return false;
    }
  };

  if (submitted) {
    return (
      <Card>
        <div className="flex flex-col items-center py-6 text-center">
          <CheckCircle2 className="h-12 w-12 text-green-700" aria-hidden />
          <h2 className="mt-3 text-xl font-bold">Application submitted</h2>
          <p className="mt-1 text-muted">Your application number is</p>
          <p className="mt-1 text-2xl font-bold tracking-wide text-brand-700" data-testid="reference-number">{submitted}</p>
          <p className="mt-3 max-w-md text-sm text-muted">Keep this number to track your application. You will be notified about every status change.</p>
          <Button className="mt-5" onClick={() => queryClient.invalidateQueries({ queryKey: ['applications', app.summary.id] })}>
            Track this application
          </Button>
        </div>
      </Card>
    );
  }

  const missingDocs = documentRequirements.filter((r) => r.mandatory && !app.documents.some((d) => d.requirementCode === r.code));
  const missingDeclarations = declarations.filter((r) => r.mandatory && !accepted.includes(r.code));
  const steps = ['Application details', 'Documents', 'Review & submit'];

  return (
    <div className="space-y-4">
      <ol className="flex flex-wrap gap-2" aria-label="Application steps">
        {steps.map((label, i) => (
          <li key={label}>
            <button
              type="button"
              onClick={() => setStep(i as 0 | 1 | 2)}
              aria-current={step === i ? 'step' : undefined}
              className={`rounded-full px-3 py-1.5 text-sm ${step === i ? 'bg-brand-600 text-white' : 'bg-white text-ink ring-1 ring-line hover:bg-canvas'}`}
            >
              {i + 1}. {label}
            </button>
          </li>
        ))}
      </ol>
      <FormErrorSummary errors={serverErrors} />

      {step === 0 && (
        <Card title="Application details" actions={<span className="text-xs text-muted">Version {app.service.versionNumber} of the form</span>}>
          <form
            noValidate
            onSubmit={form.handleSubmit(async (values) => {
              if (await trySave(values)) {
                notify('Draft saved.');
                setStep(1);
              }
            })}
            className="space-y-5"
          >
            <DynamicForm fields={fields} form={form} />
            <div className="flex flex-wrap justify-between gap-2">
              <Button variant="ghost" onClick={() => setConfirmWithdraw(true)}>Discard draft</Button>
              <div className="flex gap-2">
                <Button
                  variant="secondary"
                  loading={save.isPending}
                  onClick={form.handleSubmit(async (values) => {
                    if (await trySave(values)) notify('Draft saved.');
                  })}
                >
                  Save draft
                </Button>
                <Button type="submit" loading={save.isPending}>Save and continue</Button>
              </div>
            </div>
          </form>
        </Card>
      )}

      {step === 1 && (
        <Card title="Supporting documents">
          {documentRequirements.length === 0 && <p className="text-sm text-muted">This service does not need documents.</p>}
          <div className="space-y-4">
            {documentRequirements.map((r) => (
              <RequirementUpload key={r.code} app={app} requirement={r} onUploaded={() => queryClient.invalidateQueries({ queryKey: ['applications', app.summary.id] })} />
            ))}
          </div>
          <div className="mt-5 flex justify-between">
            <Button variant="secondary" onClick={() => setStep(0)}>Back</Button>
            <Button onClick={() => setStep(2)}>Continue to review</Button>
          </div>
        </Card>
      )}

      {step === 2 && (
        <div className="space-y-4">
          <Card title="Your answers" actions={<Button size="sm" variant="ghost" onClick={() => setStep(0)}>Edit</Button>}>
            <AnswerList fields={fields} data={app.formData} />
          </Card>
          <Card title="Documents" actions={<Button size="sm" variant="ghost" onClick={() => setStep(1)}>Edit</Button>}>
            <ul className="space-y-1 text-sm">
              {documentRequirements.map((r) => {
                const doc = app.documents.find((d) => d.requirementCode === r.code);
                return (
                  <li key={r.code}>
                    {doc ? '✓' : '✗'} {r.name}: {doc ? doc.fileName : <span className="text-red-700">{r.mandatory ? 'missing (required)' : 'not provided'}</span>}
                  </li>
                );
              })}
            </ul>
          </Card>
          {declarations.length > 0 && (
            <Card title="Declarations">
              <div className="space-y-3">
                {declarations.map((r) => (
                  <Checkbox
                    key={r.code}
                    label={`${r.name}${r.mandatory ? ' (required)' : ''}`}
                    checked={accepted.includes(r.code)}
                    onChange={(e) => setAccepted((a) => (e.target.checked ? [...a, r.code] : a.filter((c) => c !== r.code)))}
                  />
                ))}
              </div>
            </Card>
          )}
          {(missingDocs.length > 0 || missingDeclarations.length > 0) && (
            <Alert tone="warning" title="Before you submit">
              <ul className="list-disc pl-5">
                {missingDocs.map((d) => <li key={d.code}>Upload: {d.name}</li>)}
                {missingDeclarations.map((d) => <li key={d.code}>Accept: {d.name}</li>)}
              </ul>
            </Alert>
          )}
          <div className="flex justify-between">
            <Button variant="secondary" onClick={() => setStep(1)}>Back</Button>
            <Button
              loading={save.isPending}
              onClick={form.handleSubmit(async (values) => {
                if (await trySave(values)) setConfirmSubmit(true);
              }, () => {
                setServerErrors(['Some answers are missing or invalid. Go back to "Application details" to fix them.']);
              })}
            >
              Submit application
            </Button>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmSubmit}
        title="Submit application?"
        message="After submission you cannot edit your answers. The department may contact you for clarification."
        confirmLabel="Submit"
        loading={submit.isPending}
        onConfirm={() => submit.mutate()}
        onCancel={() => setConfirmSubmit(false)}
      />
      <ConfirmDialog
        open={confirmWithdraw}
        title="Discard this draft?"
        message="The draft will be withdrawn and cannot be submitted later."
        confirmLabel="Discard draft"
        danger
        loading={withdraw.isPending}
        onConfirm={() => withdraw.mutate()}
        onCancel={() => setConfirmWithdraw(false)}
      />
    </div>
  );
}

function RequirementUpload({ app, requirement, onUploaded }: { app: ApplicationDetail; requirement: RequirementSnapshot; onUploaded: () => void }) {
  const doc = app.documents.find((d) => d.requirementCode === requirement.code);
  const { notify } = useToast();
  const canUpload = app.availableActions.includes('UPLOAD_DOCUMENT') && doc?.status !== 'VERIFIED';
  return (
    <div className="rounded-md border border-line p-3">
      <p className="text-sm font-medium">
        {requirement.name} {requirement.mandatory ? <Badge tone="warning">Required</Badge> : <Badge>Optional</Badge>}
      </p>
      {requirement.description && <p className="text-xs text-muted">{requirement.description}</p>}
      {doc && (
        <div className="mt-2">
          <DocumentCard document={doc} title="Uploaded file" onDownload={(v) => applicationsApi.downloadDocument(app.summary.id, doc.id, v)} />
        </div>
      )}
      {canUpload && (
        <div className="mt-2">
          <DocumentUploader
            label={doc ? 'Replace file' : 'Upload file'}
            allowedTypes={requirement.allowedMediaTypes}
            maxBytes={requirement.maxSizeBytes}
            onUpload={async (file, onProgress, signal) => {
              await applicationsApi.uploadDocument(app.summary.id, requirement.code, file, onProgress, signal);
              notify(`${requirement.name} uploaded.`);
              onUploaded();
            }}
          />
        </div>
      )}
    </div>
  );
}

function TrackingView({ app }: { app: ApplicationDetail }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [response, setResponse] = useState('');
  const [confirmWithdraw, setConfirmWithdraw] = useState(false);
  const openClarification = app.clarifications.find((c) => c.status === 'OPEN');
  const fields = app.service.formSchema.fields ?? [];
  const finalDecision = [...app.decisions].reverse().find((d) => d.decision === 'APPROVED' || d.decision === 'REJECTED');
  const set = (detail: ApplicationDetail) => {
    queryClient.setQueryData(['applications', app.summary.id], detail);
    queryClient.invalidateQueries({ queryKey: ['applications', 'mine'] });
    queryClient.invalidateQueries({ queryKey: ['notifications'] });
  };
  const respond = useMutation({
    mutationFn: () => applicationsApi.respondToClarification(app.summary.id, response),
    onSuccess: (d) => {
      set(d);
      setResponse('');
      notify('Your response was sent to the department.');
    },
  });
  const withdraw = useMutation({
    mutationFn: () => applicationsApi.withdraw(app.summary.id),
    onSuccess: (d) => {
      set(d);
      setConfirmWithdraw(false);
      notify('Application withdrawn.');
    },
  });

  return (
    <div className="grid gap-5 lg:grid-cols-3">
      <div className="space-y-5 lg:col-span-2">
        {openClarification && (
          <Card title="Clarification requested" className="border-amber-300">
            <Alert tone="warning" title={`Please respond by ${formatDate(openClarification.dueAt)} (${relativeDays(openClarification.dueAt)})`}>
              <p className="whitespace-pre-line">{openClarification.question}</p>
            </Alert>
            <div className="mt-4 space-y-4">
              {app.service.requirements.filter((r) => r.requirementType === 'DOCUMENT').map((r) => (
                <RequirementUpload key={r.code} app={app} requirement={r} onUploaded={() => queryClient.invalidateQueries({ queryKey: ['applications', app.summary.id] })} />
              ))}
              <TextArea label="Your response" required value={response} maxLength={4000} onChange={(e) => setResponse(e.target.value)} />
              {respond.isError && <Alert tone="error">{respond.error instanceof ApiError ? respond.error.message : 'Could not send your response.'}</Alert>}
              <Button disabled={response.trim().length === 0} loading={respond.isPending} onClick={() => respond.mutate()}>
                Send response
              </Button>
            </div>
          </Card>
        )}
        {finalDecision && (
          <Card title="Decision">
            <Alert tone={finalDecision.decision === 'APPROVED' ? 'success' : 'error'} title={finalDecision.decision === 'APPROVED' ? 'Approved' : 'Rejected'}>
              <p>Decided on {formatDateTime(finalDecision.decidedAt)}</p>
              {finalDecision.reason && <p className="mt-1 whitespace-pre-line">{finalDecision.reason}</p>}
            </Alert>
          </Card>
        )}
        <Card title="Status history">
          <Timeline entries={app.history.map((h) => ({ ...h, title: h.command }))} />
        </Card>
        <Card title="Your answers">
          <AnswerList fields={fields} data={app.formData} />
        </Card>
      </div>
      <div className="space-y-5">
        <Card title="Summary">
          <DescriptionList
            items={[
              { label: 'Reference', value: app.summary.referenceNumber },
              { label: 'Submitted', value: formatDateTime(app.summary.submittedAt) },
              { label: 'Expected processing', value: `${app.service.processingDays} days` },
              { label: 'Current stage', value: app.summary.currentStep === 'APPROVAL' ? 'Final approval' : app.summary.currentStep === 'REVIEW' ? 'Department review' : '—' },
            ]}
          />
          {app.availableActions.includes('WITHDRAW') && (
            <Button className="mt-4" variant="secondary" onClick={() => setConfirmWithdraw(true)}>Withdraw application</Button>
          )}
        </Card>
        <Card title="Documents">
          {app.documents.length === 0 ? (
            <p className="text-sm text-muted">No documents.</p>
          ) : (
            <div className="space-y-3">
              {app.documents.map((d) => (
                <DocumentCard key={d.id} document={d} title={app.service.requirements.find((r) => r.code === d.requirementCode)?.name ?? 'Document'} onDownload={(v) => applicationsApi.downloadDocument(app.summary.id, d.id, v)} />
              ))}
            </div>
          )}
        </Card>
        <Card title="Need help?">
          <p className="text-sm text-muted">If something is wrong with how your application is handled, you can raise a grievance.</p>
          <LinkButton className="mt-3" variant="secondary" size="sm" to={`/my/grievances/new?applicationId=${app.summary.id}`}>Raise a grievance</LinkButton>
        </Card>
      </div>
      <ConfirmDialog
        open={confirmWithdraw}
        title="Withdraw application?"
        message="Withdrawing ends the application. You would need to apply again."
        confirmLabel="Withdraw"
        danger
        loading={withdraw.isPending}
        onConfirm={() => withdraw.mutate()}
        onCancel={() => setConfirmWithdraw(false)}
      />
    </div>
  );
}
