import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { AlertResponse, ResolveAlertRequest, AlertCountResponse } from './types';

export function useAlerts() {
  return useQuery({
    queryKey: ['alerts'],
    queryFn: () => apiClient.get<AlertResponse[]>('/alerts'),
    refetchInterval: 30_000,
  });
}

export function useAlertCount() {
  return useQuery({
    queryKey: ['alerts', 'count'],
    queryFn: () => apiClient.get<AlertCountResponse>('/alerts/count'),
    refetchInterval: 30_000,
    staleTime: 0,
  });
}

export function useResolveAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ alertId, notes }: { alertId: string; notes: string }) =>
      apiClient.post<void>(`/alerts/${alertId}/resolve`, { notes } as ResolveAlertRequest),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] }),
        queryClient.invalidateQueries({ queryKey: ['alerts'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] }),
      ]);
    },
  });
}
