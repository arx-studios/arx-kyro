import type { Metadata } from 'next';
import { ReportDetailView } from '@/features/reports/ReportDetailView';

export const metadata: Metadata = { title: 'Report' };

export default async function ReportPage({ params }: { params: Promise<{ reportId: string }> }) {
  const { reportId } = await params;
  return <ReportDetailView reportId={reportId} />;
}
