import type { Metadata } from 'next';
import { RequestDetailView } from '@/features/discovery/RequestDetailView';

export const metadata: Metadata = { title: 'Request details · KYRO' };

export default async function RequestDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <RequestDetailView id={id} />;
}
