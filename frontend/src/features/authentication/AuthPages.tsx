import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import type { z } from 'zod';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import { Alert, Button, Card, Checkbox, TextInput } from '@/components/ui';
import { emailSchema, loginSchema, registerSchema, resetSchema } from '@/validation/schemas';
import { accountApi } from './api';

function AuthCard({ title, subtitle, children }: { title: string; subtitle?: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-md">
      <h1 className="text-2xl font-bold">{title}</h1>
      {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      <Card className="mt-5">{children}</Card>
    </div>
  );
}

/** Applies server field errors to the form so they appear next to the right inputs. */
function applyServerErrors<T extends Record<string, unknown>>(
  error: unknown,
  setError: (name: keyof T & string, value: { message: string }) => void,
  known: (keyof T & string)[],
): string | null {
  if (!(error instanceof ApiError)) return 'Something went wrong. Please try again.';
  let unmatched = false;
  error.fieldErrors.forEach((fe) => {
    if (known.includes(fe.field as keyof T & string)) setError(fe.field as keyof T & string, { message: fe.message });
    else unmatched = true;
  });
  return error.fieldErrors.length === 0 || unmatched ? error.message : null;
}

export function LoginPage() {
  const { login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as { from?: string; loggedOut?: boolean; registered?: boolean; reset?: boolean };
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof loginSchema>>({ resolver: zodResolver(loginSchema) });

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await login(values.email, values.password);
      navigate(state.from ?? '/dashboard', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Unable to sign in right now. Please try again.');
    }
  });

  return (
    <AuthCard title="Sign in" subtitle="Access your applications, grievances and work queue.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {sessionExpired && <Alert tone="warning">Your session has expired. Please sign in again.</Alert>}
        {state.loggedOut && <Alert tone="success">You have been signed out.</Alert>}
        {state.registered && <Alert tone="success">Registration complete. You can now sign in.</Alert>}
        {state.reset && <Alert tone="success">Password changed. Sign in with your new password.</Alert>}
        {error && <Alert tone="error">{error}</Alert>}
        <TextInput label="Email" type="email" autoComplete="username" required {...form.register('email')} error={form.formState.errors.email?.message} />
        <TextInput
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          {...form.register('password')}
          error={form.formState.errors.password?.message}
        />
        <Button type="submit" className="w-full" loading={form.formState.isSubmitting}>
          Sign in
        </Button>
        <div className="flex justify-between text-sm">
          <Link className="text-brand-700 hover:underline" to="/forgot-password">
            Forgot password?
          </Link>
          <Link className="text-brand-700 hover:underline" to="/register">
            Create an account
          </Link>
        </div>
      </form>
    </AuthCard>
  );
}

export function RegisterPage() {
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  type Values = z.infer<typeof registerSchema>;
  const form = useForm<Values>({ resolver: zodResolver(registerSchema) });
  const errors = form.formState.errors;

  const onSubmit = form.handleSubmit(async (values) => {
    setError(null);
    try {
      await accountApi.register({
        email: values.email,
        password: values.password,
        fullName: values.fullName,
        phone: values.phone || undefined,
      });
      navigate('/login', { replace: true, state: { registered: true } });
    } catch (e) {
      setError(applyServerErrors<Values>(e, form.setError, ['email', 'password', 'fullName', 'phone']));
    }
  });

  return (
    <AuthCard title="Create a citizen account" subtitle="Use synthetic details only — this is a demonstration system.">
      <form onSubmit={onSubmit} noValidate className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        <TextInput label="Full name" autoComplete="name" required {...form.register('fullName')} error={errors.fullName?.message} />
        <TextInput label="Email" type="email" autoComplete="email" required {...form.register('email')} error={errors.email?.message} />
        <TextInput label="Phone (optional)" type="tel" autoComplete="tel" {...form.register('phone')} error={errors.phone?.message} />
        <TextInput
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          hint="12–64 characters with at least one letter and one digit."
          {...form.register('password')}
          error={errors.password?.message}
        />
        <TextInput
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          required
          {...form.register('confirmPassword')}
          error={errors.confirmPassword?.message}
        />
        <Checkbox
          label="I understand JanSetu is a portfolio simulation, not an official government service, and I will not enter real personal data."
          {...form.register('consent')}
          error={errors.consent?.message}
        />
        <Button type="submit" className="w-full" loading={form.formState.isSubmitting}>
          Create account
        </Button>
        <p className="text-center text-sm">
          Already registered?{' '}
          <Link className="text-brand-700 hover:underline" to="/login">
            Sign in
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}

export function ForgotPasswordPage() {
  const [sent, setSent] = useState<string | null>(null);
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  return (
    <AuthCard title="Reset your password" subtitle="We will send a reset link if an active account exists for this address.">
      {sent ? (
        <Alert tone="success" title="Check your email">
          {sent} The link expires in 30 minutes.
        </Alert>
      ) : (
        <form
          noValidate
          className="space-y-4"
          onSubmit={async (e) => {
            e.preventDefault();
            const parsed = emailSchema.safeParse(email);
            if (!parsed.success) {
              setError(parsed.error.issues[0].message);
              return;
            }
            setError(null);
            setLoading(true);
            try {
              setSent((await accountApi.forgotPassword(parsed.data)).message);
            } catch (err) {
              setError(err instanceof ApiError ? err.message : 'Request failed. Try again later.');
            } finally {
              setLoading(false);
            }
          }}
        >
          <TextInput label="Email" type="email" required value={email} onChange={(e) => setEmail(e.target.value)} error={error ?? undefined} />
          <Button type="submit" className="w-full" loading={loading}>
            Send reset link
          </Button>
        </form>
      )}
      <p className="mt-4 text-center text-sm">
        <Link className="text-brand-700 hover:underline" to="/login">
          Back to sign in
        </Link>
      </p>
    </AuthCard>
  );
}

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);
  const form = useForm<z.infer<typeof resetSchema>>({ resolver: zodResolver(resetSchema) });

  if (!token) {
    return (
      <AuthCard title="Reset link invalid">
        <Alert tone="error">This reset link is incomplete. Request a new one.</Alert>
        <Link className="mt-4 inline-block text-sm text-brand-700 hover:underline" to="/forgot-password">
          Request a new link
        </Link>
      </AuthCard>
    );
  }

  return (
    <AuthCard title="Choose a new password">
      <form
        noValidate
        className="space-y-4"
        onSubmit={form.handleSubmit(async (values) => {
          setError(null);
          try {
            await accountApi.resetPassword(token, values.password);
            navigate('/login', { replace: true, state: { reset: true } });
          } catch (e) {
            setError(e instanceof ApiError ? e.message : 'Reset failed. Request a new link.');
          }
        })}
      >
        {error && <Alert tone="error">{error}</Alert>}
        <TextInput
          label="New password"
          type="password"
          autoComplete="new-password"
          required
          hint="12–64 characters with at least one letter and one digit."
          {...form.register('password')}
          error={form.formState.errors.password?.message}
        />
        <TextInput
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          required
          {...form.register('confirmPassword')}
          error={form.formState.errors.confirmPassword?.message}
        />
        <Button type="submit" className="w-full" loading={form.formState.isSubmitting}>
          Set new password
        </Button>
      </form>
    </AuthCard>
  );
}
