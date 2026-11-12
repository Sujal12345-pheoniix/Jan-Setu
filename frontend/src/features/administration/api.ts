import { apiRequest } from '@/api/client';
import type { Page, Permission, Role, RoleSummary, UserResponse } from '@/types/api';

export interface UserQuery {
  page?: number;
  size?: number;
  q?: string;
  status?: string;
  userType?: string;
  departmentId?: string;
  sort?: string;
}

export const usersApi = {
  list: (q: UserQuery, signal?: AbortSignal) => apiRequest<Page<UserResponse>>('/users', { query: { ...q }, signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<UserResponse>(`/users/${id}`, { signal }),
  create: (body: Record<string, unknown>, idempotencyKey: string) =>
    apiRequest<UserResponse>('/users', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
  update: (id: string, body: Record<string, unknown>) => apiRequest<UserResponse>(`/users/${id}`, { method: 'PATCH', body }),
  setStatus: (id: string, action: 'suspend' | 'deactivate' | 'reactivate', reason?: string) =>
    apiRequest<UserResponse>(`/users/${id}/${action}`, { method: 'POST', body: { reason } }),
  replaceRoles: (id: string, roles: string[]) =>
    apiRequest<UserResponse>(`/users/${id}/roles`, { method: 'PUT', body: { roles } }),
};

export const rolesApi = {
  list: (signal?: AbortSignal) => apiRequest<Page<RoleSummary>>('/roles', { query: { size: 100 }, signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<Role>(`/roles/${id}`, { signal }),
  create: (body: { code: string; name: string; description?: string; permissions: string[] }) =>
    apiRequest<Role>('/roles', { method: 'POST', body }),
  replacePermissions: (id: string, permissions: string[], version: number) =>
    apiRequest<Role>(`/roles/${id}/permissions`, { method: 'PUT', body: { permissions, version } }),
  permissions: (signal?: AbortSignal) => apiRequest<Permission[]>('/permissions', { signal }),
};
