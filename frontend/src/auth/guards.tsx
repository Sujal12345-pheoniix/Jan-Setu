import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthProvider';
import { PageLoader } from '@/components/ui/Spinner';

/** Requires a signed-in user. Remembers the requested page so login can return to it. */
export function RequireAuth({ children }: { children?: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <PageLoader label="Restoring your session" />;
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return children ? <>{children}</> : <Outlet />;
}

/**
 * Hides routes the user has no permission for. This is navigation UX only: the backend enforces
 * every permission, scope and ownership rule independently.
 */
export function RequirePermission({ anyOf, children }: { anyOf: string[]; children?: ReactNode }) {
  const { can } = useAuth();
  if (!anyOf.some(can)) return <Navigate to="/unauthorized" replace />;
  return children ? <>{children}</> : <Outlet />;
}
