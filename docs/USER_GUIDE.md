# JanSetu User Guide

> JanSetu is a portfolio simulation, not a government service. Use synthetic information only.

## Citizens

1. **Register.** Choose **Register**, enter your name, email and a password (12+ characters with a letter and a digit), and confirm the demo notice. Then sign in.
2. **Find a service.** **Browse services** lets you search and filter by department or category. A service page shows eligibility, required documents, processing time and the fee.
3. **Apply.** Choose **Start application**. A draft is saved under **My applications**.
   1. **Application details.** Fill in the form. **Save draft** keeps your progress; required fields are only checked at submission.
   2. **Documents.** Upload each required document (PDF, PNG or JPEG, within the size shown). You can replace a file until it is verified.
   3. **Review & submit.** Check your answers, accept the declarations, then **Submit application**. You receive a reference number such as `JAN-2026-000123`.
4. **Track it.** The application page shows the current stage, expected processing time, the full status history and, at the end, the decision with its reason.
5. **Clarifications.** If the department asks for more information, the application shows **Clarification requested** and you get a notification. Upload any corrected documents, type your response, and choose **Send response**.
6. **Withdraw.** You can withdraw a draft, or a submitted application that is not yet under review.
7. **Grievances.** **My grievances → Raise a grievance**: choose the department and category, describe the issue, then attach evidence. Replies from the department appear in the conversation.
   - **Resolved grievances:** you can **Reopen** one within 14 days (once), **Accept and close** it, and rate the resolution.
8. **Profile & security:**
   - update your name and phone;
   - change your password (this signs you out everywhere);
   - choose notification preferences;
   - see your signed-in devices and sign them out;
   - review recent account activity.

## Officers

- **Task queue:** your tasks, unassigned work (**Claim**), and overdue items.
- **Case page:**
  1. **Start review.**
  2. **Mark verified** or **Reject document** for each document (rejection reasons are shown to the citizen).
  3. Optionally **Request clarification**; the SLA deadline is extended by the citizen's waiting time.
  4. **Recommend for approval** (all mandatory documents must be verified), or **Reject** with a reason.
- **Internal comments** are never shown to the citizen.
- **Grievances:**
  1. **Acknowledge** the grievance.
  2. **Start work** once it is assigned to you.
  3. Reply publicly, or add internal notes.
  4. **Resolve** it with a summary.

## Reviewers

- Open cases in **Pending approval** from the task queue or the applications list.
- **Approve**, **Reject** (reason required) or **Return for correction** to the officer. You cannot approve a case you recommended yourself.
- **Mark completed** once the service has been delivered.

## Department administrators

- **Departments & staff:** add or remove staff in your department and edit department details.
- **Service catalogue:**
  1. Create a draft service.
  2. Edit its details, form fields (form builder) and requirements.
  3. **Publish**. Each publish creates an immutable version, and existing applications keep the version they used.
  4. **Deprecate** to withdraw the service from the catalogue.
- **Case page:** **Assign task** or **Reassign** to an eligible member. Only officers can take review tasks, and only reviewers can take approval tasks.
- **Workflow & SLA:** explains the built-in workflow and lists overdue work.
- **Reports:** your department's metrics.

## Platform administrators

- **Users:** create staff accounts (they must change the initial password), change roles, suspend, deactivate or reactivate accounts. Every change is audited.
- **Departments:** create, edit, activate and deactivate departments.
- **Roles & permissions:** view system roles (read-only), and create and edit custom roles. You cannot grant privileged permissions you do not hold.
- **System health:** API status and platform settings.

## Auditors

- **Audit log:** filter by action, resource, resource ID, user, outcome and date. Open an event for its details, and **Export CSV** (up to 5,000 rows; exports are audited).

## Management viewers

- **Reports:** applications submitted and decided, SLA compliance, average processing time, status distribution, department performance, workload, and grievance trends, for a chosen date range. Only aggregates are shown, never citizen personal data.
