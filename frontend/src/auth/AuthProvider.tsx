import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import * as client from '@/api/client';
import type { CurrentUser } from '@/types/api';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  user: CurrentUser | null;
  /** Set when the session ended unexpectedly (refresh failed). Shown on the login page. */
  sessionExpired: boolean;
  login: (email: string, password: string) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  reload: () => Promise<void>;
  /** UX helper only - the backend remains the authority for every action. */
  can: (permission: string) => boolean;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

async function fetchMe(): Promise<CurrentUser> {
  return client.apiRequest<CurrentUser>('/auth/me');
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  // Restore a session from the HttpOnly refresh cookie on first load.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await client.refreshAccessToken();
      if (cancelled) return;
      if (!token) {
        setStatus('anonymous');
        return;
      }
      try {
        const me = await fetchMe();
        if (!cancelled) {
          setUser(me);
          setStatus('authenticated');
        }
      } catch {
        if (!cancelled) setStatus('anonymous');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(
    () =>
      client.onSessionEvent((event) => {
        if (event === 'expired') {
          client.setAccessToken(null);
          setUser(null);
          setStatus('anonymous');
          setSessionExpired(true);
          queryClient.clear();
        }
      }),
    [queryClient],
  );

  const login = useCallback(async (email: string, password: string) => {
    await client.login(email, password);
    const me = await fetchMe();
    setUser(me);
    setStatus('authenticated');
    setSessionExpired(false);
    return me;
  }, []);

  const logout = useCallback(async () => {
    await client.logout();
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  const reload = useCallback(async () => {
    setUser(await fetchMe());
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      status,
      user,
      sessionExpired,
      login,
      logout,
      reload,
      can: (permission) => !!user?.permissions.includes(permission),
      hasRole: (role) => !!user?.user.roles.includes(role),
    }),
    [status, user, sessionExpired, login, logout, reload],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}
