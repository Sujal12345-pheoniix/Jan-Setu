import { apiBlob, apiRequest } from '@/api/client';
import type { AuditLogEntry, Page } from '@/types/api';

export interface AuditQuery {
  page?: number;
  size?: number;
  actorId?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  outcome?: string;
  from?: string;
  to?: string;
}

export const auditApi = {
  search: (q: AuditQuery, signal?: AbortSignal) => apiRequest<Page<AuditLogEntry>>('/audit-logs', { query: { ...q }, signal }),
  exportCsv: (q: AuditQuery) => apiBlob('/audit-logs/export', { ...q, page: undefined, size: undefined }),
};
