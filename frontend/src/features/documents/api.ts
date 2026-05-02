import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { DocumentUploadResponse, DocumentSummaryResponse } from './types';

export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (formData: FormData) =>
      apiClient.upload<DocumentUploadResponse>('/documents/upload', formData),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });
}

export function usePatientDocuments(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'documents'],
    queryFn: () => apiClient.get<DocumentSummaryResponse[]>(`/documents/patient/${patientId}`),
    enabled: !!patientId,
  });
}

export function useDocument(documentId: string | undefined) {
  return useQuery({
    queryKey: ['documents', documentId],
    queryFn: () => apiClient.get<DocumentSummaryResponse>(`/documents/${documentId}`),
    enabled: !!documentId,
  });
}

export function useLinkDocumentToPatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ documentId, patientId }: { documentId: string; patientId: string }) =>
      apiClient.patch<DocumentSummaryResponse>(`/documents/${documentId}/link-patient`, { patientId }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });
}

export function getDocumentContentUrl(documentId: string): string {
  return `/api/documents/${documentId}/content`;
}
