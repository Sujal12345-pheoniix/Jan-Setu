import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Lock, MessageSquarePlus, Star } from 'lucide-react';
import type { z } from 'zod';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  ConfirmDialog,
  DataTable,
  DescriptionList,
  EmptyState,
  ErrorState,
  LinkButton,
  Modal,
  PageHeader,
  Pagination,
  ReasonDialog,
  Select,
  Skeleton,
  StatusBadge,
  TableSkeleton,
  TextArea,
  TextInput,
  useToast,
} from '@/components/ui';
import { DocumentCard, DocumentUploader } from '@/features/documents/DocumentComponents';
import { useDepartmentsLookup } from '@/features/services/CataloguePages';
import { departmentsApi } from '@/features/departments/api';
import { Timeline } from '@/features/applications/Timeline';
import { useUrlState } from '@/hooks/useUrlState';
import type { GrievanceDetail, GrievanceSummary } from '@/types/api';
import { formatDate, formatDateTime, humanize, relativeDays } from '@/utils/format';
import { grievanceSchema } from '@/validation/schemas';
import { GRIEVANCE_CATEGORIES, grievancesApi } from './api';

const STATUSES = ['SUBMITTED', 'ACKNOWLEDGED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REOPENED'];

/** List used by citizens (/my/grievances) and staff (/work/grievances); the backend scopes the results. */
export function GrievanceListPage({ staff }: { staff?: boolean }) {
  const { values, page, set } = useUrlState(['status', 'category', 'q', 'mine'] as const);
  const base = staff ? '/work/grievances' : '/my/grievances';
  const list = useQuery({
    queryKey: ['grievances', staff ? 'staff' : 'mine', values, page],
    queryFn: ({ signal }) =>
      grievancesApi.list(
        { status: values.status, category: values.category, q: values.q, assignedToMe: values.mine === 'true' || undefined, page, size: 20 },
        signal,
      ),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader
        title={staff ? 'Department grievances' : 'My grievances'}
        description={staff ? 'Grievances raised against your departments.' : 'Track complaints you have raised and the department’s replies.'}
        actions={!staff && <LinkButton to="/my/grievances/new" icon={<MessageSquarePlus className="h-4 w-4" aria-hidden />}>Raise a grievance</LinkButton>}
      />
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-4" role="search">
        <TextInput label="Search" placeholder="Reference or subject" defaultValue={values.q} onBlur={(e) => set({ q: e.target.value })} />
        <Select label="Status" placeholder="Any status" value={values.status} onChange={(e) => set({ status: e.target.value })} options={STATUSES.map((s) => ({ value: s, label: humanize(s) }))} />
        <Select label="Category" placeholder="Any category" value={values.category} onChange={(e) => set({ category: e.target.value })} options={GRIEVANCE_CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))} />
        {staff && (
          <Select label="Assigned" value={values.mine} onChange={(e) => set({ mine: e.target.value })} options={[{ value: '', label: 'Everyone' }, { value: 'true', label: 'Assigned to me' }]} />
        )}
      </div>
      {list.isPending && <TableSkeleton />}
      {list.isError && <ErrorState error={list.error} onRetry={() => list.refetch()} />}
      {list.data && list.data.content.length === 0 && (
        <EmptyState title="No grievances found" description={staff ? 'Nothing matches these filters.' : 'You have not raised any grievances.'} />
      )}
      {list.data && list.data.content.length > 0 && (
        <>
          <DataTable<GrievanceSummary>
            caption="Grievances"
            rows={list.data.content}
            rowKey={(r) => r.id}
            columns={[
              { key: 'ref', header: 'Reference', render: (r) => <Link className="font-medium text-brand-700 hover:underline" to={`${base}/${r.id}`}>{r.referenceNumber}</Link> },
              { key: 'subject', header: 'Subject', render: (r) => r.subject },
              { key: 'category', header: 'Category', hideOnMobile: true, render: (r) => humanize(r.category) },
              { key: 'department', header: 'Department', hideOnMobile: true, render: (r) => r.departmentName },
              ...(staff ? [{ key: 'assignee', header: 'Assignee', hideOnMobile: true, render: (r: GrievanceSummary) => r.assigneeName ?? <span className="text-muted">Unassigned</span> }] : []),
              { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
              { key: 'updated', header: 'Updated', hideOnMobile: true, render: (r) => formatDate(r.updatedAt) },
            ]}
          />
          <Pagination page={list.data.page} totalPages={list.data.totalPages} totalElements={list.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
    </div>
  );
}

export function NewGrievancePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [params] = useSearchParams();
  const applicationId = params.get('applicationId') ?? undefined;
  const departments = useDepartmentsLookup();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof grievanceSchema>>({
    resolver: zodResolver(grievanceSchema),
    defaultValues: { applicationId, category: applicationId ? 'SERVICE_DELAY' : '' },
  });
  const errors = form.formState.errors;
  return (
    <div className="max-w-2xl">
      <PageHeader title="Raise a grievance" breadcrumbs={[{ label: 'My grievances', to: '/my/grievances' }, { label: 'New' }]} />
      <Card>
        <form
          noValidate
          className="space-y-4"
          onSubmit={form.handleSubmit(async (values) => {
            setError(null);
            if (!values.departmentId && !values.applicationId) {
              form.setError('departmentId', { message: 'Choose the department concerned' });
              return;
            }
            try {
              const created = await grievancesApi.create({ ...values, departmentId: values.departmentId || undefined });
              queryClient.invalidateQueries({ queryKey: ['grievances'] });
              notify(`Grievance ${created.summary.referenceNumber} registered.`);
              navigate(`/my/grievances/${created.summary.id}`);
            } catch (e) {
              setError(e instanceof ApiError ? e.message : 'Could not register the grievance.');
            }
          })}
        >
          {error && <Alert tone="error">{error}</Alert>}
          {applicationId ? (
            <Alert tone="info">This grievance will be linked to your application and routed to its department.</Alert>
          ) : (
            <Select
              label="Department"
              required
              placeholder="Select a department"
              options={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
              {...form.register('departmentId')}
              error={errors.departmentId?.message}
            />
          )}
          <Select
            label="Category"
            required
            placeholder="Select a category"
            options={GRIEVANCE_CATEGORIES.map((c) => ({ value: c, label: humanize(c) }))}
            {...form.register('category')}
            error={errors.category?.message}
          />
          <TextInput label="Subject" required maxLength={200} {...form.register('subject')} error={errors.subject?.message} />
          <TextArea label="Describe the issue" required rows={6} maxLength={4000} hint="Do not include real identity numbers or personal data." {...form.register('description')} error={errors.description?.message} />
          <p className="text-xs text-muted">You can attach supporting evidence after the grievance is registered.</p>
          <div className="flex justify-end gap-2">
            <LinkButton variant="secondary" to="/my/grievances">Cancel</LinkButton>
            <Button type="submit" loading={form.formState.isSubmitting}>Submit grievance</Button>
          </div>
        </form>
      </Card>
    </div>
  );
}

export function GrievanceDetailPage({ staff }: { staff?: boolean }) {
  const { id = '' } = useParams();
  const { user, can } = useAuth();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const query = useQuery({ queryKey: ['grievances', id], queryFn: ({ signal }) => grievancesApi.get(id, signal) });
  const [dialog, setDialog] = useState<null | 'resolve' | 'reopen' | 'assign' | 'close' | 'feedback'>(null);
  const [comment, setComment] = useState('');
  const [visibility, setVisibility] = useState<'PUBLIC' | 'INTERNAL'>('PUBLIC');
  const [rating, setRating] = useState(5);
  const [feedbackText, setFeedbackText] = useState('');
  const [assignee, setAssignee] = useState('');
  const g = query.data;
  const members = useQuery({
    queryKey: ['departments', g?.summary.departmentId, 'members'],
    queryFn: ({ signal }) => departmentsApi.members(g!.summary.departmentId, { size: 100 }, signal),
    enabled: !!g && !!staff && can('GRIEVANCE:ASSIGN'),
  });
  const act = useMutation({
    mutationFn: ({ action, body }: { action: string; body?: Record<string, unknown> }) => grievancesApi.action(id, action, body),
    onSuccess: (detail: GrievanceDetail, vars) => {
      queryClient.setQueryData(['grievances', id], detail);
      queryClient.invalidateQueries({ queryKey: ['grievances', staff ? 'staff' : 'mine'] });
      setDialog(null);
      if (vars.action === 'comments') setComment(''); // never discard a draft comment on other actions
      notify(vars.action === 'comments' ? 'Comment added.' : `Grievance ${humanize(detail.summary.status).toLowerCase()}.`);
    },
  });
  const actionError = act.error instanceof ApiError ? act.error.message : act.error ? 'The action failed.' : undefined;

  if (query.isPending) return <Skeleton className="h-96" />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const detail = query.data;
  const a = detail.availableActions;
  const eligible = (members.data?.content ?? []).filter((m) => m.status === 'ACTIVE' && m.roles.includes('OFFICER'));

  return (
    <div>
      <PageHeader
        title={detail.summary.subject}
        description={`${detail.summary.referenceNumber} · ${detail.summary.departmentName ?? ''} · ${humanize(detail.summary.category)}`}
        breadcrumbs={[{ label: staff ? 'Grievances' : 'My grievances', to: staff ? '/work/grievances' : '/my/grievances' }, { label: detail.summary.referenceNumber }]}
        actions={<StatusBadge status={detail.summary.status} />}
      />
      {actionError && <Alert tone="error" className="mb-4">{actionError}</Alert>}
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <Card title="Description">
            <p className="whitespace-pre-line text-sm">{detail.description}</p>
            {detail.applicationId && (
              <p className="mt-3 text-sm">
                Linked application:{' '}
                <Link className="text-brand-700 hover:underline" to={staff ? `/work/applications/${detail.applicationId}` : `/my/applications/${detail.applicationId}`}>
                  view
                </Link>
              </p>
            )}
          </Card>
          {detail.resolutionSummary && (
            <Card title="Resolution">
              <Alert tone="success">{detail.resolutionSummary}</Alert>
              {detail.reopenDeadline && detail.summary.status === 'RESOLVED' && (
                <p className="mt-2 text-xs text-muted">Can be reopened until {formatDateTime(detail.reopenDeadline)} ({relativeDays(detail.reopenDeadline)}).</p>
              )}
            </Card>
          )}
          <Card title="Conversation">
            {detail.comments.length === 0 ? (
              <p className="text-sm text-muted">No messages yet.</p>
            ) : (
              <ul className="space-y-3">
                {detail.comments.map((c) => (
                  <li key={c.id} className={`rounded-md p-3 text-sm ${c.visibility === 'INTERNAL' ? 'border border-dashed border-amber-400 bg-amber-50' : c.byCitizen ? 'bg-canvas' : 'bg-brand-50'}`}>
                    <p className="flex flex-wrap items-center gap-2 text-xs text-muted">
                      <span className="font-medium text-ink">{c.authorName ?? (c.byCitizen ? 'Citizen' : 'Department')}</span>
                      <span>{formatDateTime(c.createdAt)}</span>
                      {c.visibility === 'INTERNAL' && <Badge tone="warning"><Lock className="mr-1 h-3 w-3" aria-hidden />Internal note</Badge>}
                    </p>
                    <p className="mt-1 whitespace-pre-line">{c.body}</p>
                  </li>
                ))}
              </ul>
            )}
            {a.includes('COMMENT') && (
              <div className="mt-4 space-y-2">
                <TextArea label={staff ? 'Add a reply or note' : 'Add a comment'} value={comment} maxLength={4000} onChange={(e) => setComment(e.target.value)} />
                <div className="flex flex-wrap items-center justify-end gap-2">
                  {staff && (
                    <Select
                      label="Visibility"
                      className="w-56"
                      value={visibility}
                      onChange={(e) => setVisibility(e.target.value as 'PUBLIC' | 'INTERNAL')}
                      options={[{ value: 'PUBLIC', label: 'Public reply (citizen sees it)' }, { value: 'INTERNAL', label: 'Internal note (staff only)' }]}
                    />
                  )}
                  <Button disabled={!comment.trim()} loading={act.isPending} onClick={() => act.mutate({ action: 'comments', body: { visibility: staff ? visibility : 'PUBLIC', body: comment } })}>
                    {staff && visibility === 'INTERNAL' ? 'Add internal note' : 'Send'}
                  </Button>
                </div>
              </div>
            )}
          </Card>
          <Card title="History">
            <Timeline entries={detail.history.map((h) => ({ toStatus: h.toStatus, actorName: h.actorName, note: h.note, occurredAt: h.occurredAt }))} />
          </Card>
        </div>
        <div className="space-y-5">
          <Card title="Actions">
            <div className="flex flex-col gap-2">
              {a.includes('ACKNOWLEDGE') && <Button onClick={() => act.mutate({ action: 'acknowledge' })} loading={act.isPending}>Acknowledge</Button>}
              {a.includes('ASSIGN') && <Button variant="secondary" onClick={() => setDialog('assign')}>{detail.summary.assigneeId ? 'Reassign' : 'Assign'}</Button>}
              {a.includes('START') && <Button onClick={() => act.mutate({ action: 'start' })} loading={act.isPending}>Start work</Button>}
              {a.includes('RESOLVE') && <Button onClick={() => setDialog('resolve')}>Resolve</Button>}
              {a.includes('REOPEN') && <Button variant="secondary" onClick={() => setDialog('reopen')}>Reopen (issue not solved)</Button>}
              {a.includes('CLOSE') && <Button variant="secondary" onClick={() => setDialog('close')}>{staff ? 'Close' : 'Accept and close'}</Button>}
              {a.includes('FEEDBACK') && <Button variant="secondary" icon={<Star className="h-4 w-4" aria-hidden />} onClick={() => setDialog('feedback')}>Rate the resolution</Button>}
              {a.length === 0 || (a.length === 1 && a[0] === 'COMMENT') ? <p className="text-sm text-muted">No actions available in the current status.</p> : null}
            </div>
          </Card>
          <Card title="Details">
            <DescriptionList
              items={[
                { label: 'Raised', value: formatDateTime(detail.summary.createdAt) },
                { label: 'Acknowledged', value: formatDateTime(detail.acknowledgedAt) },
                ...(staff ? [{ label: 'Assignee', value: detail.summary.assigneeName ?? 'Unassigned' }, { label: 'Citizen', value: detail.summary.citizenName }] : []),
                { label: 'Resolved', value: formatDateTime(detail.resolvedAt) },
                { label: 'Reopened', value: `${detail.reopenCount} time(s)` },
              ]}
            />
            {detail.feedback && (
              <p className="mt-3 text-sm">
                Feedback: {'★'.repeat(detail.feedback.rating)}{'☆'.repeat(5 - detail.feedback.rating)} {detail.feedback.comment}
              </p>
            )}
          </Card>
          <Card title="Evidence">
            <div className="space-y-3">
              {detail.documents.length === 0 && <p className="text-sm text-muted">No files attached.</p>}
              {detail.documents.map((d) => (
                <DocumentCard key={d.id} document={d} title="Evidence" onDownload={() => grievancesApi.downloadEvidence(id, d.id)} />
              ))}
              {!staff && detail.summary.status !== 'CLOSED' && detail.summary.status !== 'RESOLVED' && user && (
                <DocumentUploader
                  label="Attach evidence"
                  onUpload={async (file, onProgress) => {
                    await grievancesApi.uploadEvidence(id, file, onProgress);
                    queryClient.invalidateQueries({ queryKey: ['grievances', id] });
                    notify('Evidence attached.');
                  }}
                />
              )}
            </div>
          </Card>
        </div>
      </div>

      <ReasonDialog open={dialog === 'resolve'} title="Resolve grievance" label="Resolution summary (shown to the citizen)" confirmLabel="Resolve" required loading={act.isPending} error={actionError} onCancel={() => setDialog(null)} onSubmit={(text) => act.mutate({ action: 'resolve', body: { text } })} />
      <ReasonDialog open={dialog === 'reopen'} title="Reopen grievance" label="Why is the issue not solved?" confirmLabel="Reopen" required loading={act.isPending} error={actionError} onCancel={() => setDialog(null)} onSubmit={(text) => act.mutate({ action: 'reopen', body: { text } })} />
      <ConfirmDialog open={dialog === 'close'} title="Close grievance?" message="A closed grievance cannot receive further comments or be reopened." confirmLabel="Close grievance" loading={act.isPending} onCancel={() => setDialog(null)} onConfirm={() => act.mutate({ action: 'close' })} />
      <Modal
        open={dialog === 'assign'}
        title="Assign grievance"
        onClose={() => setDialog(null)}
        footer={<><Button variant="secondary" onClick={() => setDialog(null)}>Cancel</Button><Button disabled={!assignee} loading={act.isPending} onClick={() => act.mutate({ action: 'assign', body: { assigneeId: assignee } })}>Assign</Button></>}
      >
        <Select label="Officer" required placeholder="Select an officer" value={assignee} onChange={(e) => setAssignee(e.target.value)} options={eligible.map((m) => ({ value: m.userId, label: `${m.fullName} (${m.roles.map(humanize).join(', ')})` }))} />
        <p className="text-xs text-muted">Only active department members who can respond to grievances are eligible; the server checks this again.</p>
      </Modal>
      <Modal
        open={dialog === 'feedback'}
        title="Rate the resolution"
        onClose={() => setDialog(null)}
        footer={<><Button variant="secondary" onClick={() => setDialog(null)}>Cancel</Button><Button loading={act.isPending} onClick={() => act.mutate({ action: 'feedback', body: { rating, comment: feedbackText || undefined } })}>Submit feedback</Button></>}
      >
        <fieldset>
          <legend className="text-sm font-medium">Rating</legend>
          <div className="mt-2 flex gap-2">
            {[1, 2, 3, 4, 5].map((n) => (
              <label key={n} className="flex items-center gap-1 text-sm">
                <input type="radio" name="rating" value={n} checked={rating === n} onChange={() => setRating(n)} /> {n}
              </label>
            ))}
          </div>
        </fieldset>
        <TextArea label="Comment (optional)" value={feedbackText} maxLength={2000} onChange={(e) => setFeedbackText(e.target.value)} />
      </Modal>
    </div>
  );
}
