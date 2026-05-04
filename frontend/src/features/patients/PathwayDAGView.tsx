import { Skeleton } from '@/components/ui/skeleton';
import { StepRow } from './StepRow';
import type { PathwayStepStatus } from './types';

// ─── Props ────────────────────────────────────────────────────────────────────

interface PathwayDAGViewProps {
  steps: PathwayStepStatus[];
  isLoading: boolean;
}

// ─── Compute isLastAtDepth for branching indicators ───────────────────────────

function computeIsLastAtDepth(steps: PathwayStepStatus[]): boolean[] {
  return steps.map((step, index) => {
    if (step.depth === 0) return false;
    // A step is "last at depth" if the next step at the same depth does not exist
    // (i.e., the next step in the list at the same depth or shallower depth is not at this depth)
    for (let i = index + 1; i < steps.length; i++) {
      if (steps[i].depth === step.depth) return false;
      if (steps[i].depth < step.depth) return true;
    }
    return true;
  });
}

// ─── Component ────────────────────────────────────────────────────────────────

export function PathwayDAGView({ steps, isLoading }: PathwayDAGViewProps) {
  if (isLoading) {
    return (
      <div className="space-y-2" aria-label="Loading pathway steps">
        {Array.from({ length: 5 }).map((_, i) => (
          <Skeleton key={i} className="h-[44px] w-full" />
        ))}
      </div>
    );
  }

  if (steps.length === 0) {
    return (
      <div className="py-8 text-center space-y-2">
        <p className="text-sm font-medium text-foreground">No pathway steps</p>
        <p className="text-sm text-muted-foreground">
          This patient has an empty pathway. Use &apos;Edit Pathway&apos; to add steps
          manually, or upload clinical documents to build the pathway.
        </p>
      </div>
    );
  }

  const isLastAtDepthFlags = computeIsLastAtDepth(steps);

  return (
    <div aria-live="polite">
      <ol className="space-y-1">
        {steps.map((step, index) => (
          <StepRow
            key={step.stepId}
            step={step}
            isEditing={false}
            isLastAtDepth={isLastAtDepthFlags[index]}
          />
        ))}
      </ol>
    </div>
  );
}
