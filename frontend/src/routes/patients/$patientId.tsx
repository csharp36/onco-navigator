import { useState } from 'react';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  Clock,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';

import {
  usePatient,
  usePathwayStatus,
  useCareEvents,
  useDeactivatePatient,
  useUpdateCareEventStatus,
} from '@/features/patients/api';
import type { CareEventResponse, PathwayStepStatus } from '@/features/patients/types';
import { QuickAddCareEventDialog } from '@/features/patients/QuickAddCareEventDialog';
import { hasRole } from '@/lib/auth';

export const Route = createFileRoute('/patients/$patientId')({
  component: PatientDetailPage,
});

// ─── Deactivation reasons ─────────────────────────────────────────────────────

const DEACTIVATION_REASONS = [
  { value: 'Deceased', label: 'Deceased' },
  { value: 'Transferred to Another Practice', label: 'Transferred to Another Practice' },
  { value: 'Withdrawn from Care', label: 'Withdrawn from Care' },
];

// ─── Pathway step icon ────────────────────────────────────────────────────────

function PathwayStepIcon({ step }: { step: PathwayStepStatus }) {
  if (step.hasActiveAlert) {
    return <AlertTriangle className="h-5 w-5 text-red-500 shrink-0" />;
  }
  switch (step.status) {
    case 'COMPLETED':
      return <CheckCircle2 className="h-5 w-5 text-green-600 shrink-0" />;
    case 'OVERDUE':
    case 'MISSING':
      return <Clock className="h-5 w-5 text-amber-500 shrink-0" />;
    case 'UPCOMING':
    default:
      return <Circle className="h-5 w-5 text-muted-foreground shrink-0" />;
  }
}

// ─── Care event status badge ──────────────────────────────────────────────────

function CareEventStatusBadge({ status }: { status: CareEventResponse['status'] }) {
  switch (status) {
    case 'COMPLETED':
      return <Badge variant="secondary">Completed</Badge>;
    case 'SCHEDULED':
      return <Badge variant="outline">Scheduled</Badge>;
    case 'CANCELLED':
      return <Badge variant="destructive">Cancelled</Badge>;
    case 'PENDING':
    default:
      return <Badge variant="outline">Pending</Badge>;
  }
}

// ─── Patient status badge ─────────────────────────────────────────────────────

function PatientStatusBadge({
  status,
}: {
  status: 'On Track' | 'Alert Active' | 'Inactive';
}) {
  if (status === 'On Track') return <Badge variant="secondary">On Track</Badge>;
  if (status === 'Alert Active') return <Badge variant="destructive">Alert Active</Badge>;
  return <Badge variant="outline">Inactive</Badge>;
}

// ─── Main page component ──────────────────────────────────────────────────────

