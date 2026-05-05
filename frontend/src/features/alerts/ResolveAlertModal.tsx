import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { useResolveAlert } from './api';
import type { AlertResponse } from './types';

const resolveSchema = z.object({
  notes: z.string().min(10, { error: 'Describe the action taken (minimum 10 characters).' }),
});

type ResolveFormValues = z.infer<typeof resolveSchema>;

interface ResolveAlertModalProps {
  alert: AlertResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

function getSeverityBadgeVariant(
  severityLabel: AlertResponse['severityLabel'],
): 'destructive' | 'default' | 'secondary' | 'outline' {
  switch (severityLabel) {
    case 'OVERDUE':
    case 'CANCELLED':
      return 'destructive';
    case 'MISSING':
    case 'RESULTS PENDING':
    case 'DEADLINE':
      return 'default';
    default:
      return 'secondary';
  }
}

export function ResolveAlertModal({ alert, open, onOpenChange }: ResolveAlertModalProps) {
  const resolveAlert = useResolveAlert();

  const {
    register,
    handleSubmit,
    formState: { errors, isValid },
    reset,
    watch,
  } = useForm<ResolveFormValues>({
    resolver: zodResolver(resolveSchema),
    mode: 'onChange',
  });

  const notesValue = watch('notes', '');

  // Reset form when dialog closes
  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open, reset]);

  function onSubmit(data: ResolveFormValues) {
    if (!alert) return;
    resolveAlert.mutate(
      { alertId: alert.id, notes: data.notes },
      {
        onSuccess: () => {
          // Close dialog first per Pitfall 8 — close in onSuccess, not in useEffect
          onOpenChange(false);
          reset();
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="max-w-lg"
        showCloseButton={false}
        onInteractOutside={(e) => e.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>Resolve Alert</DialogTitle>
        </DialogHeader>

        {alert && (
          <div className="space-y-4">
            {/* Alert details summary — read-only */}
            <div className="rounded-md border bg-muted/40 p-4 space-y-2">
              <div className="flex items-center gap-2">
                <Badge variant={getSeverityBadgeVariant(alert.severityLabel)}>
                  {alert.severityLabel}
                </Badge>
                <span className="font-medium text-sm">{alert.patientName}</span>
                <span className="text-sm text-muted-foreground">MRN: {alert.patientMrn}</span>
              </div>
              <p className="text-sm font-semibold">{alert.pathwayStepName}</p>
              <p className="text-sm text-muted-foreground">{alert.deviationDescription}</p>
            </div>

            {/* Resolution notes */}
            <form onSubmit={handleSubmit(onSubmit)} id="resolve-form" className="space-y-2">
              <Label htmlFor="resolution-notes">
                Resolution Notes <span className="text-destructive">*</span>
              </Label>
              <Textarea
                id="resolution-notes"
                rows={3}
                placeholder="Describe the action taken..."
                aria-invalid={!!errors.notes}
                {...register('notes')}
              />
              {errors.notes && (
                <p className="text-xs text-destructive">{errors.notes.message}</p>
              )}
            </form>
          </div>
        )}

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={resolveAlert.isPending}
          >
            Keep Alert Open
          </Button>
          <Button
            type="submit"
            form="resolve-form"
            disabled={!isValid || (notesValue?.length ?? 0) < 10 || resolveAlert.isPending}
          >
            {resolveAlert.isPending ? 'Resolving...' : 'Resolve Alert'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
