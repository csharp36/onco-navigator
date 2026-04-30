import { createFileRoute } from '@tanstack/react-router';
import { getUserName, getUserRoles, ROLE_LABELS, type UserRole } from '@/lib/auth';

export const Route = createFileRoute('/')({
  component: DashboardHome,
});

function DashboardHome() {
  const name = getUserName();
  const roles = getUserRoles();
  const primaryRole = roles[0] as UserRole | undefined;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
        <p className="text-muted-foreground mt-1">
          Welcome, {name} ({primaryRole ? ROLE_LABELS[primaryRole] : 'Unknown Role'})
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {/* Placeholder cards — will be wired to real data in Phase 3 */}
        <div className="rounded-lg border bg-card p-6">
          <h3 className="font-semibold">Open Alerts</h3>
          <p className="text-3xl font-bold mt-2">--</p>
          <p className="text-sm text-muted-foreground">Awaiting Phase 3</p>
        </div>
        <div className="rounded-lg border bg-card p-6">
          <h3 className="font-semibold">Active Patients</h3>
          <p className="text-3xl font-bold mt-2">--</p>
          <p className="text-sm text-muted-foreground">Awaiting Phase 3</p>
        </div>
        <div className="rounded-lg border bg-card p-6">
          <h3 className="font-semibold">Pathways Monitored</h3>
          <p className="text-3xl font-bold mt-2">--</p>
          <p className="text-sm text-muted-foreground">Awaiting Phase 3</p>
        </div>
      </div>
    </div>
  );
}
