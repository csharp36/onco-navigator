import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { PatientCandidate } from './types';

interface PatientMatchSelectorProps {
  matchStatus: 'EXACT' | 'CANDIDATES' | 'NO_MATCH';
  candidates: PatientCandidate[];
  matchedPatientId: string | null;
  extractedName: string | null;
  extractedDob: string | null;
  onConfirm: (patientId: string) => void;
  onReject: () => void;
  onCreateNew: () => void;
}

function getConfidenceBadgeVariant(confidence: string): 'secondary' | 'outline' {
  const lower = confidence.toLowerCase();
  if (lower === 'high' || lower === 'medium') return 'secondary';
  return 'outline';
}

export function PatientMatchSelector({
  matchStatus,
  candidates,
  matchedPatientId,
  extractedName,
  extractedDob,
  onConfirm,
  onReject,
  onCreateNew,
}: PatientMatchSelectorProps) {
  // WR-06: Defense-in-depth — truncate and validate AI-extracted fields before display.
  // AI classification of adversarial document content could produce unusual strings.
  const safeName = extractedName?.slice(0, 100) ?? null;
  const safeDob = extractedDob?.match(/^\d{4}-\d{2}-\d{2}$/) ? extractedDob : null;

  // EXACT match -- single patient with high confidence
  if (matchStatus === 'EXACT' && matchedPatientId && candidates.length > 0) {
    const patient = candidates[0];
    return (
      <div className="space-y-3">
        <h4 className="text-sm font-medium">Patient Match Found</h4>
        <Card>
          <CardContent
            role="group"
            aria-label={`Patient match candidate: ${patient.displayName}`}
          >
            <div className="flex items-center justify-between">
              <div className="space-y-1">
                <p className="text-sm font-medium">{patient.displayName}</p>
                <p className="text-xs text-muted-foreground">
                  MRN: {patient.mrn} | DOB: {patient.dateOfBirth}
                </p>
              </div>
              <Badge variant="secondary">High confidence match</Badge>
            </div>
          </CardContent>
        </Card>
        <div className="flex items-center gap-3">
          <Button onClick={() => onConfirm(patient.patientId)}>
            Confirm Patient
          </Button>
          <button
            type="button"
            className="text-sm text-muted-foreground hover:text-foreground underline-offset-4 hover:underline"
            onClick={onReject}
          >
            Not this patient
          </button>
        </div>
      </div>
    );
  }

  // CANDIDATES -- ranked list of potential matches
  if (matchStatus === 'CANDIDATES' && candidates.length > 0) {
    const displayCandidates = candidates.slice(0, 3);
    return (
      <div className="space-y-3">
        <h4 className="text-sm font-medium">
          Select Matching Patient
          {safeName && (
            <span className="text-muted-foreground font-normal">
              {' '}-- Extracted: {safeName}
              {safeDob ? `, DOB: ${safeDob}` : ''}
            </span>
          )}
        </h4>
        <div className="space-y-2">
          {displayCandidates.map((candidate) => (
            <Card key={candidate.patientId}>
              <CardContent
                role="group"
                aria-label={`Patient match candidate: ${candidate.displayName}`}
              >
                <div className="flex items-center justify-between">
                  <div className="space-y-1">
                    <p className="text-sm font-medium">{candidate.displayName}</p>
                    <p className="text-xs text-muted-foreground">
                      MRN: {candidate.mrn} | DOB: {candidate.dateOfBirth}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge variant={getConfidenceBadgeVariant(candidate.confidence)}>
                      {candidate.confidence}
                    </Badge>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => onConfirm(candidate.patientId)}
                    >
                      Select
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
        <button
          type="button"
          className="text-sm text-muted-foreground hover:text-foreground underline-offset-4 hover:underline"
          onClick={onReject}
        >
          Search manually
        </button>
      </div>
    );
  }

  // NO_MATCH -- no patients found
  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium">No automatic match found</h4>
      <p className="text-sm text-muted-foreground">
        Search for an existing patient or create a new record with the extracted information.
      </p>
      <div className="flex items-center gap-3">
        <Button variant="outline" onClick={onCreateNew}>
          Create New Patient
        </Button>
        <button
          type="button"
          className="text-sm text-muted-foreground hover:text-foreground underline-offset-4 hover:underline"
          onClick={onReject}
        >
          Search manually
        </button>
      </div>
    </div>
  );
}
