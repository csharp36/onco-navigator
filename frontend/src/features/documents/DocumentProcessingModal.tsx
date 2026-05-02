import { useMemo, useState } from 'react';
import { Loader2, CheckCircle2, XCircle, AlertTriangle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { PatientMatchSelector } from './PatientMatchSelector';
import type { DocumentUploadResponse, DocumentType } from './types';

const STEPS = ['Uploading', 'Extracting text', 'Classifying document', 'Matching patient', 'Ready'] as const;

const DOCUMENT_TYPE_OPTIONS: { value: DocumentType; label: string }[] = [
  { value: 'PATHOLOGY_REPORT', label: 'Pathology Report' },
  { value: 'RADIOLOGY_REPORT', label: 'Radiology Report' },
  { value: 'REFERRAL_LETTER', label: 'Referral Letter' },
  { value: 'OPERATIVE_NOTE', label: 'Operative Note' },
  { value: 'LAB_RESULT', label: 'Lab Result' },
];

interface DocumentProcessingModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  uploadResult: DocumentUploadResponse | null;
  isUploading: boolean;
  onPatientSelected: (patientId: string) => void;
  onManualClassification: (documentType: DocumentType) => void;
  onCreateNewPatient: () => void;
  onSearchManual: () => void;
}

export function DocumentProcessingModal({
  open,
  onOpenChange,
  uploadResult,
  isUploading,
  onPatientSelected,
  onManualClassification,
  onCreateNewPatient,
  onSearchManual,
}: DocumentProcessingModalProps) {
  const [showMatchSelector, setShowMatchSelector] = useState(false);

  // Determine current step based on upload state
  // Backend processes steps 2-4 synchronously in the upload call
  const currentStep = useMemo(() => {
    if (!isUploading && !uploadResult) return 0;
    if (isUploading) return 0;
    // Upload complete means all backend steps finished
    return 4;
  }, [isUploading, uploadResult]);

  const progressPercent = useMemo(() => {
    if (isUploading) return 20;
    if (uploadResult) return 100;
    return 0;
  }, [isUploading, uploadResult]);

  const isComplete = currentStep === 4 && uploadResult !== null;
  const showCircuitBreakerFallback = isComplete && uploadResult?.classificationResult === null;
  const needsPatientMatch = isComplete && uploadResult !== null && uploadResult.patientMatchStatus !== 'EXACT';

  // Show match selector once processing is complete and match is needed
  const shouldShowMatchSelector = showMatchSelector || (needsPatientMatch && isComplete);

  function handleClose() {
    setShowMatchSelector(false);
    onOpenChange(false);
  }

  return (
    <Dialog open={open} onOpenChange={isUploading ? undefined : onOpenChange}>
      <DialogContent
        className="max-w-lg"
        showCloseButton={!isUploading}
        onInteractOutside={(e) => {
          if (isUploading) e.preventDefault();
        }}
      >
        <DialogHeader>
          <DialogTitle>Processing Document</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Progress bar */}
          <Progress value={progressPercent} />

          {/* Step stepper */}
          <div className="space-y-2">
            {STEPS.map((stepLabel, index) => {
              const isStepComplete = index < currentStep || (index === 4 && isComplete);
              const isStepActive = index === currentStep && isUploading;

              return (
                <div key={stepLabel} className="flex items-center gap-3">
                  {isStepComplete ? (
                    <CheckCircle2 className="size-5 text-green-600 shrink-0" />
                  ) : isStepActive ? (
                    <Loader2 className="size-5 animate-spin text-muted-foreground shrink-0" />
                  ) : (
                    <div className="size-5 rounded-full border-2 border-muted shrink-0" />
                  )}
                  <span className={`text-sm ${isStepComplete ? 'text-foreground' : 'text-muted-foreground'}`}>
                    {stepLabel}
                  </span>
                </div>
              );
            })}
          </div>

          {/* Classification results summary -- spot-check extracted data */}
          {isComplete && uploadResult?.classificationResult && (
            <div className="rounded-md border bg-muted/40 p-4 space-y-2">
              <h4 className="text-sm font-medium">Extracted Data</h4>
              <div className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
                <span className="text-muted-foreground">Document Type</span>
                <span className="font-medium">
                  {uploadResult.classificationResult.documentType?.replace(/_/g, ' ') ?? 'Unknown'}
                </span>

                {uploadResult.classificationResult.patientName && (
                  <>
                    <span className="text-muted-foreground">Patient Name</span>
                    <span>{uploadResult.classificationResult.patientName}</span>
                  </>
                )}

                {uploadResult.classificationResult.mrn && (
                  <>
                    <span className="text-muted-foreground">MRN</span>
                    <span>{uploadResult.classificationResult.mrn}</span>
                  </>
                )}

                {uploadResult.classificationResult.dateOfBirth && (
                  <>
                    <span className="text-muted-foreground">Date of Birth</span>
                    <span>{uploadResult.classificationResult.dateOfBirth}</span>
                  </>
                )}

                {uploadResult.classificationResult.eventType && (
                  <>
                    <span className="text-muted-foreground">Event Type</span>
                    <span>{uploadResult.classificationResult.eventType.replace(/_/g, ' ')}</span>
                  </>
                )}

                {uploadResult.classificationResult.eventDate && (
                  <>
                    <span className="text-muted-foreground">Event Date</span>
                    <span>{uploadResult.classificationResult.eventDate}</span>
                  </>
                )}

                <span className="text-muted-foreground">Confidence</span>
                <span>
                  <Badge variant={
                    uploadResult.classificationResult.confidence === 'high' ? 'secondary' : 'outline'
                  }>
                    {uploadResult.classificationResult.confidence}
                  </Badge>
                </span>
              </div>

              {uploadResult.classificationResult.extractedNotes && (
                <div className="pt-1">
                  <span className="text-xs text-muted-foreground">Key Findings</span>
                  <p className="text-sm mt-0.5 line-clamp-3">
                    {uploadResult.classificationResult.extractedNotes}
                  </p>
                </div>
              )}
            </div>
          )}

          {/* Circuit breaker fallback -- AI classification unavailable */}
          {showCircuitBreakerFallback && (
            <div className="rounded-md border border-amber-500/50 bg-amber-50 p-4 dark:bg-amber-950/20">
              <div className="flex items-start gap-2">
                <AlertTriangle className="size-5 text-amber-600 shrink-0 mt-0.5" />
                <div className="space-y-2 flex-1">
                  <p className="text-sm font-medium text-amber-800 dark:text-amber-200">
                    AI classification is temporarily unavailable. Select the document type manually.
                  </p>
                  <Select onValueChange={(value) => onManualClassification(value as DocumentType)}>
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Select document type" />
                    </SelectTrigger>
                    <SelectContent>
                      {DOCUMENT_TYPE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>
          )}

          {/* Patient match selector -- shown when not exact match */}
          {shouldShowMatchSelector && uploadResult && (
            <PatientMatchSelector
              matchStatus={uploadResult.patientMatchStatus}
              candidates={uploadResult.candidates}
              matchedPatientId={uploadResult.matchedPatientId}
              extractedName={uploadResult.classificationResult?.patientName ?? null}
              extractedDob={uploadResult.classificationResult?.dateOfBirth ?? null}
              onConfirm={(patientId) => {
                onPatientSelected(patientId);
                handleClose();
              }}
              onReject={() => {
                handleClose();
                onSearchManual();
              }}
              onCreateNew={() => {
                handleClose();
                onCreateNewPatient();
              }}
            />
          )}

          {/* Exact match auto-confirm */}
          {isComplete && uploadResult?.patientMatchStatus === 'EXACT' && uploadResult.matchedPatientId && (
            <div className="rounded-md border bg-muted/40 p-4">
              <p className="text-sm text-muted-foreground">
                Patient matched automatically. Opening care event form...
              </p>
            </div>
          )}
        </div>

        {isComplete && (
          <DialogFooter>
            <Button variant="outline" onClick={handleClose}>
              Close
            </Button>
          </DialogFooter>
        )}
      </DialogContent>
    </Dialog>
  );
}
