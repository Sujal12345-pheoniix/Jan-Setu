import { apiRequest } from '@/api/client';
import type {
  DepartmentSummary,
  Page,
  PublicServiceDetail,
  PublicServiceSummary,
  RequirementResponse,
  ServiceDetail,
  ServiceSummary,
} from '@/types/api';

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string;
}

export const servicesApi = {
  browse: (params: PageParams & { q?: string; category?: string; departmentId?: string }, signal?: AbortSignal) =>
    apiRequest<Page<PublicServiceSummary>>('/services', { query: { ...params }, signal }),
  published: (id: string, signal?: AbortSignal) => apiRequest<PublicServiceDetail>(`/services/${id}`, { signal }),
  publicDepartments: (signal?: AbortSignal) => apiRequest<DepartmentSummary[]>('/departments/public', { signal }),

  manageList: (params: PageParams & { q?: string; status?: string; departmentId?: string }, signal?: AbortSignal) =>
    apiRequest<Page<ServiceSummary>>('/services/manage', { query: { ...params }, signal }),
  manageGet: (id: string, signal?: AbortSignal) => apiRequest<ServiceDetail>(`/services/${id}/manage`, { signal }),
  create: (body: Record<string, unknown>, idempotencyKey: string) =>
    apiRequest<ServiceDetail>('/services', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } }),
  update: (id: string, body: Record<string, unknown>) =>
    apiRequest<ServiceDetail>(`/services/${id}`, { method: 'PATCH', body }),
  publish: (id: string, version: number) =>
    apiRequest<{ serviceId: string; version: number }>(`/services/${id}/publish`, { method: 'POST', body: { version } }),
  deprecate: (id: string) => apiRequest<ServiceSummary>(`/services/${id}/deprecate`, { method: 'POST' }),
  addRequirement: (id: string, body: Record<string, unknown>) =>
    apiRequest<RequirementResponse>(`/services/${id}/requirements`, { method: 'POST', body }),
  updateRequirement: (id: string, requirementId: string, body: Record<string, unknown>) =>
    apiRequest<RequirementResponse>(`/services/${id}/requirements/${requirementId}`, { method: 'PATCH', body }),
  removeRequirement: (id: string, requirementId: string) =>
    apiRequest<void>(`/services/${id}/requirements/${requirementId}`, { method: 'DELETE' }),
};
