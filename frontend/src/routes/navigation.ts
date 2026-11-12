import type { LucideIcon } from 'lucide-react';
import {
  BarChart3,
  Bell,
  Building2,
  ClipboardList,
  FileSearch,
  FileText,
  GitBranch,
  LayoutDashboard,
  ListChecks,
  MessageSquareWarning,
  Server,
  ShieldCheck,
  Store,
  UserCog,
  Users,
} from 'lucide-react';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Shown when the user holds ANY of these permissions (empty = every signed-in user). UX only. */
  anyOf: string[];
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

export const NAV_SECTIONS: NavSection[] = [
  {
    title: 'Overview',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, anyOf: [] },
      { to: '/notifications', label: 'Notifications', icon: Bell, anyOf: [] },
    ],
  },
  {
    title: 'My services',
    items: [
      { to: '/services', label: 'Browse services', icon: Store, anyOf: ['APPLICATION:CREATE'] },
      { to: '/my/applications', label: 'My applications', icon: FileText, anyOf: ['APPLICATION:READ_OWN'] },
      { to: '/my/grievances', label: 'My grievances', icon: MessageSquareWarning, anyOf: ['GRIEVANCE:READ_OWN'] },
    ],
  },
  {
    title: 'Casework',
    items: [
      { to: '/work/tasks', label: 'Task queue', icon: ListChecks, anyOf: ['TASK:READ'] },
      { to: '/work/applications', label: 'Applications', icon: ClipboardList, anyOf: ['APPLICATION:READ_DEPARTMENT'] },
      { to: '/work/grievances', label: 'Grievances', icon: MessageSquareWarning, anyOf: ['GRIEVANCE:READ_DEPARTMENT'] },
    ],
  },
  {
    title: 'Department',
    items: [
      { to: '/admin/services', label: 'Service catalogue', icon: Store, anyOf: ['SERVICE:READ_ALL'] },
      { to: '/admin/workflow', label: 'Workflow & SLA', icon: GitBranch, anyOf: ['SERVICE:READ_ALL', 'SLA:MANAGE'] },
      { to: '/admin/departments', label: 'Departments & staff', icon: Building2, anyOf: ['DEPARTMENT:MANAGE_MEMBERS', 'DEPARTMENT:CREATE'] },
      { to: '/reports', label: 'Reports', icon: BarChart3, anyOf: ['REPORT:READ_DEPARTMENT', 'REPORT:READ_ALL'] },
    ],
  },
  {
    title: 'Platform',
    items: [
      { to: '/admin/users', label: 'Users', icon: Users, anyOf: ['USER:READ'] },
      { to: '/admin/roles', label: 'Roles & permissions', icon: ShieldCheck, anyOf: ['ROLE:READ'] },
      { to: '/admin/system', label: 'System health', icon: Server, anyOf: ['USER:CREATE'] },
      { to: '/audit', label: 'Audit log', icon: FileSearch, anyOf: ['AUDIT:READ'] },
    ],
  },
  {
    title: 'Account',
    items: [{ to: '/profile', label: 'Profile & security', icon: UserCog, anyOf: [] }],
  },
];

export function visibleSections(can: (permission: string) => boolean): NavSection[] {
  return NAV_SECTIONS.map((section) => ({
    ...section,
    items: section.items.filter((item) => item.anyOf.length === 0 || item.anyOf.some(can)),
  })).filter((section) => section.items.length > 0);
}
