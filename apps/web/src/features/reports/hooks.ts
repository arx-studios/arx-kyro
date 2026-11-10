'use client';

import { useMutation } from '@tanstack/react-query';
import { unwrap, type ReportCreate, type ReportReceipt } from '@kyro/api-client';
import { api } from '@/lib/api';

export function useCreateReport() {
  return useMutation({
    mutationFn: async (body: ReportCreate): Promise<ReportReceipt> =>
      unwrap(await api.POST('/api/v1/reports', { body })),
  });
}
