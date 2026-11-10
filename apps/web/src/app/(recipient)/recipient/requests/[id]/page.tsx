'use client';

import { useParams } from 'next/navigation';
import { RequestDetail } from '@/features/education/RequestDetail';

export default function RequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  return <RequestDetail id={id} />;
}
