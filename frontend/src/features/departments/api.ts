import { apiRequest } from '@/api/client';
import type { Department, Member, Page } from '@/types/api';

export const departmentsApi = {
  list: (q: { page?: number; size?: number; q?: string; status?: string }, signal?: AbortSignal) =>
    apiRequest<Page<Department>>('/departments', { query: { ...q }, signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<Department>(`/departments/${id}`, { signal }),
  create: (body: Record<string, unknown>, idempotencyKey: string) =>
    apiRequest<Department>('/departments', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
  update: (id: string, body: Record<string, unknown>) =>
    apiRequest<Department>(`/departments/${id}`, { method: 'PATCH', body }),
  setActive: (id: string, active: boolean) =>
    apiRequest<Department>(`/departments/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
  members: (id: string, q: { page?: number; size?: number }, signal?: AbortSignal) =>
    apiRequest<Page<Member>>(`/departments/${id}/members`, { query: { ...q }, signal }),
  addMember: (id: string, userId: string, position?: string) =>
    apiRequest<Member>(`/departments/${id}/members`, { method: 'POST', body: { userId, position } }),
  removeMember: (id: string, userId: string) =>
    apiRequest<void>(`/departments/${id}/members/${userId}`, { method: 'DELETE' }),
};
