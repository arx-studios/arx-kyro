'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useCreateRequest } from '@/features/education/hooks';
import { RequestForm } from '@/features/education/RequestForm';
import { toCreatePayload } from '@/features/education/schemas';

export default function NewRequestPage() {
  const router = useRouter();
  const create = useCreateRequest();

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link href="/recipient/dashboard" className="inline-flex min-h-11 items-center text-sm underline">
          Back to my requests
        </Link>
        <h1 className="text-3xl font-bold text-navy-900">New request</h1>
        <p className="mt-2 text-charcoal-700">
          Start with the basics. This is saved as a draft; you add documents and submit it for verification on the next
          page.
        </p>
      </div>
      <RequestForm
        submitLabel="Save draft and continue"
        onSubmit={async (values) => {
          const created = await create.mutateAsync(toCreatePayload(values));
          router.push(`/recipient/requests/${created.id}`);
        }}
      />
    </div>
  );
}
