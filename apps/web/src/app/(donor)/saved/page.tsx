'use client';

import { RequireAuth } from '@/components/RequireAuth';
import { SavedList } from '@/features/discovery/SavedList';

export default function SavedPage() {
  return (
    <RequireAuth roles={['DONOR']}>
      <h1 className="mb-6 text-2xl font-semibold text-navy-900">Saved requests</h1>
      <SavedList />
    </RequireAuth>
  );
}