function PatientDetailPage() {
  const { patientId } = Route.useParams();
  const navigate = useNavigate();

  const { data: patient, isLoading: patientLoading, isError: patientError } = usePatient(patientId);
  const { data: pathwayStatus, isLoading: pathwayLoading } = usePathwayStatus(patientId);
  const { data: careEvents, isLoading: careEventsLoading } = useCareEvents(patientId);

  const deactivatePatient = useDeactivatePatient();
  const updateCareEventStatus = useUpdateCareEventStatus(patientId);

  const [recordEventOpen, setRecordEventOpen] = useState(false);
  const [deactivateOpen, setDeactivateOpen] = useState(false);
  const [deactivationReason, setDeactivationReason] = useState('');
  const [deactivationError, setDeactivationError] = useState<string | null>(null);

  // ── Error state ──────────────────────────────────────────────────────────────
  if (patientError) {
    return (
      <div className="p-6">
        <div className="rounded-lg border border-destructive/50 bg-destructive/5 p-4 text-destructive text-sm">
          Unable to load patient details. Check your network connection and try refreshing.
        </div>
      </div>
    );
  }

  // ── Loading state ────────────────────────────────────────────────────────────
  if (patientLoading || !patient) {
    return (
      <div className="space-y-6 p-6">
        <Skeleton className="h-28 w-full" />
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">
          <div className="lg:col-span-3 space-y-3">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-12 w-full" />
            ))}
          </div>
          <div className="lg:col-span-2 space-y-3">
            {Array.from({ length: 4 }).map((_, i) => (
              <Skeleton key={i} className="h-16 w-full" />
            ))}
          </div>
        </div>
      </div>
    );
  }

  const canDeactivate =
    hasRole('ROLE_CARE_COORDINATOR') || hasRole('ROLE_ADMIN');

  function handleDeactivate() {
    if (!deactivationReason) {
      setDeactivationError('Please select a reason for deactivation.');
      return;
    }
    setDeactivationError(null);
    deactivatePatient.mutate(
      { patientId, data: { reason: deactivationReason } },
      {
        onSuccess: () => {
          setDeactivateOpen(false);
          navigate({ to: '/patients' });
        },
        onError: () => {
          setDeactivationError(
            'An error occurred while saving. Your changes were not saved. Please try again.'
          );
        },
      }
    );
  }

  return (
    <div className="space-y-6">
      {/* ── Demographics header ─────────────────────────────────────────────── */}
      <Card className="py-4">
        <CardContent>
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-3 flex-wrap">
                <h1 className="text-xl font-semibold">
                  {patient.firstName} {patient.lastName}
                </h1>
                <PatientStatusBadge status={patient.summaryStatus} />
              </div>
              <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted-foreground">
                <span>MRN: {patient.mrn}</span>
                <span>
                  {patient.cancerType.charAt(0) +
                    patient.cancerType.slice(1).toLowerCase()}{' '}
                  — {patient.cancerStage}
                </span>
                <span>Diagnosed: {new Date(patient.diagnosisDate).toLocaleDateString()}</span>
                {patient.treatingPhysician && (
                  <span>Physician: {patient.treatingPhysician}</span>
                )}
              </div>
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              <Button onClick={() => setRecordEventOpen(true)}>Record Event</Button>
              {canDeactivate && patient.status === 'ACTIVE' && (
                <Button
                  variant="destructive"
                  onClick={() => setDeactivateOpen(true)}
                >
                  Deactivate Patient
                </Button>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      <Separator />

      {/* ── Split layout: Pathway (left 3/5) + Care Events (right 2/5) ─────── */}
      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6">

        {/* ── Left: Pathway visualization ─────────────────────────────────── */}
        <div className="lg:col-span-3">
          <Card>
            <CardHeader>
              <CardTitle className="text-xl font-semibold">Pathway Status</CardTitle>
            </CardHeader>
            <CardContent>
              {pathwayLoading ? (
                <div className="space-y-3">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <Skeleton key={i} className="h-12 w-full" />
                  ))}
                </div>
              ) : !pathwayStatus || pathwayStatus.steps.length === 0 ? (
                <p className="text-muted-foreground text-sm">
                  No pathway template found for this patient.
                </p>
              ) : (
                <ol className="space-y-1">
                  {pathwayStatus.steps.map((step) => (
                    <li
                      key={step.stepId}
                      className={`flex items-start gap-3 rounded-md p-3 min-h-[44px] ${
                        step.hasActiveAlert ? 'bg-amber-50' : ''
                      }`}
                    >
                      <PathwayStepIcon step={step} />
                      <div className="min-w-0">
                        <p className="font-medium text-sm leading-snug">
                          {step.stepNumber}. {step.stepName}
                        </p>
                        <p className="text-xs text-muted-foreground mt-0.5">
                          {step.timingInfo}
                        </p>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>
        </div>

        {/* ── Right: Care events list ──────────────────────────────────────── */}
        <div className="lg:col-span-2">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-xl font-semibold">Care Events</CardTitle>
                <Button size="sm" onClick={() => setRecordEventOpen(true)}>
                  Record Event
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {careEventsLoading ? (
                <div className="space-y-3">
                  {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="h-14 w-full" />
                  ))}
                </div>
              ) : !careEvents || careEvents.length === 0 ? (
                <div className="py-6 text-center">
                  <p className="text-muted-foreground text-sm font-medium">
                    No care events recorded.
                  </p>
                  <p className="text-muted-foreground text-xs mt-1">
                    Use 'Record Event' to log the patient's first care activity.
                  </p>
                </div>
              ) : (
                <ol className="space-y-3">
                  {careEvents.map((event) => (
                    <li
                      key={event.id}
                      className="rounded-md border p-3 text-sm space-y-1"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium capitalize">
                          {event.eventType.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())}
                        </span>
                        <CareEventStatusBadge status={event.status} />
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {new Date(event.eventDate).toLocaleDateString()}
                      </p>
                      {event.notes && (
                        <p className="text-xs text-muted-foreground line-clamp-2">
                          {event.notes}
                        </p>
                      )}
                      {/* Inline status update */}
                      <div className="pt-1">
                        <Select
                          value={event.status}
                          onValueChange={(value) =>
                            updateCareEventStatus.mutate({
                              careEventId: event.id,
                              data: {
                                status: value as CareEventResponse['status'],
                              },
                            })
                          }
                        >
                          <SelectTrigger size="sm" className="h-7 text-xs w-auto">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="SCHEDULED">Scheduled</SelectItem>
                            <SelectItem value="COMPLETED">Completed</SelectItem>
                            <SelectItem value="CANCELLED">Cancelled</SelectItem>
                            <SelectItem value="PENDING">Pending</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* ── Quick-add care event dialog ──────────────────────────────────────── */}
      <QuickAddCareEventDialog
        patientId={patientId}
        open={recordEventOpen}
        onOpenChange={setRecordEventOpen}
      />

      {/* ── Deactivate patient confirmation dialog ───────────────────────────── */}
      <Dialog open={deactivateOpen} onOpenChange={setDeactivateOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Deactivate {patient.firstName} {patient.lastName}?</DialogTitle>
            <DialogDescription>
              This stops all pathway monitoring. This action cannot be undone without admin
              intervention.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-2">
            <Label htmlFor="deactivationReason">Reason</Label>
            <Select
              value={deactivationReason}
              onValueChange={(value) => {
                setDeactivationReason(value);
                setDeactivationError(null);
              }}
            >
              <SelectTrigger
                id="deactivationReason"
                className="w-full"
                aria-invalid={!!deactivationError && !deactivationReason}
              >
                <SelectValue placeholder="Select a reason" />
              </SelectTrigger>
              <SelectContent>
                {DEACTIVATION_REASONS.map((r) => (
                  <SelectItem key={r.value} value={r.value}>
                    {r.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {deactivationError && (
              <p className="text-destructive text-xs">{deactivationError}</p>
            )}
          </div>

          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setDeactivateOpen(false);
                setDeactivationReason('');
                setDeactivationError(null);
              }}
              disabled={deactivatePatient.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={handleDeactivate}
              disabled={deactivatePatient.isPending}
            >
              {deactivatePatient.isPending ? 'Deactivating...' : 'Yes, Deactivate'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
