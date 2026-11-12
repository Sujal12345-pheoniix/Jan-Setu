import { apiRequest } from '@/api/client';
import type { NotificationItem, NotificationPreference, Page } from '@/types/api';

export const notificationsApi = {
  list: (query: { page?: number; size?: number; unreadOnly?: boolean }, signal?: AbortSignal) =>
    apiRequest<Page<NotificationItem>>('/notifications', { query: { ...query }, signal }),
  unreadCount: (signal?: AbortSignal) => apiRequest<{ unread: number }>('/notifications/unread-count', { signal }),
  markRead: (id: string) => apiRequest<NotificationItem>(`/notifications/${id}/read`, { method: 'POST' }),
  markAllRead: () => apiRequest<{ updated: number }>('/notifications/read-all', { method: 'POST' }),
  preferences: (signal?: AbortSignal) => apiRequest<NotificationPreference[]>('/notifications/preferences', { signal }),
  updatePreferences: (preferences: NotificationPreference[]) =>
    apiRequest<NotificationPreference[]>('/notifications/preferences', { method: 'PUT', body: { preferences } }),
};
