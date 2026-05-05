import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { useCreateCareEvent } from '@/features/patients/api';

// ─── Zod v4 schema ────────────────────────────────────────────────────────────

const careEventSchema = z.object({
  eventType: z.string().min(1, { error: 'Event type is required.' }),
  eventDate: z.string().min(1, { error: 'Event date is required.' }),
  status: z.string().min(1, { error: 'Status is required.' }),
  notes: z.string().optional(),
  // Phase 7: scheduling coordination fields (D-07, D-10, D-13)
  expectedCompletionDate: z.string().optional(),
  schedulingConfirmed: z.boolean().default(false),
  externalFacilityName: z.string().optional(),
});

type CareEventFormValues = z.infer<typeof careEventSchema>;

// ─── Care event type options ──────────────────────────────────────────────────

const CARE_EVENT_TYPES = [
  { value: 'REFERRAL', label: 'Referral' },
  { value: 'CONSULTATION', label: 'Consultation' },
  { value: 'BIOPSY', label: 'Biopsy' },
  { value: 'IMAGING', label: 'Imaging' },
  { value: 'SURGERY', label: 'Surgery' },
  { value: 'CHEMOTHERAPY', label: 'Chemotherapy' },
  { value: 'RADIATION', label: 'Radiation' },
  { value: 'PATHOLOGY', label: 'Pathology' },
  { value: 'FOLLOW_UP', label: 'Follow-up' },
  { value: 'OTHER', label: 'Other' },
];

const EVENT_STATUSES = [
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'PENDING', label: 'Pending' },
];

// ─── Props ────────────────────────────────────────────────────────────────────

interface QuickAddCareEventDialogProps {
  patientId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function QuickAddCareEventDialog({
  patientId,
  open,
  onOpenChange,
}: QuickAddCareEventDialogProps) {
  const createCareEvent = useCreateCareEvent(patientId);

  const form = useForm<CareEventFormValues>({
    resolver: zodResolver(careEventSchema),
    defaultValues: {
      eventType: '',
      eventDate: '',
      status: '',
      notes: '',
      expectedCompletionDate: '',
      schedulingConfirmed: false,
      externalFacilityName: '',
    },
  });

  function handleSubmit(values: CareEventFormValues) {
    const isScheduledOrPending = values.status === 'SCHEDULED' || values.status === 'PENDING';
    createCareEvent.mutate(
      {
        eventType: values.eventType,
        eventDate: values.eventDate,
        status: values.status as 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING',
        notes: values.notes || undefined,
        // Phase 7: pass scheduling fields conditionally
        expectedCompletionDate: isScheduledOrPending ? (values.expectedCompletionDate || undefined) : undefined,
        schedulingConfirmed: isScheduledOrPending ? values.schedulingConfirmed : undefined,
        externalFacilityName: values.externalFacilityName || undefined,
      },
      {
        onSuccess: () => {
          form.reset();
          onOpenChange(false);
        },
      }
    );
  }

  function handleDiscard() {
    form.reset();
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Record Care Event</DialogTitle>
        </DialogHeader>

        <form onSubmit={form.handleSubmit(handleSubmit)} noValidate>
          <div className="space-y-4">
            {/* Event Type */}
            <div className="grid gap-2">
              <Label htmlFor="eventType">Event Type</Label>
              <Select
                onValueChange={(value) =>
                  form.setValue('eventType', value, { shouldValidate: true })
                }
                defaultValue={form.getValues('eventType')}
              >
                <SelectTrigger
                  id="eventType"
                  className="w-full"
                  aria-invalid={!!form.formState.errors.eventType}
                >
                  <SelectValue placeholder="Select event type" />
                </SelectTrigger>
                <SelectContent>
                  {CARE_EVENT_TYPES.map((type) => (
                    <SelectItem key={type.value} value={type.value}>
                      {type.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.eventType && (
                <p className="text-destructive text-xs">
                  {form.formState.errors.eventType.message}
                </p>
              )}
            </div>

            {/* Event Date */}
            <div className="grid gap-2">
              <Label htmlFor="eventDate">Event Date</Label>
              <Input
                id="eventDate"
                type="date"
                {...form.register('eventDate')}
                aria-invalid={!!form.formState.errors.eventDate}
              />
              {form.formState.errors.eventDate && (
                <p className="text-destructive text-xs">
                  {form.formState.errors.eventDate.message}
                </p>
              )}
            </div>

            {/* Status */}
            <div className="grid gap-2">
              <Label htmlFor="eventStatus">Status</Label>
              <Select
                onValueChange={(value) =>
                  form.setValue('status', value, { shouldValidate: true })
                }
                defaultValue={form.getValues('status')}
              >
                <SelectTrigger
                  id="eventStatus"
                  className="w-full"
                  aria-invalid={!!form.formState.errors.status}
                >
                  <SelectValue placeholder="Select status" />
                </SelectTrigger>
                <SelectContent>
                  {EVENT_STATUSES.map((s) => (
                    <SelectItem key={s.value} value={s.value}>
                      {s.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.formState.errors.status && (
                <p className="text-destructive text-xs">
                  {form.formState.errors.status.message}
                </p>
              )}
            </div>

            {/* Notes (optional) */}
            <div className="grid gap-2">
              <Label htmlFor="notes">
                Notes{' '}
                <span className="text-muted-foreground font-normal">(optional)</span>
              </Label>
              <Textarea
                id="notes"
                rows={2}
                placeholder="Optional notes about this event"
                {...form.register('notes')}
              />
            </div>

            {/* Phase 7: Scheduling fields -- shown for SCHEDULED or PENDING only */}
            {(form.watch('status') === 'SCHEDULED' || form.watch('status') === 'PENDING') && (
              <>
                <div className="grid gap-2">
                  <Label htmlFor="expectedCompletionDate">
                    Expected Completion Date{' '}
                    <span className="text-muted-foreground font-normal">(optional)</span>
                  </Label>
                  <Input
                    id="expectedCompletionDate"
                    type="date"
                    {...form.register('expectedCompletionDate')}
                  />
                </div>
                <div className="flex items-center gap-2">
                  <input
                    id="schedulingConfirmed"
                    type="checkbox"
                    {...form.register('schedulingConfirmed')}
                    className="h-4 w-4 rounded border-border"
                  />
                  <Label htmlFor="schedulingConfirmed">
                    Scheduling confirmed with external facility
                  </Label>
                </div>
              </>
            )}

            {/* External facility name -- always visible (optional) */}
            <div className="grid gap-2">
              <Label htmlFor="externalFacilityName">
                External Facility{' '}
                <span className="text-muted-foreground font-normal">(optional)</span>
              </Label>
              <Input
                id="externalFacilityName"
                type="text"
                placeholder="e.g., Memorial Hospital Radiology"
                {...form.register('externalFacilityName')}
              />
            </div>

            {createCareEvent.isError && (
              <p className="text-destructive text-sm">
                An error occurred while saving. Your changes were not saved. Please try again.
              </p>
            )}
          </div>

          <DialogFooter className="mt-6">
            <Button
              type="button"
              variant="outline"
              onClick={handleDiscard}
              disabled={createCareEvent.isPending}
            >
              Discard Event
            </Button>
            <Button type="submit" disabled={createCareEvent.isPending}>
              {createCareEvent.isPending ? 'Saving...' : 'Save Event'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
