export interface PatientResponse {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  mrn: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  cancerStage: string;
  diagnosisDate: string;
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
}

export interface CreateCareEventRequest {
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes?: string;
  documentId?: string;
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

export interface PathwayStepStatus {
  stepId: string;
  stepNumber: number;
  stepName: string;
  status: 'COMPLETED' | 'OVERDUE' | 'MISSING' | 'UPCOMING';
  completionDate: string | null;
  timingInfo: string;
  hasActiveAlert: boolean;
}
