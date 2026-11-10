export type QueueName = 'UNASSIGNED' | 'MINE' | 'CLOSED' | 'ALL';

export const queryKeys = {
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
