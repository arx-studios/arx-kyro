'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { unwrap, type LoginRequest, type RegisterRequest, type UpdateProfileRequest } from '@kyro/api-client';
import { api } from '@/lib/api';
import { queryKeys } from '@/lib/queryKeys';

export function usePolicies() {
  return useQuery({
    queryKey: queryKeys.policies,
    queryFn: async () => unwrap(await api.GET('/api/v1/policies/current')),
    staleTime: 5 * 60_000,
  });
}

export function useRegister() {
  return useMutation({
    mutationFn: async (body: RegisterRequest) => unwrap(await api.POST('/api/v1/auth/register', { body })),
  });
}

export function useVerifyEmail() {
  return useMutation({
    mutationFn: async (token: string) => unwrap(await api.POST('/api/v1/auth/verify-email', { body: { token } })),
  });
}

export function useResendVerification() {
  return useMutation({
    mutationFn: async (email: string) =>
      unwrap(await api.POST('/api/v1/auth/verify-email/resend', { body: { email } })),
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: LoginRequest) => unwrap(await api.POST('/api/v1/auth/session', { body })),
    onSuccess: (user) => {
      qc.setQueryData(queryKeys.me, user);
    },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async () => unwrap(await api.DELETE('/api/v1/auth/session')),
    onSettled: () => {
      // Drop everything user-specific; me becomes "signed out".
      qc.clear();
      qc.setQueryData(queryKeys.me, null);
    },
  });
}

export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateProfileRequest) => unwrap(await api.PATCH('/api/v1/me/profile', { body })),
    onSuccess: (user) => {
      qc.setQueryData(queryKeys.me, user);
    },
  });
}
