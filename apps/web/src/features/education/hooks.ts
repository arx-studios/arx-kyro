'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { unwrap, type EducationRequestCreate, type EducationRequestUpdate } from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

export function useMyRequests() {
  return useQuery({
    queryKey: queryKeys.education.list,
    queryFn: async () => unwrap(await api.GET('/api/v1/me/education/requests')),
  });
}

export function useRequest(id: string) {
  return useQuery({
    queryKey: queryKeys.education.detail(id),
    queryFn: async () =>
      unwrap(await api.GET('/api/v1/education/requests/{requestId}', { params: { path: { requestId: id } } })),
  });
}

export function useCreateRequest() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: EducationRequestCreate) =>
      unwrap(await api.POST('/api/v1/education/requests', { body })),
    onSuccess: (created) => {
      qc.setQueryData(queryKeys.education.detail(created.id), created);
      return qc.invalidateQueries({ queryKey: queryKeys.education.list });
    },
  });
}

/** Common invalidation after any change to one request. */
function useRefreshRequest(id: string) {
  const qc = useQueryClient();
  return (updated?: unknown) => {
    if (updated) qc.setQueryData(queryKeys.education.detail(id), updated);
    return Promise.all([
      qc.invalidateQueries({ queryKey: queryKeys.education.detail(id) }),
      qc.invalidateQueries({ queryKey: queryKeys.education.list }),
    ]);
  };
}

export function useUpdateRequest(id: string) {
  const refresh = useRefreshRequest(id);
  return useMutation({
    mutationFn: async (body: EducationRequestUpdate) =>
      unwrap(
        await api.PATCH('/api/v1/education/requests/{requestId}', { params: { path: { requestId: id } }, body }),
      ),
    onSuccess: (updated) => refresh(updated),
  });
}

export function useSubmitRequest(id: string) {
  const refresh = useRefreshRequest(id);
  return useMutation({
    mutationFn: async () =>
      unwrap(await api.POST('/api/v1/education/requests/{requestId}/submit', { params: { path: { requestId: id } } })),
    onSuccess: (updated) => refresh(updated),
  });
}

export function useWithdrawRequest(id: string) {
  const refresh = useRefreshRequest(id);
  return useMutation({
    mutationFn: async () =>
      unwrap(
        await api.POST('/api/v1/education/requests/{requestId}/withdraw', { params: { path: { requestId: id } } }),
      ),
    onSuccess: (updated) => refresh(updated),
  });
}

export function useDeleteDocument(requestId: string) {
  const refresh = useRefreshRequest(requestId);
  return useMutation({
    mutationFn: async (documentId: string) =>
      unwrap(
        await api.DELETE('/api/v1/education/requests/{requestId}/documents/{documentId}', {
          params: { path: { requestId, documentId } },
        }),
      ),
    onSuccess: () => refresh(),
  });
}

/** Re-exported so the uploader can refresh the request once a document completes. */
export { useRefreshRequest };
