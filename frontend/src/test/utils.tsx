import type { ReactElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ToastProvider } from '@/components/ui';
import type { CurrentUser } from '@/types/api';

export function renderWithProviders(ui: ReactElement, { route = '/', path = '*', extraRoutes = [] }: {
  route?: string;
  path?: string;
  extraRoutes?: { path: string; element: ReactElement }[];
} = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <ToastProvider>
          <Routes>
            <Route path={path} element={ui} />
            {extraRoutes.map((r) => (
              <Route key={r.path} path={r.path} element={r.element} />
            ))}
          </Routes>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export function makeUser(roles: string[], permissions: string[], overrides: Partial<CurrentUser['user']> = {}): CurrentUser {
  return {
    user: {
      id: 'u-1',
      email: 'test@example.test',
      fullName: 'Test User',
      userType: roles.includes('CITIZEN') ? 'CITIZEN' : 'STAFF',
      status: 'ACTIVE',
      passwordChangeRequired: false,
      roles,
      departmentIds: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      version: 0,
      ...overrides,
    },
    permissions,
  };
}

export const CITIZEN_PERMISSIONS = ['APPLICATION:CREATE', 'APPLICATION:SUBMIT', 'APPLICATION:READ_OWN', 'DOCUMENT:UPLOAD', 'GRIEVANCE:CREATE', 'GRIEVANCE:READ_OWN'];
export const OFFICER_PERMISSIONS = ['DEPARTMENT:READ', 'APPLICATION:READ_DEPARTMENT', 'APPLICATION:CLARIFY', 'APPLICATION:RECOMMEND', 'APPLICATION:REJECT', 'TASK:READ', 'TASK:CLAIM', 'DOCUMENT:VERIFY', 'GRIEVANCE:READ_DEPARTMENT', 'GRIEVANCE:RESPOND', 'GRIEVANCE:RESOLVE', 'REPORT:READ_DEPARTMENT'];
