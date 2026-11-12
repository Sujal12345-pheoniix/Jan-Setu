import { apiRequest } from '@/api/client';
import type { ActivityItem, SessionInfo, UserResponse } from '@/types/api';

export const accountApi = {
  register: (body: { email: string; password: string; fullName: string; phone?: string }) =>
    apiRequest<UserResponse>('/auth/register', { method: 'POST', body, noRetry: true }),
  forgotPassword: (email: string) =>
    apiRequest<{ message: string }>('/auth/forgot-password', { method: 'POST', body: { email }, noRetry: true }),
  resetPassword: (token: string, newPassword: string) =>
    apiRequest<{ message: string }>('/auth/reset-password', { method: 'POST', body: { token, newPassword }, noRetry: true }),
  changePassword: (currentPassword: string, newPassword: string) =>
    apiRequest<void>('/auth/change-password', { method: 'POST', body: { currentPassword, newPassword } }),
  updateProfile: (body: { fullName?: string; phone?: string; version: number }) =>
    apiRequest<UserResponse>('/auth/me', { method: 'PATCH', body }),
  sessions: (signal?: AbortSignal) => apiRequest<SessionInfo[]>('/auth/sessions', { signal }),
  revokeSession: (id: string) => apiRequest<void>(`/auth/sessions/${id}`, { method: 'DELETE' }),
  logoutAll: () => apiRequest<void>('/auth/logout-all', { method: 'POST' }),
  activity: (signal?: AbortSignal) => apiRequest<ActivityItem[]>('/auth/activity', { signal }),
};
