export interface AlertResponse {
  id: string;
  patientId: string;
  patientName: string;
  patientMrn: string;
  alertType: 'DELAYED_EVENT' | 'MISSING_EVENT' | 'OUT_OF_ORDER';
  severityLabel: 'OVERDUE' | 'MISSING' | 'OUT OF ORDER';
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  pathwayStepName: string;
  deviationDescription: string;
  suggestedAction: string;
  createdAt: string;
  timeElapsed: string;
}

export interface ResolveAlertRequest {
  notes: string;
}

export interface AlertCountResponse {
  count: number;
}
