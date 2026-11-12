import { useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, LogOut, Menu, X } from 'lucide-react';
import { useAuth } from '@/auth/AuthProvider';
import { Logo } from '@/components/Logo';
import { notificationsApi } from '@/features/notifications/api';
import { visibleSections } from '@/routes/navigation';
import { cn, humanize } from '@/utils/format';
import { DemoBanner, Footer } from './PublicLayout';

/** Authenticated layout: role-aware sidebar (UX only), notification bell, user menu, skip link. */
export function AppShell() {
  const { user, can, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: ({ signal }) => notificationsApi.unreadCount(signal),
    refetchInterval: 60_000,
  });
  const sections = visibleSections(can);

  const nav = (
    <nav aria-label="Primary" className="space-y-5">
      {sections.map((section) => (
        <div key={section.title}>
          <p className="px-3 text-[11px] font-semibold uppercase tracking-wider text-muted">{section.title}</p>
          <ul className="mt-1 space-y-0.5">
            {section.items.map((item) => (
              <li key={item.to}>
                <NavLink
                  to={item.to}
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm',
                      isActive ? 'bg-brand-50 font-semibold text-brand-800' : 'text-ink hover:bg-canvas',
                    )
                  }
                >
                  <item.icon className="h-4 w-4 shrink-0" aria-hidden />
                  {item.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="skip-link">
        Skip to main content
      </a>
      <DemoBanner />
      <header className="sticky top-0 z-30 border-b border-line bg-white">
        <div className="flex items-center justify-between gap-3 px-4 py-2.5">
          <div className="flex items-center gap-2">
            <button
              type="button"
              className="rounded-md p-2 hover:bg-canvas lg:hidden"
              aria-label={mobileOpen ? 'Close navigation' : 'Open navigation'}
              aria-expanded={mobileOpen}
              onClick={() => setMobileOpen((o) => !o)}
            >
              {mobileOpen ? <X className="h-5 w-5" aria-hidden /> : <Menu className="h-5 w-5" aria-hidden />}
            </button>
            <Link to="/dashboard" aria-label="JanSetu dashboard">
              <Logo />
            </Link>
          </div>
          <div className="flex items-center gap-2">
            <Link
              to="/notifications"
              className="relative rounded-md p-2 hover:bg-canvas"
              aria-label={`Notifications${unread.data?.unread ? `, ${unread.data.unread} unread` : ''}`}
            >
              <Bell className="h-5 w-5" aria-hidden />
              {!!unread.data?.unread && (
                <span className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-accent-500 px-1 text-center text-[11px] font-bold text-ink">
                  {unread.data.unread > 99 ? '99+' : unread.data.unread}
                </span>
              )}
            </Link>
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium">{user?.user.fullName}</p>
              <p className="text-xs text-muted">{user?.user.roles.map(humanize).join(', ')}</p>
            </div>
            <button
              type="button"
              onClick={async () => {
                await logout();
                navigate('/login', { replace: true, state: { loggedOut: true } });
              }}
              className="inline-flex items-center gap-1.5 rounded-md border border-line px-3 py-1.5 text-sm hover:bg-canvas"
            >
              <LogOut className="h-4 w-4" aria-hidden /> Sign out
            </button>
          </div>
        </div>
      </header>
      <div className="flex flex-1">
        <aside className="hidden w-60 shrink-0 border-r border-line bg-white px-2 py-5 lg:block">{nav}</aside>
        {mobileOpen && (
          <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
            <div className="absolute inset-0 bg-black/30" onClick={() => setMobileOpen(false)} />
            <aside className="absolute inset-y-0 left-0 w-72 overflow-y-auto bg-white px-2 py-5 shadow-xl">{nav}</aside>
          </div>
        )}
        <main id="main" className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">
          {user?.user.passwordChangeRequired && (
            <div className="mb-4 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900" role="status">
              Your account uses an initial password. <Link className="font-semibold underline" to="/profile?tab=security">Change it now</Link>.
            </div>
          )}
          <Outlet />
        </main>
      </div>
      <Footer />
    </div>
  );
}
