import { apiBlob, apiRequest, uploadFile } from '@/api/client';
import type { ApplicationDetail, ApplicationSummary, DocumentView, Page } from '@/types/api';

export interface ApplicationQuery {
  page?: number;
  size?: number;
  status?: string;
  serviceId?: string;
  departmentId?: string;
  q?: string;
  assignedToMe?: boolean;
  overdue?: boolean;
}

export const applicationsApi = {
  list: (query: ApplicationQuery, signal?: AbortSignal) =>
    apiRequest<Page<ApplicationSummary>>('/applications', { query: { ...query }, signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<ApplicationDetail>(`/applications/${id}`, { signal }),
  createDraft: (serviceId: string) => apiRequest<ApplicationDetail>('/applications', { method: 'POST', body: { serviceId } }),
  saveDraft: (id: string, formData: Record<string, unknown>, version: number) =>
    apiRequest<ApplicationDetail>(`/applications/${id}`, { method: 'PATCH', body: { formData, version } }),
  submit: (id: string, idempotencyKey: string) =>
    apiRequest<{ id: string; referenceNumber: string; status: string }>(`/applications/${id}/submit`, {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  withdraw: (id: string, reason?: string) =>
    apiRequest<ApplicationDetail>(`/applications/${id}/withdraw`, { method: 'POST', body: { reason } }),
  respondToClarification: (id: string, response: string) =>
    apiRequest<ApplicationDetail>(`/applications/${id}/clarification-response`, { method: 'POST', body: { response } }),
  uploadDocument: (id: string, requirementCode: string, file: File, onProgress?: (p: number) => void, signal?: AbortSignal) => {
    const form = new FormData();
    form.append('file', file);
    return uploadFile<DocumentView>(
      `/applications/${id}/documents?requirementCode=${encodeURIComponent(requirementCode)}`,
      form,
      onProgress,
      signal,
    );
  },
  downloadDocument: (id: string, documentId: string, version?: number) =>
    apiBlob(`/applications/${id}/documents/${documentId}/download`, { version }),

  // staff
  action: (id: string, action: string, body?: Record<string, unknown>) =>
    apiRequest<ApplicationDetail>(`/applications/${id}/${action}`, { method: 'POST', body }),
  verifyDocument: (id: string, documentId: string, accept: boolean, reason?: string) =>
    apiRequest<DocumentView>(`/applications/${id}/documents/${documentId}/verification`, {
      method: 'POST',
      body: { accept, reason },
    }),
  comment: (id: string, body: string) =>
    apiRequest<ApplicationDetail>(`/applications/${id}/comments`, { method: 'POST', body: { body } }),
};
