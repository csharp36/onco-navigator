import { type ReactNode } from 'react';
import { hasRole, type UserRole } from '@/lib/auth';

interface ProtectedRouteProps {
  children: ReactNode;
  requiredRoles: UserRole[];
  fallback?: ReactNode;
}

export function ProtectedRoute({ children, requiredRoles, fallback }: ProtectedRouteProps) {
  const authorized = requiredRoles.length === 0 || requiredRoles.some(role => hasRole(role));

  if (!authorized) {
    return fallback ?? (
      <div className="flex items-center justify-center h-64">
        <div className="text-center">
          <h2 className="text-xl font-semibold">Access Denied</h2>
          <p className="text-muted-foreground mt-2">
            You do not have permission to view this page.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
