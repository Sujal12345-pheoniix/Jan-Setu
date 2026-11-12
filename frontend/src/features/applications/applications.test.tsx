import { describe, expect, it, vi, beforeEach } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/utils';
import type { ApplicationDetail, FormField } from '@/types/api';
import { buildFormSchema, toPayload } from './DynamicForm';

const api = vi.hoisted(() => ({
  get: vi.fn(),
  saveDraft: vi.fn(),
  submit: vi.fn(),
  withdraw: vi.fn(),
  respondToClarification: vi.fn(),
  uploadDocument: vi.fn(),
  downloadDocument: vi.fn(),
  list: vi.fn(),
  createDraft: vi.fn(),
  action: vi.fn(),
  verifyDocument: vi.fn(),
  comment: vi.fn(),
}));
vi.mock('./api', () => ({ applicationsApi: api }));
vi.mock('@/features/departments/api', () => ({ departmentsApi: { members: vi.fn().mockResolvedValue({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }) } }));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ can: () => true, user: null }) }));

import { CitizenApplicationPage } from '@/features/citizen/CitizenApplicationPages';
import { CaseDetailPage } from '@/features/tasks/WorkPages';

const fields: FormField[] = [
  { key: 'applicantName', label: 'Applicant name', type: 'text', required: true },
  { key: 'annualIncome', label: 'Annual income', type: 'number', required: true },
  { key: 'source', label: 'Source', type: 'select', options: ['Salary', 'Business'] },
];

function detail(overrides: Partial<ApplicationDetail> = {}, summary: Partial<ApplicationDetail['summary']> = {}): ApplicationDetail {
  return {
    summary: {
      id: 'app-1', status: 'DRAFT', serviceId: 's1', serviceName: 'Income Certificate', departmentId: 'd1',
      createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z', overdue: false, version: 3, ...summary,
    },
    service: {
      serviceVersionId: 'v1', versionNumber: 1, code: 'INCOME', name: 'Income Certificate', processingDays: 10,
      formSchema: { fields },
      requirements: [
        { code: 'ID_PROOF', name: 'Identity proof', requirementType: 'DOCUMENT', mandatory: true, displayOrder: 0 },
        { code: 'TRUTH', name: 'Information is true', requirementType: 'DECLARATION', mandatory: true, displayOrder: 1 },
      ],
    },
    formData: {},
    documents: [],
    clarifications: [],
    decisions: [],
    history: [{ toStatus: 'DRAFT', command: 'create', occurredAt: '2026-11-01T00:00:00Z' }],
    availableActions: ['EDIT', 'UPLOAD_DOCUMENT', 'SUBMIT', 'WITHDRAW'],
    ...overrides,
  };
}

describe('dynamic form helpers', () => {
  it('requires fields only on submission and validates types', () => {
    expect(buildFormSchema(fields, false).safeParse({ applicantName: '', annualIncome: '' }).success).toBe(true);
    const submit = buildFormSchema(fields, true).safeParse({ applicantName: '', annualIncome: 'abc' });
    expect(submit.success).toBe(false);
    const messages = submit.error!.issues.map((i) => i.message);
    expect(messages).toContain('Applicant name is required');
    expect(messages).toContain('Enter a number');
  });

  it('serialises numbers and declarations and drops empty values', () => {
    expect(toPayload(fields, { applicantName: ' Asha ', annualIncome: '120000', source: '' }, ['TRUTH'])).toEqual({
      applicantName: 'Asha',
      annualIncome: 120000,
      _declarations: ['TRUTH'],
    });
  });
});

