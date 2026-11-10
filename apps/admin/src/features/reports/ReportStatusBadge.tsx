import type { ReportStatus } from '@kyro/api-client';
import { Badge, type BadgeTone } from '@kyro/design-system';

const STATUS: Record<ReportStatus, { label: string; tone: BadgeTone }> = {
  OPEN: { label: 'Open', tone: 'info' },
  IN_REVIEW: { label: 'In review', tone: 'accent' },
  RESOLVED: { label: 'Resolved', tone: 'neutral' },
};

export function ReportStatusBadge({ status }: { status: ReportStatus }) {
  const s = STATUS[status] ?? { label: status, tone: 'neutral' as BadgeTone };
  return <Badge tone={s.tone}>{s.label}</Badge>;
}
