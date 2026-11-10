import type { Metadata } from 'next';
import { CaseDetailView } from '@/features/cases/CaseDetailView';

export const metadata: Metadata = { title: 'Case' };

export default async function CasePage({ params }: { params: Promise<{ caseId: string }> }) {
  const { caseId } = await params;
  return <CaseDetailView caseId={caseId} />;
}
