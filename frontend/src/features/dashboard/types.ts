import type { AlertResponse } from '../alerts/types';

export interface DashboardStatsResponse {
  openAlertCount: number;
  activePatients: number;
  onTrackPatients: number;
  topUrgentAlerts: AlertResponse[];
}
