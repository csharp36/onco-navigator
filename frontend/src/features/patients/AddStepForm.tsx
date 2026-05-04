import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

// ─── Zod v4 schema ────────────────────────────────────────────────────────────

const addStepSchema = z.object({
  name: z.string().min(1, { error: 'Step name is required.' }),
  eventType: z.string().optional(),
  windowDays: z.coerce.number().int().positive().optional().or(z.literal('')).transform(v => v === '' ? undefined : v),
  required: z.boolean().default(true),
});

type AddStepFormValues = z.infer<typeof addStepSchema>;

// ─── Event type options ───────────────────────────────────────────────────────

const EVENT_TYPES = [
  { value: 'CONSULTATION', label: 'Consultation' },
  { value: 'IMAGING', label: 'Imaging' },
  { value: 'SURGERY', label: 'Surgery' },
  { value: 'PATHOLOGY', label: 'Pathology' },
  { value: 'LAB_WORK', label: 'Lab Work' },
  { value: 'RADIATION', label: 'Radiation' },
  { value: 'CHEMOTHERAPY', label: 'Chemotherapy' },
  { value: 'FOLLOW_UP', label: 'Follow-up' },
];

// ─── Props ────────────────────────────────────────────────────────────────────

interface AddStepFormProps {
  onSubmit: (data: { name: string; eventType?: string; windowDays?: number; required: boolean }) => void;
  onCancel: () => void;
  isPending: boolean;
  error: string | null;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function AddStepForm({ onSubmit, onCancel, isPending, error }: AddStepFormProps) {
  const form = useForm<AddStepFormValues>({
    resolver: zodResolver(addStepSchema),
    defaultValues: {
      name: '',
      eventType: '',
      windowDays: undefined,
      required: true,
    },
  });

  function handleSubmit(values: AddStepFormValues) {
    onSubmit({
      name: values.name,
      eventType: values.eventType || undefined,
      windowDays: values.windowDays as number | undefined,
      required: values.required,
    });
  }

  return (
    <div className="rounded-md border p-4 space-y-4 bg-muted/30">
      {/* Step Name */}
      <div className="grid gap-2">
        <Label htmlFor="stepName">Step Name</Label>
        <Input
          id="stepName"
          placeholder="Enter step name"
          {...form.register('name')}
          aria-invalid={!!form.formState.errors.name}
          disabled={isPending}
        />
        {form.formState.errors.name && (
          <p className="text-destructive text-xs">
            {form.formState.errors.name.message}
          </p>
        )}
      </div>

      {/* Expected Event Type */}
      <div className="grid gap-2">
        <Label htmlFor="eventType">
          Expected Event Type{' '}
          <span className="text-muted-foreground font-normal">(optional)</span>
        </Label>
        <Select
          onValueChange={(value) =>
            form.setValue('eventType', value, { shouldValidate: true })
          }
          disabled={isPending}
        >
          <SelectTrigger id="eventType" className="w-full">
            <SelectValue placeholder="Select event type" />
          </SelectTrigger>
          <SelectContent>
            {EVENT_TYPES.map((type) => (
              <SelectItem key={type.value} value={type.value}>
                {type.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Time Window (days) */}
      <div className="grid gap-2">
        <Label htmlFor="windowDays">
          Time Window (days){' '}
          <span className="text-muted-foreground font-normal">(optional)</span>
        </Label>
        <Input
          id="windowDays"
          type="number"
          min={1}
          placeholder="e.g., 14"
          {...form.register('windowDays')}
          aria-invalid={!!form.formState.errors.windowDays}
          disabled={isPending}
        />
        {form.formState.errors.windowDays && (
          <p className="text-destructive text-xs">
            {form.formState.errors.windowDays.message}
          </p>
        )}
      </div>

      {error && (
        <p className="text-destructive text-sm">
          An error occurred while saving. Your changes were not saved. Please
          try again.
        </p>
      )}

      {/* Actions */}
      <div className="flex gap-2">
        <Button
          type="button"
          size="sm"
          onClick={form.handleSubmit(handleSubmit)}
          disabled={isPending}
        >
          {isPending ? 'Adding...' : 'Add Step'}
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={onCancel}
          disabled={isPending}
        >
          Discard
        </Button>
      </div>
    </div>
  );
}
