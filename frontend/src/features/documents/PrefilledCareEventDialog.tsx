import { useState } from 'react';
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
import { Badge } from '@/components/ui/badge';
import { useCreateCareEvent } from '@/features/patients/api';
import { DocumentPreviewPanel } from './DocumentPreviewPanel';
import type { DocumentPrefillData } from './types';

// ─── Zod v4 schema (same as QuickAddCareEventDialog) ────────────────────────

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

// ─── Care event type options ─────────────────────────────────────────────────

const CARE_EVENT_TYPES = [
  { value: 'REFERRAL', label: 'Referral' },
  { value: 'CONSULTATION', label: 'Consultation' },
  { value: 'BIOPSY', label: 'Biopsy' },
  { value: 'PATHOLOGY_REPORT', label: 'Pathology Report' },
  { value: 'IMAGING', label: 'Imaging' },
  { value: 'SURGERY', label: 'Surgery' },
  { value: 'CHEMOTHERAPY', label: 'Chemotherapy' },
  { value: 'RADIATION', label: 'Radiation' },
  { value: 'LAB_WORK', label: 'Lab Work' },
  { value: 'GENETIC_TESTING', label: 'Genetic Testing' },
  { value: 'FOLLOW_UP', label: 'Follow-up' },
  { value: 'OTHER', label: 'Other' },
];

/** Map document classification type to care event type */
function mapDocumentTypeToEventType(documentType: string | null | undefined): string {
  switch (documentType) {
    case 'PATHOLOGY_REPORT': return 'PATHOLOGY_REPORT';
    case 'RADIOLOGY_REPORT': return 'IMAGING';
    case 'OPERATIVE_NOTE': return 'SURGERY';
    case 'LAB_RESULT': return 'LAB_WORK';
    case 'REFERRAL_LETTER': return 'REFERRAL';
    default: return '';
  }
}

const EVENT_STATUSES = [
  { value: 'SCHEDULED', label: 'Scheduled' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'PENDING', label: 'Pending' },
];

// ─── Props ───────────────────────────────────────────────────────────────────

interface PrefilledCareEventDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  patientId: string;
  prefillData: DocumentPrefillData;
}

// ─── Component ───────────────────────────────────────────────────────────────

