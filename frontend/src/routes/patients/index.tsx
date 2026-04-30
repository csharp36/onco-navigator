import { useState } from 'react';
import { createFileRoute, Link } from '@tanstack/react-router';
import { Search } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { usePatients } from '@/features/patients/api';
import type { PatientResponse } from '@/features/patients/types';
import { QuickAddCareEventDialog } from '@/features/patients/QuickAddCareEventDialog';

export const Route = createFileRoute('/patients/')({
  component: PatientListPage,
});

// ─── Status badge ─────────────────────────────────────────────────────────────

function StatusBadge({ status }: { status: PatientResponse['summaryStatus'] }) {
  if (status === 'On Track') {
    return <Badge variant="secondary">On Track</Badge>;
  }
  if (status === 'Alert Active') {
    return <Badge variant="destructive">Alert Active</Badge>;
  }
  // Inactive
  return <Badge variant="outline">Inactive</Badge>;
}

// ─── Page ─────────────────────────────────────────────────────────────────────

function PatientListPage() {
  const [searchInput, setSearchInput] = useState('');
  const [activeMrn, setActiveMrn] = useState<string | undefined>(undefined);
  const [quickAddPatientId, setQuickAddPatientId] = useState<string | null>(null);

  const { data: patients, isLoading, isError } = usePatients(activeMrn);

  function handleSearch(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setActiveMrn(searchInput.trim() || undefined);
  }

  function handleSearchClear() {
    setSearchInput('');
    setActiveMrn(undefined);
  }

  return (
    <div className="space-y-6">
      {/* Page header */}
      <div className="flex items-center justify-between">
        <h1 className="text-3xl font-semibold tracking-tight">Patients</h1>
        <Button asChild>
          <Link to="/patients/new">Add Patient</Link>
        </Button>
      </div>

      {/* MRN search */}
      <form onSubmit={handleSearch} className="flex items-center gap-2 max-w-sm">
        <div className="relative flex-1">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground pointer-events-none" />
          <Input
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            placeholder="Search by MRN"
            className="pl-8"
          />
        </div>
        <Button type="submit" variant="outline" size="sm">
          Search
        </Button>
        {activeMrn && (
          <Button type="button" variant="ghost" size="sm" onClick={handleSearchClear}>
            Clear
          </Button>
        )}
      </form>

      {/* Loading state */}
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-12 w-full" />
          ))}
        </div>
      )}

      {/* Error state */}
      {isError && (
        <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-4 text-destructive text-sm">
          Unable to load patients. Check your network connection and try refreshing.
        </div>
      )}

      {/* Patient table */}
      {!isLoading && !isError && patients !== undefined && (
        <>
          {patients.length === 0 ? (
            <div className="rounded-lg border border-dashed p-12 text-center">
              <p className="text-muted-foreground font-medium">
                {activeMrn ? 'No patients found for that MRN.' : 'No patients enrolled yet.'}
              </p>
              {!activeMrn && (
                <p className="text-muted-foreground text-sm mt-1">
                  Add your first patient to start monitoring their care pathway.
                </p>
              )}
            </div>
          ) : (
            <div className="rounded-lg border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Name</TableHead>
                    <TableHead>MRN</TableHead>
                    <TableHead>Cancer Type</TableHead>
                    <TableHead>Stage</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Enrolled</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {patients.map((patient) => (
                    <TableRow key={patient.id}>
                      <TableCell className="font-medium">
                        <Link
                          to="/patients/$patientId"
                          params={{ patientId: patient.id }}
                          className="text-primary underline-offset-4 hover:underline"
                        >
                          {patient.firstName} {patient.lastName}
                        </Link>
                      </TableCell>
                      <TableCell className="text-muted-foreground">{patient.mrn}</TableCell>
                      <TableCell className="capitalize">
                        {patient.cancerType.charAt(0) + patient.cancerType.slice(1).toLowerCase()}
                      </TableCell>
                      <TableCell>{patient.cancerStage}</TableCell>
                      <TableCell>
                        <StatusBadge status={patient.summaryStatus} />
                      </TableCell>
                      <TableCell className="text-muted-foreground text-sm">
                        {new Date(patient.createdAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => setQuickAddPatientId(patient.id)}
                        >
                          Record Event
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </>
      )}

      {/* Quick-add care event dialog */}
      {quickAddPatientId && (
        <QuickAddCareEventDialog
          patientId={quickAddPatientId}
          open={!!quickAddPatientId}
          onOpenChange={(open) => {
            if (!open) setQuickAddPatientId(null);
          }}
        />
      )}
    </div>
  );
}
