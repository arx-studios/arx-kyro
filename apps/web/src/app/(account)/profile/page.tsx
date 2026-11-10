'use client';

import { RequireAuth } from '@/components/RequireAuth';
import { ProfileForm } from '@/features/authentication/ProfileForm';
import { useMe } from '@/features/authentication/useMe';

function ProfileContent() {
  const { user } = useMe();
  if (!user) return null;
  return (
    <div className="mx-auto max-w-md px-4 py-10">
      <h1 className="mb-6 text-3xl font-bold text-navy-900">Your profile</h1>
      <ProfileForm user={user} />
    </div>
  );
}

export default function ProfilePage() {
  return (
    <RequireAuth>
      <ProfileContent />
    </RequireAuth>
  );
}
