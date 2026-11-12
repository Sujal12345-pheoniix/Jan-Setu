import { apiBlob, apiRequest, uploadFile } from '@/api/client';
import type { DocumentView, GrievanceDetail, GrievanceSummary, Page } from '@/types/api';

export interface GrievanceQuery {
  page?: number;
  size?: number;
  status?: string;
  category?: string;
  departmentId?: string;
  assignedToMe?: boolean;
  q?: string;
}

/** Mirrors GrievanceService.CATEGORIES (also served by GET /grievances/categories). */
export const GRIEVANCE_CATEGORIES = [
  'SERVICE_DELAY',
  'STAFF_CONDUCT',
  'INCORRECT_DECISION',
  'DOCUMENT_ISSUE',
  'PORTAL_ISSUE',
  'OTHER',
] as const;

export const grievancesApi = {
  list: (query: GrievanceQuery, signal?: AbortSignal) =>
    apiRequest<Page<GrievanceSummary>>('/grievances', { query: { ...query }, signal }),
  get: (id: string, signal?: AbortSignal) => apiRequest<GrievanceDetail>(`/grievances/${id}`, { signal }),
  create: (body: { departmentId?: string; category: string; subject: string; description: string; applicationId?: string }) =>
    apiRequest<GrievanceDetail>('/grievances', { method: 'POST', body }),
  action: (id: string, action: string, body?: Record<string, unknown>) =>
    apiRequest<GrievanceDetail>(`/grievances/${id}/${action}`, { method: 'POST', body }),
  uploadEvidence: (id: string, file: File, onProgress?: (p: number) => void) => {
    const form = new FormData();
    form.append('file', file);
    return uploadFile<DocumentView>(`/grievances/${id}/documents`, form, onProgress);
  },
  downloadEvidence: (id: string, documentId: string) => apiBlob(`/grievances/${id}/documents/${documentId}/download`),
};
