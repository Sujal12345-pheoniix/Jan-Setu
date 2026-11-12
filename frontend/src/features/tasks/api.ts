import { apiRequest } from '@/api/client';
import type { Page, TaskQueueItem } from '@/types/api';

export interface TaskQuery {
  page?: number;
  size?: number;
  mine?: boolean;
  unassigned?: boolean;
  step?: string;
  overdue?: boolean;
  departmentId?: string;
}

export const tasksApi = {
  list: (query: TaskQuery, signal?: AbortSignal) => apiRequest<Page<TaskQueueItem>>('/tasks', { query: { ...query }, signal }),
  claim: (taskId: string) => apiRequest<{ status: string }>(`/tasks/${taskId}/claim`, { method: 'POST' }),
  assign: (taskId: string, assigneeId: string) =>
    apiRequest<{ status: string }>(`/tasks/${taskId}/assign`, { method: 'POST', body: { assigneeId } }),
};
