import createClient, { type Client, type Middleware } from 'openapi-fetch';
import type { paths } from './schema';
import type { Problem } from './types';

export type { paths, components, operations } from './schema';
export * from './types';

/** RFC 9457 problem body returned by the KYRO API. */
export type KyroProblem = Problem;

export const CSRF_COOKIE_NAME = 'XSRF-TOKEN';
export const CSRF_HEADER_NAME = 'X-XSRF-TOKEN';
export const CORRELATION_HEADER_NAME = 'X-Correlation-Id';
const CSRF_ENDPOINT = '/api/v1/auth/csrf';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export function isProblem(x: unknown): x is KyroProblem {
  if (typeof x !== 'object' || x === null) return false;
  const o = x as Record<string, unknown>;
  return typeof o.code === 'string' && typeof o.title === 'string' && typeof o.status === 'number';
}

export class ApiError extends Error {
  readonly problem: KyroProblem;
  readonly status: number;
  readonly code: string;
  readonly correlationId?: string;

  constructor(problem: KyroProblem) {
    super(problem.detail ?? problem.title);
    this.name = 'ApiError';
    this.problem = problem;
    this.status = problem.status;
    this.code = problem.code;
    this.correlationId = problem.correlationId;
  }
}

export interface KyroClientOptions {
  /** Origin prefix for requests. Defaults to '' (same-origin, via the Next.js rewrite). */
  baseUrl?: string;
  /** Custom fetch (tests, SSR). Defaults to the global fetch. */
  fetch?: typeof fetch;
  /** Cookie reader override (tests, SSR). Defaults to document.cookie. */
  getCookie?: (name: string) => string | undefined;
  /** Correlation id generator. Defaults to crypto.randomUUID(). */
  generateCorrelationId?: () => string;
}

export type KyroClient = Client<paths>;

function defaultGetCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined;
  for (const part of document.cookie.split(';')) {
    const idx = part.indexOf('=');
    if (idx < 0) continue;
    if (part.slice(0, idx).trim() === name) {
      return decodeURIComponent(part.slice(idx + 1).trim());
    }
  }
  return undefined;
}

function defaultCorrelationId(): string {
  return globalThis.crypto.randomUUID();
}

export function createKyroClient(options: KyroClientOptions = {}): KyroClient {
  const baseUrl = options.baseUrl ?? '';
  const fetchImpl: typeof fetch = options.fetch ?? ((...args) => globalThis.fetch(...args));
  const getCookie = options.getCookie ?? defaultGetCookie;
  const newCorrelationId = options.generateCorrelationId ?? defaultCorrelationId;

  let bodyToken: string | undefined;
  let inflight: Promise<string | undefined> | undefined;

  async function primeCsrf(): Promise<string | undefined> {
    inflight ??= (async () => {
      try {
        const res = await fetchImpl(`${baseUrl}${CSRF_ENDPOINT}`, {
          method: 'GET',
          credentials: 'include',
          headers: { Accept: 'application/json', [CORRELATION_HEADER_NAME]: newCorrelationId() },
        });
        if (!res.ok) return undefined;
        const body = (await res.json()) as { token?: string };
        bodyToken = body.token;
        return getCookie(CSRF_COOKIE_NAME) ?? bodyToken;
      } finally {
        inflight = undefined;
      }
    })();
    return inflight;
  }

  const middleware: Middleware = {
    async onRequest({ request }) {
      if (!request.headers.has(CORRELATION_HEADER_NAME)) {
        request.headers.set(CORRELATION_HEADER_NAME, newCorrelationId());
      }
      if (!SAFE_METHODS.has(request.method.toUpperCase())) {
        const token = getCookie(CSRF_COOKIE_NAME) ?? (await primeCsrf()) ?? bodyToken;
        if (token) request.headers.set(CSRF_HEADER_NAME, token);
      }
      return request;
    },
  };

  const client = createClient<paths>({ baseUrl, credentials: 'include', fetch: fetchImpl });
  client.use(middleware);
  return client;
}

type FetchResult<T> = { data?: T; error?: unknown; response: Response };

/**
 * Returns `data` or throws `ApiError`. For 204 responses `data` is undefined and the call
 * succeeds. A non-problem error body is wrapped in a synthetic problem.
 */
export function unwrap<T>(res: FetchResult<T>): T {
  if (res.response.ok) return res.data as T;
  if (isProblem(res.error)) throw new ApiError(res.error);
  const status = res.response.status;
  throw new ApiError({
    type: 'about:blank',
    title: res.response.statusText || 'Request failed',
    status,
    code: status >= 500 ? 'INTERNAL_ERROR' : 'MALFORMED_REQUEST',
  });
}
