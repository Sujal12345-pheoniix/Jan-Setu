import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AlertTriangle, Clock } from 'lucide-react';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  DataTable,
  DescriptionList,
  EmptyState,
  ErrorState,
  Modal,
  PageHeader,
  Pagination,
  ReasonDialog,
  Select,
  Skeleton,
  StatusBadge,
  TableSkeleton,
  Tabs,
  TextArea,
  TextInput,
  useToast,
} from '@/components/ui';
import { applicationsApi } from '@/features/applications/api';
import { AnswerList } from '@/features/applications/DynamicForm';
import { Timeline } from '@/features/applications/Timeline';
import { ApplicationTable } from '@/features/citizen/CitizenApplicationPages';
import { departmentsApi } from '@/features/departments/api';
import { DocumentCard } from '@/features/documents/DocumentComponents';
import { useUrlState } from '@/hooks/useUrlState';
import type { ApplicationDetail, TaskQueueItem } from '@/types/api';
import { formatDate, formatDateTime, humanize, relativeDays } from '@/utils/format';
import { tasksApi } from './api';

type QueueTab = 'mine' | 'unassigned' | 'overdue' | 'all';

export function TaskQueuePage() {
  const { can } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { values, page, set } = useUrlState(['tab', 'step'] as const);
  const tab = (values.tab || 'mine') as QueueTab;
  const query = useQuery({
    queryKey: ['tasks', tab, values.step, page],
    queryFn: ({ signal }) =>
      tasksApi.list(
        { mine: tab === 'mine' || undefined, unassigned: tab === 'unassigned' || undefined, overdue: tab === 'overdue' || undefined, step: values.step, page, size: 20 },
        signal,
      ),
    placeholderData: (prev) => prev,
  });
  const claim = useMutation({
    mutationFn: (task: TaskQueueItem) => tasksApi.claim(task.taskId),
    onSuccess: (_r, task) => {
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      notify(`${task.referenceNumber} claimed.`);
      navigate(`/work/applications/${task.applicationId}`);
    },
    onError: (e) => notify(e instanceof ApiError ? e.message : 'Could not claim the task.', 'error'),
  });

  return (
    <div>
      <PageHeader title="Task queue" description="Review and approval work for your departments, ordered by due date." />
      <Tabs<QueueTab>
        label="Task filter"
        value={tab}
        onChange={(t) => set({ tab: t })}
        tabs={[
          { value: 'mine', label: 'Assigned to me' },
          { value: 'unassigned', label: 'Unassigned' },
          { value: 'overdue', label: 'Overdue' },
          { value: 'all', label: 'All open' },
        ]}
      />
      <div className="my-3 max-w-xs">
        <Select label="Stage" placeholder="All stages" value={values.step} onChange={(e) => set({ step: e.target.value })} options={[{ value: 'REVIEW', label: 'Review (officer)' }, { value: 'APPROVAL', label: 'Approval (reviewer)' }]} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No tasks here" description="You are all caught up for this view." />}
      {query.data && query.data.content.length > 0 && (
        <>
          <DataTable<TaskQueueItem>
            caption="Tasks"
            rows={query.data.content}
            rowKey={(t) => t.taskId}
            columns={[
              { key: 'ref', header: 'Application', render: (t) => <Link className="font-medium text-brand-700 hover:underline" to={`/work/applications/${t.applicationId}`}>{t.referenceNumber}</Link> },
              { key: 'service', header: 'Service', hideOnMobile: true, render: (t) => t.serviceName },
              { key: 'step', header: 'Stage', render: (t) => humanize(t.step) },
              { key: 'status', header: 'Status', render: (t) => <StatusBadge status={t.applicationStatus} /> },
              { key: 'assignee', header: 'Assignee', hideOnMobile: true, render: (t) => t.assigneeName ?? <span className="text-muted">Unassigned</span> },
              {
                key: 'due',
                header: 'Due',
                render: (t) => (
                  <span className="inline-flex flex-wrap items-center gap-1">
                    {formatDate(t.dueAt)}
                    {t.overdue && <Badge tone="danger"><AlertTriangle className="mr-1 h-3 w-3" aria-hidden />Overdue</Badge>}
                    {t.dueSoon && <Badge tone="warning"><Clock className="mr-1 h-3 w-3" aria-hidden />Due soon</Badge>}
                  </span>
                ),
              },
              {
                key: 'actions',
                header: 'Actions',
                render: (t) =>
                  !t.assigneeId && ((t.step === 'REVIEW' && can('TASK:CLAIM')) || (t.step === 'APPROVAL' && can('APPLICATION:APPROVE'))) ? (
                    <Button size="sm" variant="secondary" loading={claim.isPending && claim.variables?.taskId === t.taskId} onClick={() => claim.mutate(t)}>
                      Claim
                    </Button>
                  ) : (
                    <Link className="text-sm text-brand-700 hover:underline" to={`/work/applications/${t.applicationId}`}>Open</Link>
                  ),
              },
            ]}
          />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
    </div>
  );
}

const STATUS_OPTIONS = ['SUBMITTED', 'UNDER_REVIEW', 'CLARIFICATION_REQUIRED', 'RESUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'WITHDRAWN'];

export function StaffApplicationsPage() {
  const { values, page, set } = useUrlState(['status', 'q', 'mine', 'overdue'] as const);
  const query = useQuery({
    queryKey: ['applications', 'staff', values, page],
    queryFn: ({ signal }) =>
      applicationsApi.list(
        { status: values.status, q: values.q, assignedToMe: values.mine === 'true' || undefined, overdue: values.overdue === 'true' || undefined, page, size: 20 },
        signal,
      ),
    placeholderData: (prev) => prev,
  });
  return (
    <div>
      <PageHeader title="Department applications" description="All submitted applications in your departments." />
      <div className="mb-4 grid gap-3 rounded-card border border-line bg-white p-4 sm:grid-cols-4" role="search">
        <TextInput label="Reference" placeholder="JAN-2026-…" defaultValue={values.q} onBlur={(e) => set({ q: e.target.value })} />
        <Select label="Status" placeholder="Any status" value={values.status} onChange={(e) => set({ status: e.target.value })} options={STATUS_OPTIONS.map((s) => ({ value: s, label: humanize(s) }))} />
        <Select label="Assigned" value={values.mine} onChange={(e) => set({ mine: e.target.value })} options={[{ value: '', label: 'Everyone' }, { value: 'true', label: 'Assigned to me' }]} />
        <Select label="SLA" value={values.overdue} onChange={(e) => set({ overdue: e.target.value })} options={[{ value: '', label: 'All' }, { value: 'true', label: 'Overdue only' }]} />
      </div>
      {query.isPending && <TableSkeleton />}
      {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
      {query.data && query.data.content.length === 0 && <EmptyState title="No applications match" />}
      {query.data && query.data.content.length > 0 && (
        <>
          <ApplicationTable rows={query.data.content} linkBase="/work/applications" staff />
          <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
        </>
      )}
    </div>
  );
}

type DialogKind = null | 'clarify' | 'recommend' | 'approve' | 'reject' | 'return' | 'complete' | 'assign' | { rejectDoc: string };

/** Officer / reviewer case view. Buttons follow `availableActions`; the backend re-validates every command. */
export function CaseDetailPage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const query = useQuery({ queryKey: ['applications', id], queryFn: ({ signal }) => applicationsApi.get(id, signal) });
  const [dialog, setDialog] = useState<DialogKind>(null);
  const [question, setQuestion] = useState('');
  const [dueDays, setDueDays] = useState('7');
  const [comment, setComment] = useState('');
  const [assignee, setAssignee] = useState('');
  const app = query.data;

  const members = useQuery({
    queryKey: ['departments', app?.summary.departmentId, 'members'],
    queryFn: ({ signal }) => departmentsApi.members(app!.summary.departmentId, { size: 100 }, signal),
    enabled: dialog === 'assign' && !!app,
  });

  const done = (detail: ApplicationDetail, message: string) => {
    queryClient.setQueryData(['applications', id], detail);
    queryClient.invalidateQueries({ queryKey: ['tasks'] });
    queryClient.invalidateQueries({ queryKey: ['applications', 'staff'] });
    setDialog(null);
    notify(message);
  };
  const act = useMutation({
    mutationFn: ({ action, body }: { action: string; body?: Record<string, unknown> }) => applicationsApi.action(id, action, body),
    onSuccess: (detail, vars) => done(detail, `Action completed: ${humanize(vars.action.replace(/-/g, '_'))}.`),
  });
  const verify = useMutation({
    mutationFn: ({ docId, accept, reason }: { docId: string; accept: boolean; reason?: string }) => applicationsApi.verifyDocument(id, docId, accept, reason),
    onSuccess: (_d, v) => {
      queryClient.invalidateQueries({ queryKey: ['applications', id] });
      setDialog(null);
      notify(v.accept ? 'Document verified.' : 'Document rejected.');
    },
  });
  const assign = useMutation({
    mutationFn: (taskId: string) => tasksApi.assign(taskId, assignee),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['applications', id] });
      queryClient.invalidateQueries({ queryKey: ['tasks'] });
      setDialog(null);
      notify('Task assigned.');
    },
  });
  const addComment = useMutation({
    mutationFn: () => applicationsApi.comment(id, comment),
    onSuccess: (detail) => {
      queryClient.setQueryData(['applications', id], detail);
      setComment('');
      notify('Internal comment added.');
    },
  });
  const error = [act.error, verify.error, assign.error, addComment.error].find(Boolean);
  const errorText = error instanceof ApiError ? error.message : error ? 'The action failed.' : undefined;

  if (query.isPending) return <Skeleton className="h-96" />;
  if (query.isError) return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  const detail = query.data;
  const a = detail.availableActions;
  const activeTask = detail.tasks?.find((t) => ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'BLOCKED'].includes(t.status));
  const requiredPermission = activeTask?.step === 'APPROVAL' ? 'REVIEWER' : 'OFFICER';
  const eligible = (members.data?.content ?? []).filter((m) => m.status === 'ACTIVE' && m.roles.includes(requiredPermission));

  return (
    <div>
      <PageHeader
        title={`${detail.summary.referenceNumber} — ${detail.service.name}`}
        description={`Applicant: ${detail.summary.applicantName ?? '—'}`}
        breadcrumbs={[{ label: 'Applications', to: '/work/applications' }, { label: detail.summary.referenceNumber ?? '' }]}
        actions={<StatusBadge status={detail.summary.status} />}
      />
      {errorText && <Alert tone="error" className="mb-4">{errorText}</Alert>}
      {detail.summary.overdue && (
        <Alert tone="warning" className="mb-4" title="SLA breached">
          This case was due {formatDate(detail.summary.dueAt)} ({relativeDays(detail.summary.dueAt)}).
        </Alert>
      )}
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <Card title="Application answers">
            <AnswerList fields={detail.service.formSchema.fields ?? []} data={detail.formData} />
          </Card>
          <Card title="Verification checklist">
            <ul className="space-y-3">
              {detail.service.requirements.filter((r) => r.requirementType === 'DOCUMENT').map((r) => {
                const doc = detail.documents.find((d) => d.requirementCode === r.code);
                return (
                  <li key={r.code}>
                    {doc ? (
                      <DocumentCard
                        document={doc}
                        title={`${r.name}${r.mandatory ? ' (required)' : ''}`}
                        onDownload={(v) => applicationsApi.downloadDocument(id, doc.id, v)}
                        actions={
                          a.includes('VERIFY_DOCUMENTS') && (
                            <>
                              {doc.status !== 'VERIFIED' && (
                                <Button size="sm" variant="secondary" loading={verify.isPending} onClick={() => verify.mutate({ docId: doc.id, accept: true })}>
                                  Mark verified
                                </Button>
                              )}
                              {doc.status !== 'REJECTED' && (
                                <Button size="sm" variant="ghost" className="text-red-700" onClick={() => setDialog({ rejectDoc: doc.id })}>
                                  Reject document
                                </Button>
                              )}
                            </>
                          )
                        }
                      />
                    ) : (
                      <p className="rounded-md border border-dashed border-line p-3 text-sm text-muted">{r.name}: not provided</p>
                    )}
                  </li>
                );
              })}
            </ul>
            {detail.service.requirements.filter((r) => r.requirementType === 'DECLARATION').map((r) => (
              <p key={r.code} className="mt-3 text-sm">
                {(detail.formData._declarations as string[] | undefined)?.includes(r.code) ? '✓' : '✗'} Declaration: {r.name}
              </p>
            ))}
          </Card>
          {detail.clarifications.length > 0 && (
            <Card title="Clarifications">
              <ul className="space-y-3">
                {detail.clarifications.map((c) => (
                  <li key={c.id} className="rounded-md bg-canvas p-3 text-sm">
                    <p className="font-medium">Q: {c.question}</p>
                    <p className="text-xs text-muted">Asked {formatDateTime(c.createdAt)} · due {formatDate(c.dueAt)} · <StatusBadge status={c.status} /></p>
                    {c.response && <p className="mt-2">A: {c.response} <span className="text-xs text-muted">({formatDateTime(c.respondedAt)})</span></p>}
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <Card title="History">
            <Timeline entries={detail.history.map((h) => ({ ...h, title: h.command }))} />
          </Card>
          <Card title="Internal comments">
            {(detail.comments ?? []).length === 0 && <p className="text-sm text-muted">No internal comments yet.</p>}
            <ul className="space-y-2">
              {(detail.comments ?? []).map((c) => (
                <li key={c.id} className="rounded-md border border-dashed border-amber-400 bg-amber-50 p-3 text-sm">
                  <p className="text-xs text-muted">{c.authorName} · {formatDateTime(c.createdAt)}</p>
                  <p className="mt-1 whitespace-pre-line">{c.body}</p>
                </li>
              ))}
            </ul>
            {a.includes('COMMENT') && (
              <div className="mt-3 space-y-2">
                <TextArea label="Add an internal comment (never shown to the applicant)" value={comment} maxLength={4000} onChange={(e) => setComment(e.target.value)} />
                <Button size="sm" disabled={!comment.trim()} loading={addComment.isPending} onClick={() => addComment.mutate()}>Add comment</Button>
              </div>
            )}
          </Card>
        </div>
        <div className="space-y-5">
          <Card title="Actions">
            <div className="flex flex-col gap-2">
              {a.includes('START_REVIEW') && <Button loading={act.isPending} onClick={() => act.mutate({ action: 'start-review' })}>Start review</Button>}
              {a.includes('REQUEST_CLARIFICATION') && <Button variant="secondary" onClick={() => setDialog('clarify')}>Request clarification</Button>}
              {a.includes('RECOMMEND') && <Button onClick={() => setDialog('recommend')}>Recommend for approval</Button>}
              {a.includes('APPROVE') && <Button onClick={() => setDialog('approve')}>Approve</Button>}
              {a.includes('RETURN') && <Button variant="secondary" onClick={() => setDialog('return')}>Return for correction</Button>}
              {a.includes('REJECT') && <Button variant="danger" onClick={() => setDialog('reject')}>Reject</Button>}
              {a.includes('COMPLETE') && <Button onClick={() => setDialog('complete')}>Mark completed</Button>}
              {a.includes('ASSIGN') && activeTask && <Button variant="secondary" onClick={() => setDialog('assign')}>{activeTask.assigneeId ? 'Reassign task' : 'Assign task'}</Button>}
              {a.filter((x) => x !== 'COMMENT' && x !== 'VERIFY_DOCUMENTS').length === 0 && (
                <p className="text-sm text-muted">No actions available for you at this stage.</p>
              )}
            </div>
          </Card>
          <Card title="Case">
            <DescriptionList
              items={[
                { label: 'Submitted', value: formatDateTime(detail.summary.submittedAt) },
                { label: 'Stage', value: humanize(detail.summary.currentStep) },
                { label: 'Assignee', value: detail.summary.assigneeName ?? 'Unassigned' },
                { label: 'Due', value: detail.summary.dueAt ? `${formatDate(detail.summary.dueAt)} (${relativeDays(detail.summary.dueAt)})` : '—' },
                { label: 'Form version', value: `v${detail.service.versionNumber}` },
              ]}
            />
          </Card>
          <Card title="Decisions">
            {detail.decisions.length === 0 ? (
              <p className="text-sm text-muted">No decisions yet.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {detail.decisions.map((d) => (
                  <li key={d.decidedAt}>
                    <StatusBadge status={d.decision} /> by {d.decidedByName} · {formatDateTime(d.decidedAt)}
                    {d.reason && <p className="mt-1 text-muted">{d.reason}</p>}
                  </li>
                ))}
              </ul>
            )}
          </Card>
          {detail.tasks && (
            <Card title="Workload & SLA">
              <ul className="space-y-2 text-sm">
                {detail.tasks.map((t) => (
                  <li key={t.id} className="flex flex-wrap items-center gap-2">
                    <Badge>{humanize(t.step)}</Badge>
                    <StatusBadge status={t.status} />
                    <span className="text-muted">{t.assigneeName ?? 'Unassigned'} · due {formatDate(t.dueAt)}</span>
                  </li>
                ))}
              </ul>
            </Card>
          )}
        </div>
      </div>

      <Modal
        open={dialog === 'clarify'}
        title="Request clarification"
        onClose={() => setDialog(null)}
        footer={<><Button variant="secondary" onClick={() => setDialog(null)}>Cancel</Button><Button disabled={!question.trim()} loading={act.isPending} onClick={() => act.mutate({ action: 'request-clarification', body: { question, dueInDays: Number(dueDays) } })}>Send request</Button></>}
      >
        <TextArea label="What does the applicant need to provide?" required value={question} maxLength={2000} onChange={(e) => setQuestion(e.target.value)} />
        <TextInput label="Response due in (days)" type="number" min={1} max={60} value={dueDays} onChange={(e) => setDueDays(e.target.value)} hint="The SLA clock pauses while waiting for the applicant." />
      </Modal>
      <ReasonDialog open={dialog === 'recommend'} title="Recommend for approval" label="Remarks for the reviewer" confirmLabel="Recommend" loading={act.isPending} error={errorText} onCancel={() => setDialog(null)} onSubmit={(reason) => act.mutate({ action: 'recommend', body: { reason } })} />
      <ReasonDialog open={dialog === 'approve'} title="Approve application" label="Decision remarks" confirmLabel="Approve" loading={act.isPending} error={errorText} onCancel={() => setDialog(null)} onSubmit={(reason) => act.mutate({ action: 'approve', body: { reason } })} />
      <ReasonDialog open={dialog === 'reject'} title="Reject application" label="Reason for rejection (shown to the applicant)" confirmLabel="Reject" required danger loading={act.isPending} error={errorText} onCancel={() => setDialog(null)} onSubmit={(reason) => act.mutate({ action: 'reject', body: { reason } })} />
      <ReasonDialog open={dialog === 'return'} title="Return for correction" label="What needs to be corrected?" confirmLabel="Return to officer" required loading={act.isPending} error={errorText} onCancel={() => setDialog(null)} onSubmit={(reason) => act.mutate({ action: 'return', body: { reason } })} />
      <ReasonDialog open={dialog === 'complete'} title="Mark as completed" label="Completion remarks" confirmLabel="Complete" loading={act.isPending} error={errorText} onCancel={() => setDialog(null)} onSubmit={(reason) => act.mutate({ action: 'complete', body: { reason } })} />
      <ReasonDialog
        open={typeof dialog === 'object' && dialog !== null}
        title="Reject document"
        label="Why is the document not acceptable? (shown to the applicant)"
        confirmLabel="Reject document"
        required
        danger
        loading={verify.isPending}
        onCancel={() => setDialog(null)}
        onSubmit={(reason) => typeof dialog === 'object' && dialog && verify.mutate({ docId: dialog.rejectDoc, accept: false, reason })}
      />
      <Modal
        open={dialog === 'assign'}
        title={`Assign ${humanize(activeTask?.step)} task`}
        onClose={() => setDialog(null)}
        footer={<><Button variant="secondary" onClick={() => setDialog(null)}>Cancel</Button><Button disabled={!assignee || !activeTask} loading={assign.isPending} onClick={() => activeTask && assign.mutate(activeTask.id)}>Assign</Button></>}
      >
        {members.isPending ? (
          <Skeleton className="h-10" />
        ) : (
          <Select
            label={`Eligible ${requiredPermission === 'REVIEWER' ? 'reviewers' : 'officers'}`}
            required
            placeholder="Select a staff member"
            value={assignee}
            onChange={(e) => setAssignee(e.target.value)}
            options={eligible.map((m) => ({ value: m.userId, label: m.fullName }))}
          />
        )}
        <p className="text-xs text-muted">The server re-checks membership, role and separation of duties.</p>
      </Modal>
    </div>
  );
}
