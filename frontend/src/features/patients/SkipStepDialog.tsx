import { useState } from 'react';

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

// ─── Props ────────────────────────────────────────────────────────────────────

interface SkipStepDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  stepName: string;
  onConfirm: (reason: string) => void;
  isPending: boolean;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SkipStepDialog({
  open,
  onOpenChange,
  stepName,
  onConfirm,
  isPending,
}: SkipStepDialogProps) {
  const [reason, setReason] = useState('');

  function handleConfirm() {
    if (!reason.trim()) return;
    onConfirm(reason.trim());
  }

  function handleOpenChange(newOpen: boolean) {
    if (!newOpen) {
      setReason('');
    }
    onOpenChange(newOpen);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>Skip &ldquo;{stepName}&rdquo;?</DialogTitle>
          <DialogDescription>
            This step will be marked as skipped and will not be evaluated for
            alerts. You can restore it later.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-2">
          <Label htmlFor="skipReason">Reason for skipping (required)</Label>
          <Input
            id="skipReason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Enter reason..."
            aria-required="true"
            disabled={isPending}
          />
        </div>

        <DialogFooter>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleOpenChange(false)}
            disabled={isPending}
          >
            Cancel
          </Button>
          <Button
            type="button"
            onClick={handleConfirm}
            disabled={!reason.trim() || isPending}
          >
            {isPending ? 'Skipping...' : 'Skip Step'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
