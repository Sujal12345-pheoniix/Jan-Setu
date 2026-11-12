import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';
import { Alert, Card, PageHeader, StatusBadge } from '@/components/ui';
import { tasksApi } from '@/features/tasks/api';
import { formatDate } from '@/utils/format';

const STEPS = [
  { status: 'SUBMITTED', who: 'Citizen', what: 'Submits a complete application; reference number and REVIEW task are created.' },
  { status: 'UNDER_REVIEW', who: 'Officer', what: 'Claims or is assigned the review task, verifies documents.' },
  { status: 'CLARIFICATION_REQUIRED', who: 'Officer → Citizen', what: 'Optional: asks for more information; SLA clock pauses.' },
  { status: 'RESUBMITTED', who: 'Citizen', what: 'Responds and replaces rejected documents; case returns to the same officer.' },
  { status: 'PENDING_APPROVAL', who: 'Officer', what: 'Recommends approval once mandatory documents are verified; APPROVAL task is created.' },
  { status: 'APPROVED', who: 'Reviewer', what: 'Approves (cannot be the recommending officer), rejects with a reason, or returns for correction.' },
  { status: 'COMPLETED', who: 'Reviewer', what: 'Marks the service as delivered.' },
];

/**
 * The platform currently runs one built-in, versioned workflow (STANDARD_REVIEW_APPROVAL_V1) for every service.
 * Department-configurable workflow definitions and SLA policies have database tables but no editor yet.
 */
export function WorkflowPage() {
  const overdue = useQuery({ queryKey: ['tasks', 'overdue', 'workflow-page'], queryFn: ({ signal }) => tasksApi.list({ overdue: true, size: 10 }, signal) });
  return (
    <div className="max-w-5xl space-y-5">
      <PageHeader title="Workflow & SLA" description="How applications move through review and approval, and how deadlines are measured." />
      <Alert tone="info" title="Built-in workflow">
        All services use the standard review → approval workflow (<code>STANDARD_REVIEW_APPROVAL_V1</code>). Custom workflow
        definitions and SLA policy versions are planned; their tables already exist. Rules are enforced by the backend state machine.
      </Alert>
      <Card title="Application lifecycle">
        <ol className="space-y-3">
          {STEPS.map((s, i) => (
            <li key={s.status} className="flex flex-wrap items-start gap-3">
              <span className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-brand-600 text-xs font-bold text-white">{i + 1}</span>
              <div className="flex-1">
                <p className="flex flex-wrap items-center gap-2"><StatusBadge status={s.status} /><span className="text-sm text-muted">{s.who}</span></p>
                <p className="mt-0.5 text-sm">{s.what}</p>
              </div>
            </li>
          ))}
        </ol>
        <p className="mt-4 flex flex-wrap items-center gap-1 text-sm text-muted">
          Terminal states: <StatusBadge status="REJECTED" /> <StatusBadge status="WITHDRAWN" /> <StatusBadge status="COMPLETED" />
        </p>
      </Card>
      <div className="grid gap-5 md:grid-cols-2">
        <Card title="SLA rules">
          <ul className="list-disc space-y-1 pl-5 text-sm">
            <li>Target = the service’s processing days, from submission (calendar days).</li>
            <li>Waiting for a citizen’s clarification extends the deadline by the time waited.</li>
            <li>Tasks due within 2 days are flagged “due soon”; past-due tasks are “overdue”.</li>
            <li>Change a service’s target in <Link className="text-brand-700 hover:underline" to="/admin/services">Service catalogue</Link> (applies to newly published versions).</li>
          </ul>
        </Card>
        <Card title="Assignment rules">
          <ul className="list-disc space-y-1 pl-5 text-sm">
            <li>Review tasks go to department members with the Officer role; approval tasks to Reviewers.</li>
            <li>Officers can claim unassigned review tasks; department admins can assign or reassign any open task.</li>
            <li>The recommending officer can never approve the same case (separation of duties).</li>
          </ul>
        </Card>
      </div>
      <Card title="Overdue work" actions={<Link className="inline-flex items-center gap-1 text-sm text-brand-700 hover:underline" to="/work/tasks?tab=overdue">Open queue <ArrowRight className="h-4 w-4" aria-hidden /></Link>}>
        {overdue.data?.content.length ? (
          <ul className="divide-y divide-line text-sm">
            {overdue.data.content.map((t) => (
              <li key={t.taskId} className="flex flex-wrap justify-between gap-2 py-2">
                <Link className="text-brand-700 hover:underline" to={`/work/applications/${t.applicationId}`}>{t.referenceNumber} · {t.serviceName}</Link>
                <span className="text-red-700">due {formatDate(t.dueAt)} · {t.assigneeName ?? 'unassigned'}</span>
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-sm text-muted">No overdue tasks in your departments.</p>
        )}
      </Card>
    </div>
  );
}
