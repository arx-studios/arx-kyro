/** TanStack Query key factory. WEB-2 consumes the `discovery` keys. */
export const queryKeys = {
  me: ['me'] as const,
  policies: ['policies', 'current'] as const,
  education: {
    all: ['education'] as const,
    list: ['education', 'list'] as const,
    detail: (id: string) => ['education', 'detail', id] as const,
  },
  discovery: {
    all: ['discovery'] as const,
    feed: ['discovery', 'feed'] as const,
    detail: (id: string) => ['discovery', 'detail', id] as const,
    saved: ['discovery', 'saved'] as const,
  },
};
