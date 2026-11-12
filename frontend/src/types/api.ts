/**
 * TypeScript mirrors of the backend DTOs (backend/src/main/java/.../api and .../service records).
 * Keep in sync with OpenAPI at /v3/api-docs; do not invent fields the backend does not return.
 */

export interface Envelope<T> {
  data: T;
  meta: { requestId: string; timestamp: string };
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  errorCode?: string;
  requestId?: string;
  fieldErrors?: { field: string; message: string }[];
}

// ---- Auth / users -------------------------------------------------------------------------------------------

export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  expiresAt: string;
  refreshExpiresAt: string;
}

export interface UserResponse {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  userType: 'CITIZEN' | 'STAFF';
  status: 'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED';
  statusReason?: string;
  passwordChangeRequired: boolean;
  lastLoginAt?: string;
  roles: string[];
  departmentIds: string[];
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface CurrentUser {
  user: UserResponse;
  permissions: string[];
}

export interface SessionInfo {
  id: string;
  createdAt: string;
  lastUsedAt?: string;
  expiresAt: string;
  userAgent?: string;
  ipAddress?: string;
  current: boolean;
}

export interface ActivityItem {
  occurredAt: string;
  action: string;
  outcome: string;
  ipAddress?: string;
}

export interface RoleSummary {
  id: string;
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  version: number;
}

export interface Role extends RoleSummary {
  permissions: string[];
}

export interface Permission {
  code: string;
  category: string;
  description: string;
}

// ---- Departments ---------------------------------------------------------------------------------------------

export interface Department {
  id: string;
  code: string;
  name: string;
  description?: string;
  contactEmail?: string;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface DepartmentSummary {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

export interface Member {
  userId: string;
  email: string;
  fullName: string;
  status: string;
  position?: string;
  joinedAt: string;
  roles: string[];
}

// ---- Catalogue -----------------------------------------------------------------------------------------------

export type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'email' | 'phone' | 'checkbox' | 'select';

export interface FormField {
  key: string;
  label: string;
  type: FieldType;
  required?: boolean;
  options?: string[];
  helpText?: string;
  maxLength?: number;
}

export interface FormSchema {
  fields?: FormField[];
}

export interface RequirementSnapshot {
  code: string;
  name: string;
  description?: string;
  requirementType: 'DOCUMENT' | 'DECLARATION' | 'INFORMATION';
  mandatory: boolean;
  allowedMediaTypes?: string;
  maxSizeBytes?: number;
  displayOrder: number;
}

export interface PublicServiceSummary {
  id: string;
  code: string;
  departmentId: string;
  name: string;
  category: string;
  description: string;
  processingDays: number;
  feeAmount: number;
  version: number;
  publishedAt: string;
}

export interface PublicServiceDetail {
  id: string;
  code: string;
  departmentId: string;
  version: number;
  name: string;
  category: string;
  description: string;
  eligibilityText?: string;
  processingDays: number;
  feeAmount: number;
  formSchema: FormSchema;
  requirements: RequirementSnapshot[];
  publishedAt: string;
}

export interface RequirementResponse {
  id: string;
  code: string;
  name: string;
  description?: string;
  requirementType: RequirementSnapshot['requirementType'];
  mandatory: boolean;
  allowedMediaTypes?: string;
  maxSizeBytes?: number;
  displayOrder: number;
  version: number;
}

export interface ServiceSummary {
  id: string;
  departmentId: string;
  code: string;
  name: string;
  category: string;
  status: 'DRAFT' | 'PUBLISHED' | 'DEPRECATED';
  currentVersion: number;
  hasUnpublishedChanges: boolean;
  updatedAt: string;
  version: number;
}

export interface ServiceDetail extends ServiceSummary {
  description: string;
  eligibilityText?: string;
  processingDays: number;
  feeAmount: number;
  formSchema: FormSchema;
  requirements: RequirementResponse[];
  createdAt: string;
}

// ---- Applications --------------------------------------------------------------------------------------------

export type ApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'CLARIFICATION_REQUIRED'
  | 'RESUBMITTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'COMPLETED'
  | 'WITHDRAWN';

export interface ApplicationSummary {
  id: string;
  referenceNumber?: string;
  status: ApplicationStatus;
  serviceId: string;
  serviceName?: string;
  departmentId: string;
  citizenId?: string;
  applicantName?: string;
  createdAt: string;
  submittedAt?: string;
  decidedAt?: string;
  updatedAt: string;
  currentStep?: 'REVIEW' | 'APPROVAL';
  taskStatus?: string;
  assigneeId?: string;
  assigneeName?: string;
  dueAt?: string;
  overdue: boolean;
  version: number;
}

export interface DocumentVersionView {
  versionNumber: number;
  fileName: string;
  mediaType: string;
  sizeBytes: number;
  uploadedAt: string;
}

export interface DocumentView {
  id: string;
  applicationId?: string;
  grievanceId?: string;
  ownerId: string;
  requirementCode?: string;
  status: 'AVAILABLE' | 'VERIFIED' | 'REJECTED' | 'UPLOADED' | 'QUARANTINED' | 'SCANNING';
  currentVersion: number;
  fileName?: string;
  mediaType?: string;
  sizeBytes: number;
  checksum?: string;
  uploadedAt?: string;
  rejectionReason?: string;
  verifiedAt?: string;
  versions: DocumentVersionView[];
}

export interface ApplicationDetail {
  summary: ApplicationSummary;
  service: {
    serviceVersionId: string;
    versionNumber: number;
    code: string;
    name: string;
    processingDays: number;
    formSchema: FormSchema;
    requirements: RequirementSnapshot[];
  };
  formData: Record<string, unknown>;
  documents: DocumentView[];
  clarifications: {
    id: string;
    question: string;
    dueAt: string;
    response?: string;
    respondedAt?: string;
    status: string;
    createdAt: string;
  }[];
  decisions: { decision: string; reason?: string; decidedByName?: string; decidedAt: string }[];
  history: { fromStatus?: string; toStatus: string; command: string; actorName?: string; reason?: string; occurredAt: string }[];
  tasks?: {
    id: string;
    step: string;
    status: string;
    assigneeId?: string;
    assigneeName?: string;
    dueAt?: string;
    outcome?: string;
    createdAt: string;
    completedAt?: string;
    version: number;
  }[];
  comments?: { id: string; authorName?: string; body: string; createdAt: string }[];
  availableActions: string[];
}

export interface TaskQueueItem {
  taskId: string;
  applicationId: string;
  referenceNumber: string;
  applicationStatus: ApplicationStatus;
  serviceName?: string;
  step: 'REVIEW' | 'APPROVAL';
  taskStatus: string;
  assigneeId?: string;
  assigneeName?: string;
  dueAt?: string;
  overdue: boolean;
  dueSoon: boolean;
  createdAt: string;
  version: number;
}

// ---- Grievances ----------------------------------------------------------------------------------------------

export type GrievanceStatus = 'SUBMITTED' | 'ACKNOWLEDGED' | 'ASSIGNED' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED' | 'REOPENED';

export interface GrievanceSummary {
  id: string;
  referenceNumber: string;
  status: GrievanceStatus;
  category: string;
  subject: string;
  departmentId: string;
  departmentName?: string;
  assigneeId?: string;
  assigneeName?: string;
  citizenName?: string;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface GrievanceDetail {
  summary: GrievanceSummary;
  description: string;
  applicationId?: string;
  resolutionSummary?: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
  closedAt?: string;
  reopenDeadline?: string;
  reopenCount: number;
  comments: { id: string; visibility: 'PUBLIC' | 'INTERNAL'; authorName?: string; byCitizen: boolean; body: string; createdAt: string }[];
  history: { fromStatus?: string; toStatus: string; actorName?: string; note?: string; occurredAt: string }[];
  documents: DocumentView[];
  feedback?: { rating: number; comment?: string; submittedAt: string };
  availableActions: string[];
}

// ---- Notifications, audit, reports ------------------------------------------------------------------------

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string;
  resourceType?: string;
  resourceId?: string;
  readAt?: string;
  createdAt: string;
}

export interface NotificationPreference {
  channel: 'IN_APP' | 'EMAIL';
  category: string;
  enabled: boolean;
}

export interface AuditLogEntry {
  id: string;
  occurredAt: string;
  actorId?: string;
  actorType: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  outcome: string;
  requestId?: string;
  ipAddress?: string;
  details: Record<string, unknown>;
}

export interface ReportSummary {
  range: { from: string; to: string };
  applicationsByStatus: Record<string, number>;
  submitted: number;
  decided: number;
  approved: number;
  rejected: number;
  slaCompliancePct?: number;
  avgProcessingDays?: number;
  activeTasks: number;
  overdueTasks: number;
  grievancesByStatus: Record<string, number>;
  openGrievances: number;
}

export interface DailyPoint {
  date: string;
  submitted: number;
  decided: number;
}

export interface DepartmentRow {
  departmentId: string;
  code: string;
  name: string;
  submitted: number;
  approved: number;
  rejected: number;
  pending: number;
  overdueTasks: number;
  slaCompliancePct?: number;
  avgProcessingDays?: number;
  openGrievances: number;
}

export interface WorkloadRow {
  assigneeId?: string;
  assigneeName: string;
  activeTasks: number;
  overdueTasks: number;
  openGrievances: number;
}

export interface GrievanceTrends {
  daily: { date: string; opened: number; resolved: number }[];
  byCategory: Record<string, number>;
}
