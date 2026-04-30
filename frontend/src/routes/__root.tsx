import { createRootRoute, Outlet } from '@tanstack/react-router';
import { AppShell } from '@/components/layout/app-shell';
import { isAuthenticated } from '@/lib/auth';

export const Route = createRootRoute({
  component: () => {
    if (!isAuthenticated()) {
      return (
        <div className="flex items-center justify-center h-screen">
          <p className="text-lg">Redirecting to login...</p>
        </div>
      );
    }
    return (
      <AppShell>
        <Outlet />
      </AppShell>
    );
  },
});
