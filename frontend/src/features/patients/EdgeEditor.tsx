import { useState } from 'react';
import { ChevronDown, ChevronRight, X } from 'lucide-react';

import { Button } from '@/components/ui/button';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import type { PathwayStepStatus, PatientPathwayEdge } from './types';

// ─── Props ────────────────────────────────────────────────────────────────────

interface EdgeEditorProps {
  steps: PathwayStepStatus[];
  edges: PatientPathwayEdge[];
  onAddEdge: (sourceStepId: string, targetStepId: string) => void;
  onRemoveEdge: (edgeId: string) => void;
  addEdgePending: boolean;
  addEdgeError: string | null;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function EdgeEditor({
  steps,
  edges,
  onAddEdge,
  onRemoveEdge,
  addEdgePending,
  addEdgeError,
}: EdgeEditorProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [showAddForm, setShowAddForm] = useState(false);
  const [sourceStepId, setSourceStepId] = useState('');
  const [targetStepId, setTargetStepId] = useState('');

  // Build step name lookup
  const stepNameById = Object.fromEntries(
    steps.map((s) => [s.stepId, s.stepName])
  );

  function handleAddEdge() {
    if (!sourceStepId || !targetStepId || sourceStepId === targetStepId) return;
    onAddEdge(sourceStepId, targetStepId);
  }

  function handleAddSuccess() {
    setSourceStepId('');
    setTargetStepId('');
    setShowAddForm(false);
  }

  // Reset the add form when add succeeds (no error, not pending)
  // The parent resets addEdgeError on success via mutation onSuccess
  const isAddDisabled =
    !sourceStepId ||
    !targetStepId ||
    sourceStepId === targetStepId ||
    addEdgePending;

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <div className="mt-4 rounded-md border">
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="flex w-full items-center justify-between p-3 text-sm font-semibold hover:bg-muted/30 rounded-md transition-colors"
            aria-expanded={isOpen}
          >
            <span>Dependencies</span>
            {isOpen ? (
              <ChevronDown className="h-4 w-4 text-muted-foreground" />
            ) : (
              <ChevronRight className="h-4 w-4 text-muted-foreground" />
            )}
          </button>
        </CollapsibleTrigger>

        <CollapsibleContent>
          <div className="px-3 pb-3 space-y-3">
            {/* Existing edges */}
            {edges.length === 0 ? (
              <p className="text-xs text-muted-foreground py-1">
                No dependencies defined. Steps run in any order.
              </p>
            ) : (
              <ul className="space-y-1.5">
                {edges.map((edge) => (
                  <li
                    key={edge.id}
                    className="flex items-center justify-between gap-2 text-xs rounded-md bg-muted/30 px-2.5 py-1.5"
                  >
                    <span>
                      <span className="font-medium">
                        {stepNameById[edge.sourceStepId] ?? edge.sourceStepId}
                      </span>{' '}
                      must complete before{' '}
                      <span className="font-medium">
                        {stepNameById[edge.targetStepId] ?? edge.targetStepId}
                      </span>
                    </span>
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-6 w-6 p-0 shrink-0"
                      aria-label={`Remove dependency between ${stepNameById[edge.sourceStepId] ?? edge.sourceStepId} and ${stepNameById[edge.targetStepId] ?? edge.targetStepId}`}
                      onClick={() => onRemoveEdge(edge.id)}
                    >
                      <X className="h-3.5 w-3.5" />
                    </Button>
                  </li>
                ))}
              </ul>
            )}

            {/* Add dependency section */}
            {showAddForm ? (
              <div className="space-y-3 pt-1">
                <div className="grid grid-cols-2 gap-2">
                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground">After step:</p>
                    <Select
                      value={sourceStepId}
                      onValueChange={setSourceStepId}
                      disabled={addEdgePending}
                    >
                      <SelectTrigger className="h-8 text-xs">
                        <SelectValue placeholder="Select step" />
                      </SelectTrigger>
                      <SelectContent>
                        {steps.map((step) => (
                          <SelectItem key={step.stepId} value={step.stepId}>
                            {step.stepName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1">
                    <p className="text-xs text-muted-foreground">
                      Before step:
                    </p>
                    <Select
                      value={targetStepId}
                      onValueChange={setTargetStepId}
                      disabled={addEdgePending}
                    >
                      <SelectTrigger className="h-8 text-xs">
                        <SelectValue placeholder="Select step" />
                      </SelectTrigger>
                      <SelectContent>
                        {steps.map((step) => (
                          <SelectItem key={step.stepId} value={step.stepId}>
                            {step.stepName}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                {addEdgeError && (
                  <p className="text-destructive text-xs">
                    Cannot add this dependency — it would create a circular
                    path. Step A cannot depend on Step B if Step B already
                    depends on Step A.
                  </p>
                )}

                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    onClick={handleAddEdge}
                    disabled={isAddDisabled}
                  >
                    {addEdgePending ? 'Adding...' : 'Add Dependency'}
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setShowAddForm(false);
                      setSourceStepId('');
                      setTargetStepId('');
                    }}
                    disabled={addEdgePending}
                  >
                    Cancel
                  </Button>
                </div>
              </div>
            ) : (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setShowAddForm(true)}
              >
                Add Dependency
              </Button>
            )}
          </div>
        </CollapsibleContent>
      </div>
    </Collapsible>
  );
}

// Export handleAddSuccess as a utility for parent to call after success
export type { EdgeEditorProps };
