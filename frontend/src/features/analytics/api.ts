import { apiRequest } from '@/api/client';
import type { DailyPoint, DepartmentRow, GrievanceTrends, ReportSummary, WorkloadRow } from '@/types/api';

export interface RangeQuery {
  from?: string;
  to?: string;
  departmentId?: string;
}

export const reportsApi = {
  summary: (q: RangeQuery, signal?: AbortSignal) => apiRequest<ReportSummary>('/reports/summary', { query: { ...q }, signal }),
  volume: (q: RangeQuery, signal?: AbortSignal) =>
    apiRequest<DailyPoint[]>('/reports/application-volume', { query: { ...q }, signal }),
  departments: (q: RangeQuery, signal?: AbortSignal) =>
    apiRequest<DepartmentRow[]>('/reports/department-performance', { query: { from: q.from, to: q.to }, signal }),
  workload: (q: RangeQuery, signal?: AbortSignal) =>
    apiRequest<WorkloadRow[]>('/reports/workload', { query: { departmentId: q.departmentId }, signal }),
  grievances: (q: RangeQuery, signal?: AbortSignal) =>
    apiRequest<GrievanceTrends>('/reports/grievance-trends', { query: { ...q }, signal }),
};
