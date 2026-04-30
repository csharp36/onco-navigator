import { Link, useRouterState } from '@tanstack/react-router';
import { hasRole } from '@/lib/auth';
import {
  LayoutDashboard, Users, Bell, Activity, Settings, ClipboardList,
} from 'lucide-react';

interface NavItem {
  label: string;
  path: string;
  icon: React.ComponentType<{ size?: number }>;
  roles: string[]; // empty = all authenticated users
}

const NAV_ITEMS: NavItem[] = [
  {
    label: 'Dashboard',
    path: '/',
    icon: LayoutDashboard,
    roles: [], // all roles
  },
  {
    label: 'Patients',
    path: '/patients',
    icon: Users,
    roles: ['ROLE_NURSE_NAVIGATOR', 'ROLE_CARE_COORDINATOR', 'ROLE_ADMIN'],
  },
  {
    label: 'Alerts',
    path: '/alerts',
    icon: Bell,
    roles: ['ROLE_NURSE_NAVIGATOR', 'ROLE_ADMIN'],
  },
  {
    label: 'Pathways',
    path: '/pathways',
    icon: Activity,
    roles: ['ROLE_ADMIN'],
  },
  {
    label: 'Audit Log',
    path: '/audit',
    icon: ClipboardList,
    roles: ['ROLE_ADMIN'],
  },
  {
    label: 'Settings',
    path: '/settings',
    icon: Settings,
    roles: ['ROLE_ADMIN'],
  },
];

interface NavSidebarProps {
  onNavigate?: () => void;
}

export function NavSidebar({ onNavigate }: NavSidebarProps) {
  const routerState = useRouterState();
  const currentPath = routerState.location.pathname;

  const visibleItems = NAV_ITEMS.filter(item =>
    item.roles.length === 0 || item.roles.some(role => hasRole(role)),
  );

  return (
    <nav className="space-y-1 p-4">
      {visibleItems.map(item => {
        const isActive = currentPath === item.path;
        const Icon = item.icon;
        return (
          <Link
            key={item.path}
            to={item.path}
            onClick={onNavigate}
            className={`
              flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors
              ${isActive
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'}
            `}
          >
            <Icon size={18} />
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
