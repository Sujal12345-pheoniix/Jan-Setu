import { Link } from 'react-router-dom';
import { ClipboardCheck, FileSearch, MessageSquareWarning, ShieldCheck } from 'lucide-react';
import { LinkButton } from '@/components/ui';

export function LandingPage() {
  const features = [
    { icon: FileSearch, title: 'Find a service', text: 'Browse published services, eligibility, required documents and processing times.' },
    { icon: ClipboardCheck, title: 'Apply and track', text: 'Save drafts, upload documents, submit, and follow every status change.' },
    { icon: MessageSquareWarning, title: 'Raise a grievance', text: 'Report a problem, follow replies and reopen if it is not solved.' },
    { icon: ShieldCheck, title: 'Accountable handling', text: 'Role-based access, department isolation and a full audit trail.' },
  ];
  return (
    <div className="space-y-10">
      <section className="rounded-card bg-brand-700 px-6 py-10 text-white sm:px-10">
        <p className="text-sm font-medium text-accent-400">Portfolio simulation</p>
        <h1 className="mt-2 max-w-2xl text-3xl font-bold sm:text-4xl">Public services and grievances, tracked end to end.</h1>
        <p className="mt-3 max-w-2xl text-brand-100">
          JanSetu demonstrates how citizens, officers and administrators can work on service applications and complaints with
          clear status, deadlines and accountability. It is not an official government portal.
        </p>
        <div className="mt-6 flex flex-wrap gap-3">
          <LinkButton to="/services" size="lg" className="bg-accent-400 text-ink hover:bg-accent-500">
            Browse services
          </LinkButton>
          <LinkButton to="/register" size="lg" variant="secondary">
            Create an account
          </LinkButton>
        </div>
      </section>
      <section aria-labelledby="how" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <h2 id="how" className="sr-only">
          What you can do
        </h2>
        {features.map((f) => (
          <div key={f.title} className="rounded-card border border-line bg-white p-5">
            <f.icon className="h-6 w-6 text-brand-600" aria-hidden />
            <h3 className="mt-3 font-semibold">{f.title}</h3>
            <p className="mt-1 text-sm text-muted">{f.text}</p>
          </div>
        ))}
      </section>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="mx-auto max-w-lg py-16 text-center">
      <p className="text-sm font-semibold text-brand-700">404</p>
      <h1 className="mt-2 text-2xl font-bold">Page not found</h1>
      <p className="mt-2 text-muted">The page you are looking for does not exist or has moved.</p>
      <Link to="/" className="mt-6 inline-block text-brand-700 hover:underline">
        Go to the home page
      </Link>
    </div>
  );
}

export function UnauthorizedPage() {
  return (
    <div className="mx-auto max-w-lg py-16 text-center">
      <p className="text-sm font-semibold text-red-700">403</p>
      <h1 className="mt-2 text-2xl font-bold">You do not have access to this page</h1>
      <p className="mt-2 text-muted">
        Your role does not include the permissions needed here. If you believe this is a mistake, contact your administrator.
      </p>
      <Link to="/dashboard" className="mt-6 inline-block text-brand-700 hover:underline">
        Back to your dashboard
      </Link>
    </div>
  );
}
