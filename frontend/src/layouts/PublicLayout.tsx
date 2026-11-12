import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { Logo } from '@/components/Logo';
import { cn } from '@/utils/format';

export function DemoBanner() {
  return (
    <div className="bg-ink px-4 py-1.5 text-center text-xs text-white">
      JanSetu is an independent portfolio simulation. It is <strong>not an official government portal</strong>. Use
      synthetic data only.
    </div>
  );
}

export function PublicLayout() {
  const { status } = useAuth();
  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="skip-link">
        Skip to main content
      </a>
      <DemoBanner />
      <header className="border-b border-line bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <Link to="/" aria-label="JanSetu home">
            <Logo />
          </Link>
          <nav aria-label="Main" className="flex items-center gap-1 text-sm">
            <NavLink
              to="/services"
              className={({ isActive }) => cn('rounded-md px-3 py-2 hover:bg-canvas', isActive && 'font-semibold text-brand-700')}
            >
              Services
            </NavLink>
            {status === 'authenticated' ? (
              <Link to="/dashboard" className="rounded-md bg-brand-600 px-3 py-2 font-medium text-white hover:bg-brand-700">
                My dashboard
              </Link>
            ) : (
              <>
                <Link to="/login" className="rounded-md px-3 py-2 hover:bg-canvas">
                  Sign in
                </Link>
                <Link to="/register" className="rounded-md bg-brand-600 px-3 py-2 font-medium text-white hover:bg-brand-700">
                  Register
                </Link>
              </>
            )}
          </nav>
        </div>
      </header>
      <main id="main" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <Footer />
    </div>
  );
}

export function Footer() {
  return (
    <footer className="border-t border-line bg-white">
      <div className="mx-auto flex max-w-6xl flex-wrap justify-between gap-2 px-4 py-4 text-xs text-muted">
        <p>© JanSetu demo project. Synthetic data only. No real government integrations.</p>
        <p>Accessibility target: WCAG 2.1 AA</p>
      </div>
    </footer>
  );
}
