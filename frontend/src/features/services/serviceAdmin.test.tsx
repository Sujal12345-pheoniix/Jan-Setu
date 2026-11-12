import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/test/utils';

const services = vi.hoisted(() => ({ create: vi.fn() }));
vi.mock('./api', () => ({ servicesApi: services }));
vi.mock('@/features/departments/api', () => ({
  departmentsApi: {
    list: vi.fn().mockResolvedValue({
      content: [{ id: 'dept-1', code: 'REVENUE', name: 'Revenue (Demo)', status: 'ACTIVE', createdAt: '', updatedAt: '', version: 0 }],
      page: 0, size: 100, totalElements: 1, totalPages: 1,
    }),
  },
}));
vi.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ can: () => true, user: { permissions: [] } }) }));

import { NewServicePage } from './ServiceAdminPages';

describe('new service form', () => {
  it('sends the default department selected for the admin', async () => {
    services.create.mockResolvedValue({ id: 'svc-1' });
    renderWithProviders(<NewServicePage />, { route: '/admin/services/new', path: '/admin/services/new', extraRoutes: [{ path: '/admin/services/:id', element: <p>editor</p> }] });
    await screen.findByRole('option', { name: 'Revenue (Demo)' });
    await userEvent.type(screen.getByLabelText(/^code/i), 'NEW_SVC');
    await userEvent.type(screen.getByLabelText(/^name/i), 'New service');
    await userEvent.type(screen.getByLabelText(/^description/i), 'A synthetic service');
    await userEvent.click(screen.getByRole('button', { name: 'Create draft' }));
    await waitFor(() => expect(services.create).toHaveBeenCalled());
    expect(services.create.mock.calls[0][0]).toMatchObject({ departmentId: 'dept-1', code: 'NEW_SVC', processingDays: 15 });
  });
});
