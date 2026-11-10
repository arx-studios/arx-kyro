'use client';

import { useMutation } from '@tanstack/react-query';
import { unwrap } from '@kyro/api-client';
import { api } from '@/lib/api';

export function useRequestPasswordReset() {
  return useMutation({
    mutationFn: async (email: string) => unwrap(await api.POST('/api/v1/auth/password-reset', { body: { email } })),
  });
}

export function useConfirmPasswordReset() {
  return useMutation({
    mutationFn: async (body: { token: string; newPassword: string }) =>
      unwrap(await api.POST('/api/v1/auth/password-reset/confirm', { body })),
  });
}

export function useChangePassword() {
  return useMutation({
    mutationFn: async (body: { currentPassword: string; newPassword: string }) =>
      unwrap(await api.POST('/api/v1/me/password', { body })),
  });
}
