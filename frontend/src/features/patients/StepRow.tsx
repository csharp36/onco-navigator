import {
  AlertTriangle,
  CheckCircle2,
  Circle,
  MinusCircle,
  Pencil,
  RotateCcw,
  Trash2,
} from 'lucide-react';

import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import type { PathwayStepStatus } from './types';

// ─── Step icon helper ─────────────────────────────────────────────────────────

function PathwayStepIcon({ step }: { step: PathwayStepStatus }) {
  if (step.status === 'COMPLETED') {
    return (
      <CheckCircle2
        className="h-5 w-5 text-green-600 shrink-0"
        aria-label="Completed"
      />
    );
  }
  if (step.status === 'ACTIVE' && step.hasActiveAlert) {
    return (
      <AlertTriangle
        className="h-5 w-5 text-red-500 shrink-0"
        aria-label="Active - overdue"
      />
    );
  }
  if (step.status === 'ACTIVE') {
    return (
      <Circle
        className="h-5 w-5 text-blue-500 shrink-0"
        aria-label="Active"
      />
    );
  }
  if (step.status === 'PROPOSED') {
    return (
      <Circle
        className="h-5 w-5 text-muted-foreground shrink-0 icon-dashed"
        aria-label="Proposed - pending confirmation"
      />
    );
  }
  // SKIPPED
  return (
    <MinusCircle
      className="h-5 w-5 text-muted-foreground shrink-0"
      aria-label="Skipped"
    />
  );
}

// ─── Step name text styling ───────────────────────────────────────────────────

function stepNameClass(status: PathwayStepStatus['status']): string {
  switch (status) {
    case 'ACTIVE':
    case 'COMPLETED':
      return 'font-semibold text-sm leading-snug';
    case 'PROPOSED':
      return 'text-sm text-muted-foreground';
    case 'SKIPPED':
      return 'text-sm line-through text-muted-foreground';
    default:
      return 'text-sm';
  }
}

// ─── Props ────────────────────────────────────────────────────────────────────

interface StepRowProps {
  step: PathwayStepStatus;
  isEditing: boolean;
  isLastAtDepth?: boolean;
  onEdit?: () => void;
  onRemove?: () => void;
  onSkip?: () => void;
  onUnskip?: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function StepRow({
  step,
  isEditing,
  isLastAtDepth = false,
  onEdit,
  onRemove,
  onSkip,
  onUnskip,
}: StepRowProps) {
  const branchConnector =
    step.depth > 0
      ? isLastAtDepth
        ? '\u2514\u2500\u2500'
        : '\u251C\u2500\u2500'
      : null;

  return (
    <TooltipProvider>
      <li
        className={`flex items-start gap-3 rounded-md p-3 min-h-[44px] ${
          step.hasActiveAlert ? 'bg-amber-50' : ''
        }`}
        style={{ paddingLeft: `calc(${step.depth * 24}px + 0.75rem)` }}
        aria-level={step.depth + 1}
      >
        {/* Branching indicator */}
        {branchConnector && (
          <span
            className="font-mono text-muted-foreground text-sm shrink-0"
            aria-hidden="true"
          >
            {branchConnector}
          </span>
        )}

        {/* Status icon */}
        <PathwayStepIcon step={step} />

        {/* Step content */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            {step.status === 'PROPOSED' ? (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span className={stepNameClass(step.status)}>
                    {step.stepName}
                  </span>
                </TooltipTrigger>
                <TooltipContent>
                  <p className="max-w-xs">
                    Pending confirmation — this step was proposed by AI and will
                    not be evaluated until a nurse confirms it.
                  </p>
                </TooltipContent>
              </Tooltip>
            ) : (
              <span className={stepNameClass(step.status)}>
                {step.stepName}
              </span>
            )}

            {step.status === 'SKIPPED' && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <span>
                    <Badge variant="outline">Skipped</Badge>
                  </span>
                </TooltipTrigger>
                {step.skipReason && (
                  <TooltipContent>
                    <p className="max-w-xs">{step.skipReason}</p>
                  </TooltipContent>
                )}
              </Tooltip>
            )}
          </div>

          <p className="text-xs text-muted-foreground mt-0.5">
            {step.timingInfo}
          </p>
        </div>

        {/* Edit mode actions */}
        {isEditing && (
          <div className="flex items-center gap-1 shrink-0">
            {step.status === 'ACTIVE' && (
              <>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 w-8 p-0"
                  aria-label={`Edit ${step.stepName}`}
                  onClick={onEdit}
                >
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 px-2 text-xs"
                  onClick={onSkip}
                >
                  Skip
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                  aria-label={`Remove ${step.stepName}`}
                  onClick={onRemove}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </>
            )}

            {step.status === 'COMPLETED' && (
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                aria-label={`Remove ${step.stepName}`}
                onClick={onRemove}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            )}

            {step.status === 'PROPOSED' && (
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                aria-label={`Remove ${step.stepName}`}
                onClick={onRemove}
              >
                <Trash2 className="h-4 w-4" />
              </Button>
            )}

            {step.status === 'SKIPPED' && (
              <>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 px-2 text-xs"
                  onClick={onUnskip}
                >
                  <RotateCcw className="h-3.5 w-3.5 mr-1" />
                  Restore
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                  aria-label={`Remove ${step.stepName}`}
                  onClick={onRemove}
                >
                  <Trash2 className="h-4 w-4" />
                </Button>
              </>
            )}
          </div>
        )}
      </li>
    </TooltipProvider>
  );
}
