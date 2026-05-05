export interface PatientResponse {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  mrn: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  cancerStage: string;
  diagnosisDate: string;
  referralReceivedAt: string | null;
  assignedNavigatorId: string | null;
  treatingPhysician: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  summaryStatus: 'On Track' | 'Alert Active' | 'Inactive';
  createdAt: string;
}

export interface CreatePatientRequest {
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  mrn: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  cancerStage: string;
  diagnosisDate: string;
  assignedNavigatorId?: string;
  treatingPhysician?: string;
  pathwayMode?: 'template' | 'empty';
  templateId?: string;  // Phase 8: specific template variant selection
}

export interface CareEventResponse {
  id: string;
  patientId: string;
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes: string | null;
  pathwayStepId: string | null;
  createdAt: string;
  // Phase 7: scheduling coordination fields
  expectedCompletionDate: string | null;
  schedulingConfirmed: boolean;
  externalFacilityName: string | null;
}

export interface CreateCareEventRequest {
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes?: string;
  documentId?: string;
  // Phase 7: scheduling coordination fields
  expectedCompletionDate?: string;
  schedulingConfirmed?: boolean;
  externalFacilityName?: string;
}

export interface UpdateCareEventStatusRequest {
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
}

export interface DeactivatePatientRequest {
  reason: string;
}

export interface PathwayStatusResponse {
  patientId: string;
  steps: PathwayStepStatus[];
}

// ── Phase 5: Per-Patient Pathway DAG Types ──────────────────────────────────

export type PathwayStepStatusEnum = 'ACTIVE' | 'PROPOSED' | 'COMPLETED' | 'SKIPPED' | 'REJECTED';

export interface PathwayStepStatus {
  stepId: string;
  stepName: string;
  status: PathwayStepStatusEnum;
  depth: number;
  sortOrder: number;
  completionDate: string | null;
  timingInfo: string;
  hasActiveAlert: boolean;
  skipReason: string | null;
  prerequisiteStepIds: string[];
  // Phase 6: AI extraction source tracking
  sourceDocumentId: string | null;
  extractionSource: 'TEMPLATE' | 'MANUAL' | 'AI_EXTRACTED' | null;
  sourceDocumentFilename: string | null;
}

export interface PatientPathwayStep {
  id: string;
  pathwayId: string;
  name: string;
  description: string | null;
  eventType: string | null;
  windowDays: number | null;
  required: boolean;
  status: PathwayStepStatusEnum;
  skipReason: string | null;
  alertText: string | null;
  suggestedAction: string | null;
  completedAt: string | null;
  completedCareEventId: string | null;
  depth: number;
  sortOrder: number;
  prerequisiteStepIds: string[];
  createdAt: string;
  // Phase 6: AI extraction source tracking
  sourceDocumentId: string | null;
  extractionSource: 'TEMPLATE' | 'MANUAL' | 'AI_EXTRACTED' | null;
  sourceDocumentFilename: string | null;
}

export interface PatientPathwayEdge {
  id: string;
  pathwayId: string;
  sourceStepId: string;
  targetStepId: string;
  createdAt: string;
}

export interface CreateStepRequest {
  name: string;
  description?: string;
  eventType?: string;
  windowDays?: number;
  required: boolean;
  alertText?: string;
  suggestedAction?: string;
}

export interface CreateEdgeRequest {
  sourceStepId: string;
  targetStepId: string;
}

export interface SkipStepRequest {
  reason: string;
}

// -- Phase 8: Template Inheritance Types --

export interface PathwayTemplateResponse {
  id: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  name: string;
  description: string | null;
  parentTemplateId: string | null;
  version: number;
  isRoot: boolean;
}
