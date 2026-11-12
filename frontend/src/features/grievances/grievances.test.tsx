import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/utils';

const api = vi.hoisted(() => ({ create: vi.fn(), list: vi.fn(), get: vi.fn(), action: vi.fn(), uploadEvidence: vi.fn(), downloadEvidence: vi.fn() }));
vi.mock('./api', async (orig) => ({ ...(await orig<typeof import('./api')>()), grievancesApi: api }));
vi.mock('@/features/services/api', () => ({
  servicesApi: { publicDepartments: vi.fn().mockResolvedValue([{ id: 'd1', code: 'REVENUE', name: 'Revenue (Demo)', active: true }]) },
}));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ can: () => false, user: { user: { id: 'u1' } } }) }));

import { GrievanceDetailPage, NewGrievancePage } from './GrievancePages';

describe('grievance submission', () => {
  it('validates the form, then registers the grievance', async () => {
    api.create.mockResolvedValue({ summary: { id: 'g1', referenceNumber: 'GRV-2026-000001' } });
    renderWithProviders(<NewGrievancePage />, { route: '/my/grievances/new', path: '/my/grievances/new', extraRoutes: [{ path: '/my/grievances/:id', element: <p>detail page</p> }] });
    await userEvent.click(screen.getByRole('button', { name: 'Submit grievance' }));
    expect(await screen.findByText('Choose a category')).toBeInTheDocument();
    expect(screen.getByText('Use at least 5 characters')).toBeInTheDocument();
    expect(api.create).not.toHaveBeenCalled();

    await screen.findByRole('option', { name: 'Revenue (Demo)' });
    await userEvent.selectOptions(screen.getByLabelText(/department/i), 'd1');
    await userEvent.selectOptions(screen.getByLabelText(/category/i), 'SERVICE_DELAY');
    await userEvent.type(screen.getByLabelText(/subject/i), 'Certificate delayed');
    await userEvent.type(screen.getByLabelText(/describe the issue/i), 'Pending for six weeks without any update.');
    await userEvent.click(screen.getByRole('button', { name: 'Submit grievance' }));
    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ departmentId: 'd1', category: 'SERVICE_DELAY', subject: 'Certificate delayed' })),
    );
    expect(await screen.findByText('detail page')).toBeInTheDocument();
  });
});

describe('grievance detail for citizens', () => {
  it('offers reopen only when the backend allows it', async () => {
    api.get.mockResolvedValue({
      summary: { id: 'g1', referenceNumber: 'GRV-2026-000001', status: 'RESOLVED', category: 'OTHER', subject: 'Issue', departmentId: 'd1', createdAt: '2026-11-01T00:00:00Z', updatedAt: '2026-11-01T00:00:00Z', version: 1 },
      description: 'desc', resolutionSummary: 'Fixed.', reopenDeadline: '2026-11-30T00:00:00Z', reopenCount: 0,
      comments: [{ id: 'c1', visibility: 'PUBLIC', authorName: 'Department', byCitizen: false, body: 'Fixed.', createdAt: '2026-11-02T00:00:00Z' }],
      history: [], documents: [], availableActions: ['COMMENT', 'REOPEN', 'CLOSE', 'FEEDBACK'],
    });
    renderWithProviders(<GrievanceDetailPage />, { route: '/my/grievances/g1', path: '/my/grievances/:id' });
    expect(await screen.findByRole('button', { name: /reopen/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument();
    expect(screen.queryByText(/internal note/i)).not.toBeInTheDocument();
  });
});
