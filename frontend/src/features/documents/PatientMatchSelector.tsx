import { useState } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { usePatients } from '@/features/patients/api';
import type { PatientCandidate } from './types';

interface PatientMatchSelectorProps {
  matchStatus: 'EXACT' | 'CANDIDATES' | 'NO_MATCH';
  candidates: PatientCandidate[];
  matchedPatientId: string | null;
  extractedName: string | null;
  extractedDob: string | null;
  onConfirm: (patientId: string) => void;
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
  onCreateNew,
}: PatientMatchSelectorProps) {
  const [showSearch, setShowSearch] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');

  // WR-06: Defense-in-depth -- truncate and validate AI-extracted fields before display.
  const safeName = extractedName?.slice(0, 100) ?? null;
  const safeDob = extractedDob?.match(/^\d{4}-\d{2}-\d{2}$/) ? extractedDob : null;

  // Fetch all patients for inline search (pilot scale <500 patients)
  const { data: allPatients } = usePatients();
  const filteredPatients = allPatients?.filter((p) => {
    if (!searchTerm) return true;
    const term = searchTerm.toLowerCase();
    return (
      p.firstName.toLowerCase().includes(term) ||
      p.lastName.toLowerCase().includes(term) ||
      p.mrn.toLowerCase().includes(term)
    );
  }).slice(0, 8) ?? [];

  // EXACT match -- single patient with high confidence
  if (matchStatus === 'EXACT' && matchedPatientId && candidates.length > 0) {
    const patient = candidates[0];
    return (
      <div className="space-y-3">
        <h4 className="text-sm font-medium">Patient Match Found</h4>
        <Card>
          <CardContent role="group" aria-label={`Patient match: ${patient.displayName}`}>
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
          <Button onClick={() => onConfirm(patient.patientId)}>Confirm Patient</Button>
          <button
            type="button"
            className="text-sm text-muted-foreground hover:text-foreground underline-offset-4 hover:underline"
            onClick={() => setShowSearch(true)}
          >
            Not this patient
          </button>
        </div>
      </div>
    );
  }

  // CANDIDATES -- ranked list of potential matches
  if (matchStatus === 'CANDIDATES' && candidates.length > 0 && !showSearch) {
    return (
      <div className="space-y-3">
        <h4 className="text-sm font-medium">
          Select Matching Patient
          {safeName && (
            <span className="text-muted-foreground font-normal">
              {' '}&mdash; Extracted: {safeName}
              {safeDob ? `, DOB: ${safeDob}` : ''}
            </span>
          )}
        </h4>
        <div className="space-y-2">
          {candidates.slice(0, 3).map((candidate) => (
            <Card key={candidate.patientId}>
              <CardContent role="group" aria-label={`Candidate: ${candidate.displayName}`}>
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
                    <Button size="sm" variant="outline" onClick={() => onConfirm(candidate.patientId)}>
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
          onClick={() => setShowSearch(true)}
        >
          Search manually
        </button>
      </div>
    );
  }

  // NO_MATCH or showSearch -- inline patient search
  return (
    <div className="space-y-3">
      <h4 className="text-sm font-medium">
        {showSearch ? 'Search for Patient' : 'No automatic match found'}
      </h4>
      <Input
        placeholder="Search by name or MRN..."
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        autoFocus
      />
      {filteredPatients.length > 0 ? (
        <div className="space-y-1 max-h-48 overflow-y-auto">
          {filteredPatients.map((p) => (
            <button
              key={p.id}
              type="button"
              className="w-full text-left rounded-md border p-2 hover:bg-muted/50 transition-colors"
              onClick={() => onConfirm(p.id)}
            >
              <p className="text-sm font-medium">
                {p.firstName} {p.lastName}
              </p>
              <p className="text-xs text-muted-foreground">
                MRN: {p.mrn} | {p.cancerType} | {p.status}
              </p>
            </button>
          ))}
        </div>
      ) : (
        <p className="text-sm text-muted-foreground">
          {searchTerm ? 'No patients match your search.' : 'No patients found.'}
        </p>
      )}
      <div className="flex items-center gap-3 pt-1">
        <Button variant="outline" size="sm" onClick={onCreateNew}>
          Create New Patient
        </Button>
        {showSearch && (
          <button
            type="button"
            className="text-sm text-muted-foreground hover:text-foreground underline-offset-4 hover:underline"
            onClick={() => setShowSearch(false)}
          >
            Back to candidates
          </button>
        )}
      </div>
    </div>
  );
}
