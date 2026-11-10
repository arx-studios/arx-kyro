export type QueueName = 'UNASSIGNED' | 'MINE' | 'CLOSED' | 'ALL';

export type ReportQueueName = 'OPEN' | 'MINE' | 'RESOLVED' | 'ALL';

export const queryKeys = {
  dashboard: ['dashboard'] as const,
  reports: {
    all: ['reports'] as const,
    list: (queue: ReportQueueName) => ['reports', 'list', queue] as const,
    detail: (reportId: string) => ['reports', 'detail', reportId] as const,
  },
  me: ['me'] as const,
  cases: {
    all: ['cases'] as const,
    list: (queue: QueueName) => ['cases', 'list', queue] as const,
    detail: (caseId: string) => ['cases', 'detail', caseId] as const,
  },
  audit: {
    list: (entityId: string) => ['audit', 'list', entityId] as const,
  },
};
