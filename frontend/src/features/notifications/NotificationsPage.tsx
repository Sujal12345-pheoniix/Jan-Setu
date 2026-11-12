import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/auth/AuthProvider';
import { Button, EmptyState, ErrorState, PageHeader, Pagination, TableSkeleton, Tabs } from '@/components/ui';
import { useUrlState } from '@/hooks/useUrlState';
import type { NotificationItem } from '@/types/api';
import { formatDateTime } from '@/utils/format';
import { notificationsApi } from './api';

export function NotificationsPage() {
  const { values, page, set } = useUrlState(['view'] as const);
  const { can } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const unreadOnly = values.view === 'unread';
  const query = useQuery({
    queryKey: ['notifications', 'list', unreadOnly, page],
    queryFn: ({ signal }) => notificationsApi.list({ unreadOnly, page, size: 20 }, signal),
    placeholderData: (prev) => prev,
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['notifications'] });
  const markAll = useMutation({ mutationFn: notificationsApi.markAllRead, onSuccess: invalidate });
  const open = useMutation({
    mutationFn: (n: NotificationItem) => (n.readAt ? Promise.resolve(n) : notificationsApi.markRead(n.id)),
    onSuccess: (n) => {
      invalidate();
      const staff = can('APPLICATION:READ_DEPARTMENT') || can('GRIEVANCE:READ_DEPARTMENT');
      if (n.resourceType === 'APPLICATION' && n.resourceId) navigate(`${staff ? '/work' : '/my'}/applications/${n.resourceId}`);
      if (n.resourceType === 'GRIEVANCE' && n.resourceId) navigate(`${staff ? '/work' : '/my'}/grievances/${n.resourceId}`);
    },
  });
  return (
    <div>
      <PageHeader
        title="Notifications"
        actions={<Button variant="secondary" loading={markAll.isPending} onClick={() => markAll.mutate()}>Mark all as read</Button>}
      />
      <Tabs label="Notification filter" value={unreadOnly ? 'unread' : 'all'} onChange={(v) => set({ view: v === 'all' ? '' : v })} tabs={[{ value: 'all', label: 'All' }, { value: 'unread', label: 'Unread' }]} />
      <div className="mt-4">
        {query.isPending && <TableSkeleton />}
        {query.isError && <ErrorState error={query.error} onRetry={() => query.refetch()} />}
        {query.data && query.data.content.length === 0 && <EmptyState title={unreadOnly ? 'No unread notifications' : 'No notifications yet'} />}
        {query.data && query.data.content.length > 0 && (
          <>
            <ul className="divide-y divide-line rounded-card border border-line bg-white">
              {query.data.content.map((n) => (
                <li key={n.id}>
                  <button type="button" onClick={() => open.mutate(n)} className="flex w-full items-start gap-3 px-4 py-3 text-left hover:bg-canvas">
                    <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${n.readAt ? 'bg-transparent' : 'bg-accent-500'}`} aria-hidden />
                    <span className="flex-1">
                      <span className={`block text-sm ${n.readAt ? '' : 'font-semibold'}`}>{n.title}{!n.readAt && <span className="sr-only"> (unread)</span>}</span>
                      <span className="block text-sm text-muted">{n.body}</span>
                      <span className="block text-xs text-muted">{formatDateTime(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
            <Pagination page={query.data.page} totalPages={query.data.totalPages} totalElements={query.data.totalElements} onChange={(p) => set({ page: p })} />
          </>
        )}
      </div>
    </div>
  );
}
