import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { DashboardStatsResponse } from './types';

export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => apiClient.get<DashboardStatsResponse>('/dashboard/stats'),
    refetchInterval: 30_000,
  });
}
