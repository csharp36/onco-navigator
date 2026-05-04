import { useState } from 'react';
import { Plus } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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

import { StepRow } from './StepRow';
import { AddStepForm } from './AddStepForm';
import { EdgeEditor } from './EdgeEditor';
import { SkipStepDialog } from './SkipStepDialog';
import {
  usePathwayEdges,
  useCreateStep,
  useUpdateStep,
  useDeleteStep,
  useSkipStep,
  useUnskipStep,
  useCreateEdge,
  useDeleteEdge,
} from './api';
import type { PathwayStepStatus } from './types';

// ─── Event type options for inline step editing ───────────────────────────────

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

// ─── Inline step edit form ────────────────────────────────────────────────────

interface InlineStepEditProps {
  step: PathwayStepStatus;
  onSave: (data: { name: string; eventType?: string; windowDays?: number }) => void;
  onDiscard: () => void;
  isPending: boolean;
}

function InlineStepEdit({ step, onSave, onDiscard, isPending }: InlineStepEditProps) {
  const [name, setName] = useState(step.stepName);
  const [eventType, setEventType] = useState('');
  const [windowDays, setWindowDays] = useState('');
  const [nameError, setNameError] = useState('');

  function handleSave() {
    if (!name.trim()) {
      setNameError('Step name is required.');
      return;
    }
    setNameError('');
    onSave({
      name: name.trim(),
      eventType: eventType || undefined,
      windowDays: windowDays ? parseInt(windowDays, 10) : undefined,
    });
  }

  return (
    <li className="rounded-md border p-3 space-y-3 min-h-[44px]">
      <div className="grid gap-1.5">
        <Label htmlFor={`editStepName-${step.stepId}`}>Step Name</Label>
        <Input
          id={`editStepName-${step.stepId}`}
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={isPending}
          autoFocus
          aria-invalid={!!nameError}
        />
        {nameError && (
          <p className="text-destructive text-xs">{nameError}</p>
        )}
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor={`editEventType-${step.stepId}`}>
          Expected Event Type{' '}
          <span className="text-muted-foreground font-normal">(optional)</span>
        </Label>
        <Select value={eventType} onValueChange={setEventType} disabled={isPending}>
          <SelectTrigger id={`editEventType-${step.stepId}`} className="w-full">
            <SelectValue placeholder="Select event type" />
          </SelectTrigger>
          <SelectContent>
            {EVENT_TYPES.map((t) => (
              <SelectItem key={t.value} value={t.value}>
                {t.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="grid gap-1.5">
        <Label htmlFor={`editWindowDays-${step.stepId}`}>
          Time Window (days){' '}
          <span className="text-muted-foreground font-normal">(optional)</span>
        </Label>
        <Input
          id={`editWindowDays-${step.stepId}`}
          type="number"
          min={1}
          value={windowDays}
          onChange={(e) => setWindowDays(e.target.value)}
          disabled={isPending}
          placeholder="e.g., 14"
        />
      </div>

      <div className="flex gap-2">
        <Button type="button" size="sm" onClick={handleSave} disabled={isPending}>
          {isPending ? 'Saving...' : 'Save Step'}
        </Button>
        <Button type="button" variant="ghost" size="sm" onClick={onDiscard} disabled={isPending}>
          Discard
        </Button>
      </div>
    </li>
  );
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface PathwayEditorProps {
  patientId: string;
  steps: PathwayStepStatus[];
}

// ─── Main PathwayEditor component ─────────────────────────────────────────────

export function PathwayEditor({ patientId, steps }: PathwayEditorProps) {
  const [showAddForm, setShowAddForm] = useState(false);
  const [editingStepId, setEditingStepId] = useState<string | null>(null);
  const [skipDialogStep, setSkipDialogStep] = useState<{ id: string; name: string } | null>(null);
  const [removeDialogStep, setRemoveDialogStep] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const [addEdgeError, setAddEdgeError] = useState<string | null>(null);

  // Mutations
  const createStep = useCreateStep(patientId);
  const updateStep = useUpdateStep(patientId);
  const deleteStep = useDeleteStep(patientId);
  const skipStep = useSkipStep(patientId);
  const unskipStep = useUnskipStep(patientId);
  const createEdge = useCreateEdge(patientId);
  const deleteEdge = useDeleteEdge(patientId);

  // Edges query
  const { data: edges = [] } = usePathwayEdges(patientId);

  function handleAddStep(data: { name: string; eventType?: string; windowDays?: number; required: boolean }) {
    createStep.mutate(
      {
        name: data.name,
        eventType: data.eventType,
        windowDays: data.windowDays,
        required: data.required,
      },
      {
        onSuccess: () => {
          setShowAddForm(false);
        },
      }
    );
  }

  function handleUpdateStep(
    stepId: string,
    data: { name: string; eventType?: string; windowDays?: number }
  ) {
    // Look up the original step to get required field
    const original = steps.find((s) => s.stepId === stepId);
    updateStep.mutate(
      {
        stepId,
        data: {
          name: data.name,
          eventType: data.eventType,
          windowDays: data.windowDays,
          required: original?.prerequisiteStepIds !== undefined ? true : true,
        },
      },
      {
        onSuccess: () => {
          setEditingStepId(null);
        },
      }
    );
  }

  function handleDeleteStep(stepId: string) {
    deleteStep.mutate(stepId, {
      onSuccess: () => {
        setRemoveDialogStep(null);
      },
    });
  }

  function handleSkipStep(stepId: string, reason: string) {
    skipStep.mutate(
      { stepId, reason },
      {
        onSuccess: () => {
          setSkipDialogStep(null);
        },
      }
    );
  }

  function handleUnskipStep(stepId: string) {
    unskipStep.mutate(stepId);
  }

  function handleAddEdge(sourceStepId: string, targetStepId: string) {
    setAddEdgeError(null);
    createEdge.mutate(
      { sourceStepId, targetStepId },
      {
        onSuccess: () => {
          setAddEdgeError(null);
        },
        onError: () => {
          setAddEdgeError('cycle');
        },
      }
    );
  }

  function handleRemoveEdge(edgeId: string) {
    deleteEdge.mutate(edgeId);
  }

  return (
    <div className="space-y-2">
      <ol className="space-y-1">
        {steps.map((step) => {
          if (editingStepId === step.stepId && step.status === 'ACTIVE') {
            return (
              <InlineStepEdit
                key={step.stepId}
                step={step}
                onSave={(data) => handleUpdateStep(step.stepId, data)}
                onDiscard={() => setEditingStepId(null)}
                isPending={updateStep.isPending}
              />
            );
          }

          return (
            <StepRow
              key={step.stepId}
              step={step}
              isEditing={true}
              onEdit={() => setEditingStepId(step.stepId)}
              onRemove={() => setRemoveDialogStep({ id: step.stepId, name: step.stepName })}
              onSkip={() => setSkipDialogStep({ id: step.stepId, name: step.stepName })}
              onUnskip={() => handleUnskipStep(step.stepId)}
            />
          );
        })}
      </ol>

      {/* Add step button / inline form */}
      {showAddForm ? (
        <AddStepForm
          onSubmit={handleAddStep}
          onCancel={() => setShowAddForm(false)}
          isPending={createStep.isPending}
          error={createStep.isError ? 'error' : null}
        />
      ) : (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-2"
          onClick={() => setShowAddForm(true)}
        >
          <Plus className="h-4 w-4 mr-1" />
          Add Step
        </Button>
      )}

      {/* Edge editor */}
      <EdgeEditor
        steps={steps}
        edges={edges}
        onAddEdge={handleAddEdge}
        onRemoveEdge={handleRemoveEdge}
        addEdgePending={createEdge.isPending}
        addEdgeError={addEdgeError}
      />

      {/* Skip step dialog */}
      {skipDialogStep && (
        <SkipStepDialog
          open={!!skipDialogStep}
          onOpenChange={(open) => { if (!open) setSkipDialogStep(null); }}
          stepName={skipDialogStep.name}
          onConfirm={(reason) => handleSkipStep(skipDialogStep.id, reason)}
          isPending={skipStep.isPending}
        />
      )}

      {/* Remove step confirmation dialog */}
      <Dialog
        open={!!removeDialogStep}
        onOpenChange={(open) => { if (!open) setRemoveDialogStep(null); }}
      >
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>
              Remove &ldquo;{removeDialogStep?.name}&rdquo;?
            </DialogTitle>
            <DialogDescription>
              Downstream steps that depended on this step will become
              immediately ready. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          {deleteStep.isError && (
            <p className="text-destructive text-sm">
              An error occurred while saving. Your changes were not saved.
              Please try again.
            </p>
          )}
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRemoveDialogStep(null)}
              disabled={deleteStep.isPending}
            >
              Cancel
            </Button>
            <Button
              variant="destructive"
              onClick={() =>
                removeDialogStep && handleDeleteStep(removeDialogStep.id)
              }
              disabled={deleteStep.isPending}
            >
              {deleteStep.isPending ? 'Removing...' : 'Remove Step'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
