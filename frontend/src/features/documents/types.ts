export interface DocumentUploadResponse {
  documentId: string;
  classificationResult: DocumentClassificationResult | null;
  patientMatchStatus: 'EXACT' | 'CANDIDATES' | 'NO_MATCH';
  candidates: PatientCandidate[];
  matchedPatientId: string | null;
}

export interface DocumentClassificationResult {
  documentType: string;
  confidence: string;
  mrn: string | null;
  patientName: string | null;
  dateOfBirth: string | null;
  eventType: string | null;
  eventDate: string | null;
  extractedNotes: string | null;
}

export interface PatientCandidate {
  patientId: string;
  displayName: string;
  mrn: string;
  dateOfBirth: string;
  confidence: string;
}

export type DocumentType =
  | 'PATHOLOGY_REPORT'
  | 'RADIOLOGY_REPORT'
  | 'REFERRAL_LETTER'
  | 'OPERATIVE_NOTE'
  | 'LAB_RESULT'
  | 'UNKNOWN';

export interface DocumentSummaryResponse {
  id: string;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  documentType: string | null;
  classificationSource: string | null;
  careEventId: string | null;
  createdAt: string;
}

export type ProcessingStep = 'uploading' | 'extracting' | 'classifying' | 'matching' | 'ready';

export interface DocumentPrefillData {
  documentId: string;
  classification: DocumentClassificationResult;
  patientId: string;
}