describe('citizen application workspace', () => {
  beforeEach(() => vi.clearAllMocks());

  it('saves a draft with the current version and shows field errors from the server', async () => {
    api.get.mockResolvedValue(detail());
    api.saveDraft.mockRejectedValueOnce(Object.assign(new (await import('@/api/client')).ApiError({
      status: 400, detail: 'The application form has invalid or missing answers.', errorCode: 'VALIDATION_ERROR',
      fieldErrors: [{ field: 'formData.annualIncome', message: 'must be a number' }],
    })));
    renderWithProviders(<CitizenApplicationPage />, { route: '/my/applications/app-1', path: '/my/applications/:id' });
    await userEvent.type(await screen.findByLabelText(/applicant name/i), 'Asha');
    await userEvent.type(screen.getByLabelText(/annual income/i), '5');
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(api.saveDraft).toHaveBeenCalledWith('app-1', { applicantName: 'Asha', annualIncome: 5 }, 3));
    expect(await screen.findByText('must be a number')).toBeInTheDocument();
  });

  it('submits from the review step and shows the reference number', async () => {
    const ready = detail({
      formData: { applicantName: 'Asha', annualIncome: 120000, _declarations: ['TRUTH'] },
      documents: [{ id: 'doc-1', ownerId: 'u1', requirementCode: 'ID_PROOF', status: 'AVAILABLE', currentVersion: 1, fileName: 'id.pdf', sizeBytes: 1000, versions: [] }],
    });
    api.get.mockResolvedValue(ready);
    api.saveDraft.mockResolvedValue(ready);
    api.submit.mockResolvedValue({ id: 'app-1', referenceNumber: 'JAN-2026-000042', status: 'SUBMITTED' });
    renderWithProviders(<CitizenApplicationPage />, { route: '/my/applications/app-1', path: '/my/applications/:id' });
    await userEvent.click(await screen.findByRole('button', { name: /3\. review & submit/i }));
    expect(screen.getByRole('checkbox', { name: /information is true/i })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }));
    const dialog = await screen.findByRole('dialog', { name: 'Submit application?' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Submit' }));
    expect(await screen.findByTestId('reference-number')).toHaveTextContent('JAN-2026-000042');
    expect(api.submit).toHaveBeenCalledWith('app-1', expect.any(String));
  });

  it('lets the citizen respond to a clarification request', async () => {
    api.get.mockResolvedValue(detail(
      {
        availableActions: ['RESPOND_CLARIFICATION', 'UPLOAD_DOCUMENT'],
        clarifications: [{ id: 'c1', question: 'Upload a clearer ID.', dueAt: '2026-12-01T00:00:00Z', status: 'OPEN', createdAt: '2026-11-02T00:00:00Z' }],
      },
      { status: 'CLARIFICATION_REQUIRED', referenceNumber: 'JAN-2026-000001' },
    ));
    api.respondToClarification.mockResolvedValue(detail({}, { status: 'RESUBMITTED', referenceNumber: 'JAN-2026-000001' }));
    renderWithProviders(<CitizenApplicationPage />, { route: '/my/applications/app-1', path: '/my/applications/:id' });
    expect(await screen.findByText('Upload a clearer ID.')).toBeInTheDocument();
    const send = screen.getByRole('button', { name: 'Send response' });
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText(/your response/i), 'Uploaded a new scan.');
    await userEvent.click(send);
    await waitFor(() => expect(api.respondToClarification).toHaveBeenCalledWith('app-1', 'Uploaded a new scan.'));
  });
});

describe('officer case decisions', () => {
  beforeEach(() => vi.clearAllMocks());

  it('requires a reason before rejecting and shows only server-allowed actions', async () => {
    api.get.mockResolvedValue(detail(
      { availableActions: ['REQUEST_CLARIFICATION', 'RECOMMEND', 'REJECT', 'VERIFY_DOCUMENTS', 'COMMENT'], tasks: [], comments: [] },
      { status: 'UNDER_REVIEW', referenceNumber: 'JAN-2026-000007', applicantName: 'Asha Demo' },
    ));
    api.action.mockResolvedValue(detail({ availableActions: [] }, { status: 'REJECTED', referenceNumber: 'JAN-2026-000007' }));
    renderWithProviders(<CaseDetailPage />, { route: '/work/applications/app-1', path: '/work/applications/:id' });
    await screen.findByRole('heading', { name: /JAN-2026-000007/ });
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Reject' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reject application' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject' }));
    expect(await within(dialog).findByText(/is required/i)).toBeInTheDocument();
    expect(api.action).not.toHaveBeenCalled();
    await userEvent.type(within(dialog).getByRole('textbox'), 'Income above scheme limit');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reject' }));
    await waitFor(() => expect(api.action).toHaveBeenCalledWith('app-1', 'reject', { reason: 'Income above scheme limit' }));
  });

  it('surfaces backend authorization errors instead of deciding locally', async () => {
    const { ApiError } = await import('@/api/client');
    api.get.mockResolvedValue(detail({ availableActions: ['START_REVIEW'], tasks: [], comments: [] }, { status: 'SUBMITTED', referenceNumber: 'JAN-2026-000008' }));
    api.action.mockRejectedValue(new ApiError({ status: 403, detail: 'This task is assigned to another user.', errorCode: 'ACCESS_DENIED' }));
    renderWithProviders(<CaseDetailPage />, { route: '/work/applications/app-1', path: '/work/applications/:id' });
    await userEvent.click(await screen.findByRole('button', { name: 'Start review' }));
    expect(await screen.findByText('This task is assigned to another user.')).toBeInTheDocument();
  });
});
