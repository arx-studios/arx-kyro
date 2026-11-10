'use client';

import { RequireAuth } from '@/components/RequireAuth';
import { MyPledges } from '@/features/funding/MyPledges';

export default function PledgesPage() {
  return (
    <RequireAuth roles={['DONOR']}>
      <h1 className="mb-6 text-2xl font-semibold text-navy-900">My pledges</h1>
      <MyPledges />
    </RequireAuth>
  );
}