export function PrefilledCareEventDialog({
  open,
  onOpenChange,
  patientId,
  prefillData,
}: PrefilledCareEventDialogProps) {
  const createCareEvent = useCreateCareEvent(patientId);

  const [previewOpen, setPreviewOpen] = useState(false);
  const [modifiedFields, setModifiedFields] = useState<Set<string>>(new Set());

  // Derive event type: use classification's eventType if valid, else map from documentType
  const derivedEventType = prefillData.classification.eventType
    && CARE_EVENT_TYPES.some(t => t.value === prefillData.classification.eventType)
    ? prefillData.classification.eventType
    : mapDocumentTypeToEventType(prefillData.classification.documentType);

  const form = useForm<CareEventFormValues>({
    resolver: zodResolver(careEventSchema),
    defaultValues: {
      eventType: derivedEventType,
      eventDate: prefillData.classification.eventDate ?? '',
      status: 'COMPLETED',
      notes: prefillData.classification.extractedNotes ?? '',
      expectedCompletionDate: '',
      schedulingConfirmed: false,
      externalFacilityName: '',
    },
  });

  function markFieldModified(fieldName: string) {
    setModifiedFields((prev) => new Set(prev).add(fieldName));
  }

  function getFieldClassName(fieldName: string): string {
    return modifiedFields.has(fieldName) ? '' : 'bg-muted/30';
  }

  function handleSubmit(values: CareEventFormValues) {
    const isScheduledOrPending = values.status === 'SCHEDULED' || values.status === 'PENDING';
    createCareEvent.mutate(
      {
        eventType: values.eventType,
        eventDate: values.eventDate,
        status: values.status as 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING',
        notes: values.notes || undefined,
        documentId: prefillData.documentId,
        // Phase 7: pass scheduling fields conditionally
        expectedCompletionDate: isScheduledOrPending ? (values.expectedCompletionDate || undefined) : undefined,
        schedulingConfirmed: isScheduledOrPending ? values.schedulingConfirmed : undefined,
        externalFacilityName: values.externalFacilityName || undefined,
      },
      {
        onSuccess: () => {
          form.reset();
          setModifiedFields(new Set());
          onOpenChange(false);
        },
      },
    );
  }

  function handleDiscard() {
    form.reset();
    setModifiedFields(new Set());
    onOpenChange(false);
  }

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Record Care Event from Document</DialogTitle>
          </DialogHeader>

          {/* Source Document section -- read-only */}
          <div className="rounded-md border bg-muted/40 p-4 space-y-2">
            <p className="text-sm font-medium">Source Document</p>
            <div className="flex items-center gap-2">
              <Badge variant="secondary">
                {prefillData.classification.documentType}
              </Badge>
              <span className="text-xs text-muted-foreground">
                {prefillData.classification.confidence} confidence
              </span>
            </div>
            {prefillData.documentId && (
              <Button
                variant="link"
                size="sm"
                className="p-0 h-auto"
                onClick={() => {
                  // Close dialog first — Radix Dialog focus trap blocks Sheet interaction
                  onOpenChange(false);
                  // Small delay to let dialog unmount before opening sheet
                  setTimeout(() => setPreviewOpen(true), 150);
                }}
              >
                Preview full document
              </Button>
            )}
          </div>

          <form onSubmit={form.handleSubmit(handleSubmit)} noValidate>
            <div className="space-y-4">
              {/* Event Type */}
              <div className="grid gap-2">
                <Label htmlFor="prefill-eventType">Event Type</Label>
                <Select
                  onValueChange={(value) => {
                    form.setValue('eventType', value, { shouldValidate: true });
                    markFieldModified('eventType');
                  }}
                  defaultValue={form.getValues('eventType')}
                >
                  <SelectTrigger
                    id="prefill-eventType"
                    className={`w-full ${getFieldClassName('eventType')}`}
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
                <Label htmlFor="prefill-eventDate">Event Date</Label>
                <Input
                  id="prefill-eventDate"
                  type="date"
                  className={getFieldClassName('eventDate')}
                  {...form.register('eventDate', {
                    onChange: () => markFieldModified('eventDate'),
                  })}
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
                <Label htmlFor="prefill-eventStatus">Status</Label>
                <Select
                  onValueChange={(value) => {
                    form.setValue('status', value, { shouldValidate: true });
                    markFieldModified('status');
                  }}
                  defaultValue={form.getValues('status')}
                >
                  <SelectTrigger
                    id="prefill-eventStatus"
                    className={`w-full ${getFieldClassName('status')}`}
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
                <Label htmlFor="prefill-notes">
                  Notes{' '}
                  <span className="text-muted-foreground font-normal">(optional)</span>
                </Label>
                <Textarea
                  id="prefill-notes"
                  rows={2}
                  className={getFieldClassName('notes')}
                  placeholder="Optional notes about this event"
                  {...form.register('notes', {
                    onChange: () => markFieldModified('notes'),
                  })}
                />
              </div>

              {/* Phase 7: Scheduling fields -- shown for SCHEDULED or PENDING only */}
              {(form.watch('status') === 'SCHEDULED' || form.watch('status') === 'PENDING') && (
                <>
                  <div className="grid gap-2">
                    <Label htmlFor="prefill-expectedCompletionDate">
                      Expected Completion Date{' '}
                      <span className="text-muted-foreground font-normal">(optional)</span>
                    </Label>
                    <Input
                      id="prefill-expectedCompletionDate"
                      type="date"
                      {...form.register('expectedCompletionDate', {
                        onChange: () => markFieldModified('expectedCompletionDate'),
                      })}
                    />
                  </div>
                  <div className="flex items-center gap-2">
                    <input
                      id="prefill-schedulingConfirmed"
                      type="checkbox"
                      {...form.register('schedulingConfirmed', {
                        onChange: () => markFieldModified('schedulingConfirmed'),
                      })}
                      className="h-4 w-4 rounded border-border"
                    />
                    <Label htmlFor="prefill-schedulingConfirmed">
                      Scheduling confirmed with external facility
                    </Label>
                  </div>
                </>
              )}

              {/* External facility name -- always visible (optional) */}
              <div className="grid gap-2">
                <Label htmlFor="prefill-externalFacilityName">
                  External Facility{' '}
                  <span className="text-muted-foreground font-normal">(optional)</span>
                </Label>
                <Input
                  id="prefill-externalFacilityName"
                  type="text"
                  placeholder="e.g., Memorial Hospital Radiology"
                  {...form.register('externalFacilityName', {
                    onChange: () => markFieldModified('externalFacilityName'),
                  })}
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

      {prefillData.documentId && (
        <DocumentPreviewPanel
          open={previewOpen}
          onOpenChange={(isOpen) => {
            setPreviewOpen(isOpen);
            // Re-open the care event dialog when preview closes
            if (!isOpen) onOpenChange(true);
          }}
          documentId={prefillData.documentId}
          filename="document.pdf"
        />
      )}
    </>
  );
}
