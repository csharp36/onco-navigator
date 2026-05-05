import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type {
  PatientResponse, CreatePatientRequest, CareEventResponse,
  CreateCareEventRequest, UpdateCareEventStatusRequest,
  DeactivatePatientRequest, PathwayStatusResponse,
  PatientPathwayStep, PatientPathwayEdge,
  CreateStepRequest, CreateEdgeRequest,
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

// ── Phase 5: Pathway Step/Edge CRUD Hooks ──────────────────────────────────

export function usePathwaySteps(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'pathway-steps'],
    queryFn: () => apiClient.get<PatientPathwayStep[]>(
      `/patients/${patientId}/pathway/steps`),
  });
}

export function usePathwayEdges(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'pathway-edges'],
    queryFn: () => apiClient.get<PatientPathwayEdge[]>(
      `/patients/${patientId}/pathway/edges`),
  });
}

export function useCreateStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateStepRequest) =>
      apiClient.post<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}

export function useUpdateStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ stepId, data }: { stepId: string; data: CreateStepRequest }) =>
      apiClient.put<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

export function useDeleteStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.delete<void>(`/patients/${patientId}/pathway/steps/${stepId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
    },
  });
}

export function useSkipStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ stepId, reason }: { stepId: string; reason: string }) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/skip`, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
    },
  });
}

export function useUnskipStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/unskip`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

// ── Phase 6: AI-proposed step confirm/reject hooks ──────────────────────────

export function useConfirmStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.post<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/confirm`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}

export function useRejectStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/reject`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

/**
 * Fetches alreadyCoveredEventTypes for a specific document.
 * Used to display the "Already covered" informational section (D-10).
 * Only called when PROPOSED steps exist with a sourceDocumentId.
 */
export function useDocumentAlreadyCovered(documentId: string | null) {
  return useQuery({
    queryKey: ['documents', documentId, 'already-covered'],
    queryFn: () =>
      apiClient.get<{ alreadyCoveredEventTypes: string | null }>(
        `/documents/${documentId}`),
    enabled: !!documentId,
    select: (data) => {
      const raw = data?.alreadyCoveredEventTypes;
      if (!raw) return [];
      return raw.split(',').filter(Boolean);
    },
    staleTime: Infinity,  // Static data -- document extraction results don't change
  });
}

export function useCreateEdge(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateEdgeRequest) =>
      apiClient.post<PatientPathwayEdge>(
        `/patients/${patientId}/pathway/edges`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}

export function useDeleteEdge(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) =>
      apiClient.delete<void>(`/patients/${patientId}/pathway/edges/${edgeId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}
