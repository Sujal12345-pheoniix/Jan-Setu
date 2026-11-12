import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '@/api/client';
import { useAuth } from '@/auth/AuthProvider';
import {
  Alert,
  Badge,
  Button,
  Card,
  Checkbox,
  ConfirmDialog,
  DataTable,
  DescriptionList,
  PageHeader,
  Skeleton,
  StatusBadge,
  Tabs,
  TextInput,
  useToast,
} from '@/components/ui';
import { accountApi } from '@/features/authentication/api';
import { notificationsApi } from '@/features/notifications/api';
import type { NotificationPreference } from '@/types/api';
import { formatDateTime, humanize } from '@/utils/format';
import { passwordSchema, phoneSchema } from '@/validation/schemas';

type Tab = 'profile' | 'security' | 'notifications' | 'sessions' | 'activity';

export function ProfilePage() {
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') ?? 'profile') as Tab;
  return (
    <div className="max-w-4xl">
      <PageHeader title="Profile & security" />
      <Tabs<Tab>
        label="Profile sections"
        value={tab}
        onChange={(t) => setParams({ tab: t }, { replace: true })}
        tabs={[
          { value: 'profile', label: 'Profile' },
          { value: 'security', label: 'Password' },
          { value: 'notifications', label: 'Notifications' },
          { value: 'sessions', label: 'Devices' },
          { value: 'activity', label: 'Activity' },
        ]}
      />
      <div className="mt-5">
        {tab === 'profile' && <ProfileTab />}
        {tab === 'security' && <PasswordTab />}
        {tab === 'notifications' && <PreferencesTab />}
        {tab === 'sessions' && <SessionsTab />}
        {tab === 'activity' && <ActivityTab />}
      </div>
    </div>
  );
}

function ProfileTab() {
  const { user, reload } = useAuth();
  const { notify } = useToast();
  const [fullName, setFullName] = useState(user?.user.fullName ?? '');
  const [phone, setPhone] = useState(user?.user.phone ?? '');
  const [error, setError] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: () => accountApi.updateProfile({ fullName, phone, version: user!.user.version }),
    onSuccess: async () => {
      await reload();
      notify('Profile updated.');
    },
    onError: (e) => setError(e instanceof ApiError ? (e.status === 409 ? 'Your profile changed elsewhere. Reload and try again.' : e.message) : 'Update failed.'),
  });
  if (!user) return null;
  const phoneProblem = phoneSchema.safeParse(phone).success ? undefined : 'Enter a valid phone number';
  return (
    <div className="grid gap-5 md:grid-cols-2">
      <Card title="Personal details">
        <form
          className="space-y-4"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            setError(null);
            if (!phoneProblem && fullName.trim().length >= 2) save.mutate();
          }}
        >
          {error && <Alert tone="error">{error}</Alert>}
          <TextInput label="Full name" required value={fullName} onChange={(e) => setFullName(e.target.value)} error={fullName.trim().length < 2 ? 'Enter at least 2 characters' : undefined} />
          <TextInput label="Phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} error={phoneProblem} />
          <Button type="submit" loading={save.isPending}>Save changes</Button>
        </form>
      </Card>
      <Card title="Account">
        <DescriptionList
          items={[
            { label: 'Email (sign-in)', value: user.user.email },
            { label: 'Account type', value: humanize(user.user.userType) },
            { label: 'Status', value: <StatusBadge status={user.user.status} /> },
            { label: 'Roles', value: user.user.roles.map((r) => <Badge key={r} className="mr-1">{humanize(r)}</Badge>) },
            { label: 'Last sign-in', value: formatDateTime(user.user.lastLoginAt) },
            { label: 'Member since', value: formatDateTime(user.user.createdAt) },
          ]}
        />
      </Card>
    </div>
  );
}

function PasswordTab() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [touched, setTouched] = useState(false);
  const change = useMutation({
    mutationFn: () => accountApi.changePassword(current, next),
    onSuccess: async () => {
      await logout();
      navigate('/login', { replace: true, state: { reset: true } });
    },
  });
  const nextProblem = touched ? passwordSchema.safeParse(next).error?.issues[0]?.message : undefined;
  const confirmProblem = touched && next !== confirm ? 'Passwords do not match' : undefined;
  return (
    <Card title="Change password" className="max-w-lg">
      <form
        className="space-y-4"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          setTouched(true);
          if (passwordSchema.safeParse(next).success && next === confirm && current) change.mutate();
        }}
      >
        <Alert tone="info">Changing your password signs you out on all devices.</Alert>
        {change.isError && <Alert tone="error">{change.error instanceof ApiError ? change.error.message : 'Change failed.'}</Alert>}
        <TextInput label="Current password" type="password" autoComplete="current-password" required value={current} onChange={(e) => setCurrent(e.target.value)} />
        <TextInput label="New password" type="password" autoComplete="new-password" required hint="12–64 characters with at least one letter and one digit." value={next} onChange={(e) => setNext(e.target.value)} error={nextProblem} />
        <TextInput label="Confirm new password" type="password" autoComplete="new-password" required value={confirm} onChange={(e) => setConfirm(e.target.value)} error={confirmProblem} />
        <Button type="submit" loading={change.isPending}>Change password</Button>
      </form>
    </Card>
  );
}

