import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  PatientResponse, CreatePatientRequest, CareEventResponse,
  CreateCareEventRequest, UpdateCareEventStatusRequest,
  DeactivatePatientRequest, PathwayStatusResponse,
} from './types';

export function usePatients(mrn?: string) {
  return useQuery({
    queryKey: mrn ? ['patients', { mrn }] : ['patients'],
    queryFn: () => apiClient.get<PatientResponse[]>(
      mrn ? `/patients?mrn=${encodeURIComponent(mrn)}` : '/patients'
    ),
  });
}

export function usePatient(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => apiClient.get<PatientResponse>(`/patients/${patientId}`),
  });
}

export function useCreatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePatientRequest) =>
      apiClient.post<PatientResponse>('/patients', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
}

export function useDeactivatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ patientId, data }: { patientId: string; data: DeactivatePatientRequest }) =>
      apiClient.patch<void>(`/patients/${patientId}/deactivate`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
    },
  });
}

export function useCareEvents(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'care-events'],
    queryFn: () => apiClient.get<CareEventResponse[]>(`/patients/${patientId}/care-events`),
  });
}

export function useCreateCareEvent(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateCareEventRequest) =>
      apiClient.post<CareEventResponse>(`/patients/${patientId}/care-events`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'care-events'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
    },
  });
}

export function useUpdateCareEventStatus(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ careEventId, data }: { careEventId: string; data: UpdateCareEventStatusRequest }) =>
      apiClient.patch<CareEventResponse>(`/patients/${patientId}/care-events/${careEventId}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'care-events'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
    },
  });
}

export function usePathwayStatus(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'pathway-status'],
    queryFn: () => apiClient.get<PathwayStatusResponse>(`/patients/${patientId}/pathway-status`),
  });
}
