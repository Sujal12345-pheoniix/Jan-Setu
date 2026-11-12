import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders, makeUser, CITIZEN_PERMISSIONS } from '@/test/utils';
import { visibleSections } from '@/routes/navigation';

const auth = vi.hoisted(() => ({ value: { status: 'anonymous', user: null as unknown, can: (_p: string): boolean => false } }));
vi.mock('./AuthProvider', () => ({ useAuth: () => auth.value }));

import { RequireAuth, RequirePermission } from './guards';

describe('route guards', () => {
  it('redirects anonymous users to the login page', () => {
    auth.value = { status: 'anonymous', user: null, can: () => false };
    renderWithProviders(<RequireAuth><p>secret</p></RequireAuth>, {
      route: '/dashboard',
      extraRoutes: [{ path: '/login', element: <p>login page</p> }],
      path: '/dashboard',
    });
    expect(screen.getByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('secret')).not.toBeInTheDocument();
  });

  it('shows a loader while the session is being restored', () => {
    auth.value = { status: 'loading', user: null, can: () => false };
    renderWithProviders(<RequireAuth><p>secret</p></RequireAuth>, { route: '/x', path: '/x' });
    expect(screen.getByRole('status')).toHaveTextContent(/restoring your session/i);
  });

  it('sends users without the permission to the unauthorized page', () => {
    const user = makeUser(['CITIZEN'], CITIZEN_PERMISSIONS);
    auth.value = { status: 'authenticated', user, can: (p) => user.permissions.includes(p) };
    renderWithProviders(<RequirePermission anyOf={['USER:READ']}><p>admin area</p></RequirePermission>, {
      route: '/admin/users',
      path: '/admin/users',
      extraRoutes: [{ path: '/unauthorized', element: <p>no access</p> }],
    });
    expect(screen.getByText('no access')).toBeInTheDocument();
  });

  it('renders protected content when the permission is held', () => {
    const user = makeUser(['PLATFORM_ADMIN'], ['USER:READ']);
    auth.value = { status: 'authenticated', user, can: (p) => user.permissions.includes(p) };
    renderWithProviders(<RequirePermission anyOf={['USER:READ']}><p>admin area</p></RequirePermission>, { route: '/a', path: '/a' });
    expect(screen.getByText('admin area')).toBeInTheDocument();
  });
});

describe('role-based navigation', () => {
  const labels = (perms: string[]) => visibleSections((p) => perms.includes(p)).flatMap((s) => s.items.map((i) => i.label));

  it('citizens see their own services only', () => {
    const items = labels(CITIZEN_PERMISSIONS);
    expect(items).toEqual(expect.arrayContaining(['Dashboard', 'My applications', 'My grievances', 'Browse services']));
    expect(items).not.toContain('Task queue');
    expect(items).not.toContain('Users');
  });

  it('officers see casework but no platform administration', () => {
    const items = labels(['TASK:READ', 'APPLICATION:READ_DEPARTMENT', 'GRIEVANCE:READ_DEPARTMENT', 'REPORT:READ_DEPARTMENT']);
    expect(items).toEqual(expect.arrayContaining(['Task queue', 'Applications', 'Grievances', 'Reports']));
    expect(items).not.toContain('Users');
    expect(items).not.toContain('My applications');
  });

  it('auditors see the audit log', () => {
    expect(labels(['AUDIT:READ', 'ROLE:READ'])).toEqual(expect.arrayContaining(['Audit log', 'Roles & permissions']));
  });
});