function PreferencesTab() {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['notifications', 'preferences'], queryFn: ({ signal }) => notificationsApi.preferences(signal) });
  const [prefs, setPrefs] = useState<NotificationPreference[]>([]);
  useEffect(() => {
    if (query.data) setPrefs(query.data);
  }, [query.data]);
  const save = useMutation({
    mutationFn: () => notificationsApi.updatePreferences(prefs),
    onSuccess: (data) => {
      queryClient.setQueryData(['notifications', 'preferences'], data);
      notify('Notification preferences saved.');
    },
  });
  if (query.isPending) return <Skeleton className="h-48" />;
  const categories = [...new Set(prefs.map((p) => p.category))];
  const toggle = (channel: string, category: string, enabled: boolean) =>
    setPrefs((all) => all.map((p) => (p.channel === channel && p.category === category ? { ...p, enabled } : p)));
  return (
    <Card title="Notification preferences" className="max-w-2xl">
      <table className="w-full text-sm">
        <caption className="sr-only">Notification preferences by category and channel</caption>
        <thead>
          <tr className="text-left text-muted">
            <th scope="col" className="py-2">Category</th>
            <th scope="col" className="py-2">In-app</th>
            <th scope="col" className="py-2">Email</th>
          </tr>
        </thead>
        <tbody>
          {categories.map((category) => (
            <tr key={category} className="border-t border-line">
              <th scope="row" className="py-2 text-left font-medium">{humanize(category)}</th>
              {(['IN_APP', 'EMAIL'] as const).map((channel) => (
                <td key={channel} className="py-2">
                  <Checkbox
                    label={<span className="sr-only">{`${humanize(category)} via ${channel === 'IN_APP' ? 'in-app' : 'email'}`}</span>}
                    checked={prefs.find((p) => p.channel === channel && p.category === category)?.enabled ?? true}
                    onChange={(e) => toggle(channel, category, e.target.checked)}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      <p className="mt-3 text-xs text-muted">Email delivery is not active in this demo; your choice is stored for when it is.</p>
      <Button className="mt-4" loading={save.isPending} onClick={() => save.mutate()}>Save preferences</Button>
    </Card>
  );
}

function SessionsTab() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [confirmAll, setConfirmAll] = useState(false);
  const query = useQuery({ queryKey: ['auth', 'sessions'], queryFn: ({ signal }) => accountApi.sessions(signal) });
  const revoke = useMutation({
    mutationFn: accountApi.revokeSession,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['auth', 'sessions'] });
      notify('Device signed out.');
    },
  });
  const all = useMutation({
    mutationFn: accountApi.logoutAll,
    onSuccess: async () => {
      await logout();
      navigate('/login', { replace: true, state: { loggedOut: true } });
    },
  });
  if (query.isPending) return <Skeleton className="h-48" />;
  return (
    <Card title="Signed-in devices" actions={<Button variant="danger" size="sm" onClick={() => setConfirmAll(true)}>Sign out everywhere</Button>}>
      <DataTable
        caption="Active sessions"
        rows={query.data ?? []}
        rowKey={(s) => s.id}
        columns={[
          { key: 'device', header: 'Device', render: (s) => <span className="break-all text-xs">{s.userAgent ?? 'Unknown'} {s.current && <Badge tone="success">This device</Badge>}</span> },
          { key: 'ip', header: 'IP', hideOnMobile: true, render: (s) => s.ipAddress ?? '—' },
          { key: 'created', header: 'Signed in', render: (s) => formatDateTime(s.createdAt) },
          { key: 'last', header: 'Last used', hideOnMobile: true, render: (s) => formatDateTime(s.lastUsedAt ?? s.createdAt) },
          { key: 'actions', header: 'Actions', render: (s) => !s.current && <Button size="sm" variant="secondary" onClick={() => revoke.mutate(s.id)}>Sign out</Button> },
        ]}
      />
      <ConfirmDialog open={confirmAll} title="Sign out on every device?" message="You will need to sign in again on all devices, including this one." confirmLabel="Sign out everywhere" danger loading={all.isPending} onConfirm={() => all.mutate()} onCancel={() => setConfirmAll(false)} />
    </Card>
  );
}

function ActivityTab() {
  const query = useQuery({ queryKey: ['auth', 'activity'], queryFn: ({ signal }) => accountApi.activity(signal) });
  if (query.isPending) return <Skeleton className="h-48" />;
  return (
    <Card title="Recent account activity">
      <DataTable
        caption="Your recent activity"
        rows={query.data ?? []}
        rowKey={(a) => `${a.occurredAt}-${a.action}`}
        columns={[
          { key: 'when', header: 'When', render: (a) => formatDateTime(a.occurredAt) },
          { key: 'action', header: 'Action', render: (a) => humanize(a.action) },
          { key: 'outcome', header: 'Outcome', render: (a) => <StatusBadge status={a.outcome} /> },
          { key: 'ip', header: 'IP', hideOnMobile: true, render: (a) => a.ipAddress ?? '—' },
        ]}
      />
    </Card>
  );
}
