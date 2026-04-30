import { useState } from 'react';
import { createFileRoute } from '@tanstack/react-router';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useAlerts } from '@/features/alerts/api';
import { AlertCard } from '@/features/alerts/AlertCard';
import { ResolveAlertModal } from '@/features/alerts/ResolveAlertModal';
import type { AlertResponse } from '@/features/alerts/types';

export const Route = createFileRoute('/alerts/')({
  component: AlertQueuePage,
});

type SeverityFilter = 'ALL' | 'OVERDUE' | 'MISSING' | 'OUT OF ORDER';

function AlertQueuePage() {
  const { data: alerts, isLoading, isError } = useAlerts();

  const [selectedAlert, setSelectedAlert] = useState<AlertResponse | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

  // Filter state
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('ALL');
  const [patientNameFilter, setPatientNameFilter] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');

  const hasActiveFilters =
    severityFilter !== 'ALL' || patientNameFilter !== '' || dateFrom !== '' || dateTo !== '';

  function handleResolve(alert: AlertResponse) {
    setSelectedAlert(alert);
    setModalOpen(true);
  }

  function clearFilters() {
    setSeverityFilter('ALL');
    setPatientNameFilter('');
    setDateFrom('');
    setDateTo('');
  }

  // Apply client-side filters
  const filteredAlerts = (alerts ?? []).filter((alert) => {
    if (severityFilter !== 'ALL' && alert.severityLabel !== severityFilter) return false;
    if (
      patientNameFilter.trim() !== '' &&
      !alert.patientName.toLowerCase().includes(patientNameFilter.trim().toLowerCase())
    ) {
      return false;
    }
    if (dateFrom !== '') {
      const alertDate = alert.createdAt.slice(0, 10);
      if (alertDate < dateFrom) return false;
    }
    if (dateTo !== '') {
      const alertDate = alert.createdAt.slice(0, 10);
      if (alertDate > dateTo) return false;
    }
    return true;
  });

  const overdueAlerts = filteredAlerts.filter((a) => a.severityLabel === 'OVERDUE');
  const missingAlerts = filteredAlerts.filter((a) => a.severityLabel === 'MISSING');
  const outOfOrderAlerts = filteredAlerts.filter((a) => a.severityLabel === 'OUT OF ORDER');

  const totalFiltered = filteredAlerts.length;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-semibold tracking-tight">Alerts</h1>
      </div>

      {/* Filter bar */}
      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground font-medium">Alert Type</label>
          <Select
            value={severityFilter}
            onValueChange={(v) => setSeverityFilter(v as SeverityFilter)}
          >
            <SelectTrigger className="w-40">
              <SelectValue placeholder="All Types" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Types</SelectItem>
              <SelectItem value="OVERDUE">Overdue</SelectItem>
              <SelectItem value="MISSING">Missing</SelectItem>
              <SelectItem value="OUT OF ORDER">Out of Order</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground font-medium">Patient Name</label>
          <Input
            type="text"
            placeholder="Filter by patient name..."
            value={patientNameFilter}
            onChange={(e) => setPatientNameFilter(e.target.value)}
            className="w-48"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground font-medium">From</label>
          <Input
            type="date"
            value={dateFrom}
            onChange={(e) => setDateFrom(e.target.value)}
            className="w-36"
          />
        </div>

        <div className="flex flex-col gap-1">
          <label className="text-xs text-muted-foreground font-medium">To</label>
          <Input
            type="date"
            value={dateTo}
            onChange={(e) => setDateTo(e.target.value)}
            className="w-36"
          />
        </div>

        {hasActiveFilters && (
          <Button variant="outline" size="sm" onClick={clearFilters}>
            Clear Filters
          </Button>
        )}
      </div>

      {/* Loading state */}
      {isLoading && (
        <div className="space-y-3">
          {[1, 2, 3].map((n) => (
            <Skeleton key={n} className="h-[100px] w-full rounded-xl" />
          ))}
        </div>
      )}

      {/* Error state */}
      {isError && (
        <p className="text-sm text-muted-foreground">
          Unable to load alerts. Check your network connection and try refreshing.
        </p>
      )}

      {/* Empty state */}
      {!isLoading && !isError && totalFiltered === 0 && (
        <div className="space-y-1">
          <p className="text-sm font-medium">No open alerts.</p>
          <p className="text-sm text-muted-foreground">All patient pathways are on track.</p>
        </div>
      )}

      {/* Alert groups by severity */}
      {!isLoading && !isError && totalFiltered > 0 && (
        <div className="space-y-6">
          {overdueAlerts.length > 0 && (
            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Overdue
              </h2>
              {overdueAlerts.map((alert) => (
                <AlertCard key={alert.id} alert={alert} onResolve={handleResolve} />
              ))}
            </div>
          )}

          {missingAlerts.length > 0 && (
            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Missing
              </h2>
              {missingAlerts.map((alert) => (
                <AlertCard key={alert.id} alert={alert} onResolve={handleResolve} />
              ))}
            </div>
          )}

          {outOfOrderAlerts.length > 0 && (
            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">
                Out of Order
              </h2>
              {outOfOrderAlerts.map((alert) => (
                <AlertCard key={alert.id} alert={alert} onResolve={handleResolve} />
              ))}
            </div>
          )}
        </div>
      )}

      <ResolveAlertModal
        alert={selectedAlert}
        open={modalOpen}
        onOpenChange={setModalOpen}
      />
    </div>
  );
}
