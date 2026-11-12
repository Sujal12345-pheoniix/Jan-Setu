import { lazy, Suspense, type ComponentType } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { RequireAuth, RequirePermission } from '@/auth/guards';
import { PageLoader } from '@/components/ui';
import { AppShell } from '@/layouts/AppShell';
import { PublicLayout } from '@/layouts/PublicLayout';
import { LandingPage, NotFoundPage, UnauthorizedPage } from '@/pages/StaticPages';
import { ForgotPasswordPage, LoginPage, RegisterPage, ResetPasswordPage } from '@/features/authentication/AuthPages';
import { ServiceCataloguePage, ServiceDetailPage } from '@/features/services/CataloguePages';
import { useAuth } from '@/auth/AuthProvider';

/** Code-splits a named page export; each route chunk loads on first visit. */
function lazyNamed<M, K extends keyof M>(loader: () => Promise<M>, name: K) {
  return lazy(() => loader().then((m) => ({ default: m[name] as unknown as ComponentType })));
}

const DashboardPage = lazyNamed(() => import('@/features/citizen/DashboardPage'), 'DashboardPage');
const MyApplicationsPage = lazyNamed(() => import('@/features/citizen/CitizenApplicationPages'), 'MyApplicationsPage');
const CitizenApplicationPage = lazyNamed(() => import('@/features/citizen/CitizenApplicationPages'), 'CitizenApplicationPage');
const ProfilePage = lazyNamed(() => import('@/features/citizen/ProfilePage'), 'ProfilePage');
const grievances = () => import('@/features/grievances/GrievancePages');
const NewGrievancePage = lazyNamed(grievances, 'NewGrievancePage');
const GrievanceListPage = lazy(() => grievances().then((m) => ({ default: m.GrievanceListPage })));
const GrievanceDetailPage = lazy(() => grievances().then((m) => ({ default: m.GrievanceDetailPage })));
const NotificationsPage = lazyNamed(() => import('@/features/notifications/NotificationsPage'), 'NotificationsPage');
const work = () => import('@/features/tasks/WorkPages');
const TaskQueuePage = lazyNamed(work, 'TaskQueuePage');
const StaffApplicationsPage = lazyNamed(work, 'StaffApplicationsPage');
const CaseDetailPage = lazyNamed(work, 'CaseDetailPage');
const serviceAdmin = () => import('@/features/services/ServiceAdminPages');
const ServiceManageListPage = lazyNamed(serviceAdmin, 'ServiceManageListPage');
const NewServicePage = lazyNamed(serviceAdmin, 'NewServicePage');
const ServiceEditorPage = lazyNamed(serviceAdmin, 'ServiceEditorPage');
const depts = () => import('@/features/departments/DepartmentPages');
const DepartmentListPage = lazyNamed(depts, 'DepartmentListPage');
const DepartmentDetailPage = lazyNamed(depts, 'DepartmentDetailPage');
const admin = () => import('@/features/administration/AdminPages');
const UserListPage = lazyNamed(admin, 'UserListPage');
const UserDetailPage = lazyNamed(admin, 'UserDetailPage');
const RolesPage = lazyNamed(admin, 'RolesPage');
const SystemHealthPage = lazyNamed(admin, 'SystemHealthPage');
const WorkflowPage = lazyNamed(() => import('@/features/workflows/WorkflowPage'), 'WorkflowPage');
const AuditPage = lazyNamed(() => import('@/features/audit/AuditPage'), 'AuditPage');
const ReportsPage = lazyNamed(() => import('@/features/analytics/ReportsPage'), 'ReportsPage');

/** Public catalogue pages render inside the app shell when signed in, otherwise in the public layout. */
function CatalogueLayout() {
  const { status } = useAuth();
  return status === 'authenticated' ? <AppShell /> : <PublicLayout />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<PageLoader />}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<LandingPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="forgot-password" element={<ForgotPasswordPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />
        </Route>
        <Route element={<CatalogueLayout />}>
          <Route path="services" element={<ServiceCataloguePage />} />
          <Route path="services/:id" element={<ServiceDetailPage />} />
        </Route>

        <Route element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="unauthorized" element={<UnauthorizedPage />} />

          <Route element={<RequirePermission anyOf={['APPLICATION:READ_OWN']} />}>
            <Route path="my/applications" element={<MyApplicationsPage />} />
            <Route path="my/applications/:id" element={<CitizenApplicationPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['GRIEVANCE:READ_OWN']} />}>
            <Route path="my/grievances" element={<GrievanceListPage />} />
            <Route path="my/grievances/new" element={<NewGrievancePage />} />
            <Route path="my/grievances/:id" element={<GrievanceDetailPage />} />
          </Route>

          <Route element={<RequirePermission anyOf={['TASK:READ']} />}>
            <Route path="work/tasks" element={<TaskQueuePage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['APPLICATION:READ_DEPARTMENT']} />}>
            <Route path="work/applications" element={<StaffApplicationsPage />} />
            <Route path="work/applications/:id" element={<CaseDetailPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['GRIEVANCE:READ_DEPARTMENT']} />}>
            <Route path="work/grievances" element={<GrievanceListPage staff />} />
            <Route path="work/grievances/:id" element={<GrievanceDetailPage staff />} />
          </Route>

          <Route element={<RequirePermission anyOf={['SERVICE:READ_ALL']} />}>
            <Route path="admin/services" element={<ServiceManageListPage />} />
            <Route path="admin/services/new" element={<NewServicePage />} />
            <Route path="admin/services/:id" element={<ServiceEditorPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['SERVICE:READ_ALL', 'SLA:MANAGE']} />}>
            <Route path="admin/workflow" element={<WorkflowPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['DEPARTMENT:MANAGE_MEMBERS', 'DEPARTMENT:CREATE']} />}>
            <Route path="admin/departments" element={<DepartmentListPage />} />
            <Route path="admin/departments/:id" element={<DepartmentDetailPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['USER:READ']} />}>
            <Route path="admin/users" element={<UserListPage />} />
            <Route path="admin/users/:id" element={<UserDetailPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['ROLE:READ']} />}>
            <Route path="admin/roles" element={<RolesPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['USER:CREATE']} />}>
            <Route path="admin/system" element={<SystemHealthPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['AUDIT:READ']} />}>
            <Route path="audit" element={<AuditPage />} />
          </Route>
          <Route element={<RequirePermission anyOf={['REPORT:READ_DEPARTMENT', 'REPORT:READ_ALL']} />}>
            <Route path="reports" element={<ReportsPage />} />
          </Route>
        </Route>

        <Route element={<PublicLayout />}>
          <Route path="404" element={<NotFoundPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>
        <Route path="home" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
