import { describe, expect, it, vi } from 'vitest';
import { ApiError, createKyroClient, isProblem, unwrap } from '../src/index';

const BASE = 'http://localhost:8080';

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

function reqOf(call: unknown[]): Request {
  const first = call[0];
  return first instanceof Request ? first : new Request(first as string, call[1] as RequestInit);
}

describe('createKyroClient', () => {
  it('adds a correlation id and does not fetch a CSRF token for GET requests', async () => {
    const fetchMock = vi.fn(async () => json({ termsVersion: 'a', privacyVersion: 'b' }));
    const client = createKyroClient({ baseUrl: BASE, fetch: fetchMock, generateCorrelationId: () => 'corr-1' });
    await client.GET('/api/v1/policies/current');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const req = reqOf(fetchMock.mock.calls[0] as unknown[]);
    expect(req.headers.get('X-Correlation-Id')).toBe('corr-1');
    expect(req.headers.has('X-XSRF-TOKEN')).toBe(false);
    expect(req.credentials).toBe('include');
  });

  it('uses the XSRF-TOKEN cookie on mutating requests without priming', async () => {
    const fetchMock = vi.fn(async () => new Response(null, { status: 204 }));
    const client = createKyroClient({
      baseUrl: BASE,
      fetch: fetchMock,
      getCookie: (n) => (n === 'XSRF-TOKEN' ? 'cookie-token' : undefined),
    });
    await client.DELETE('/api/v1/auth/session');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const req = reqOf(fetchMock.mock.calls[0] as unknown[]);
    expect(req.method).toBe('DELETE');
    expect(req.headers.get('X-XSRF-TOKEN')).toBe('cookie-token');
  });

  it('primes CSRF via GET /api/v1/auth/csrf when the cookie is missing, then sets the header', async () => {
    let cookie: string | undefined;
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const req = input instanceof Request ? input : new Request(input as string, init);
      if (new URL(req.url, 'http://x').pathname === '/api/v1/auth/csrf') {
        cookie = 'primed-token';
        return json({ headerName: 'X-XSRF-TOKEN', token: 'primed-token' });
      }
      return new Response(null, { status: 204 });
    });
    const client = createKyroClient({
      baseUrl: BASE,
      fetch: fetchMock as unknown as typeof fetch,
      getCookie: () => cookie,
    });
    await client.POST('/api/v1/auth/verify-email', { body: { token: 't' } });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const first = reqOf(fetchMock.mock.calls[0] as unknown[]);
    expect(new URL(first.url).pathname).toBe('/api/v1/auth/csrf');
    expect(first.method).toBe('GET');
    const second = reqOf(fetchMock.mock.calls[1] as unknown[]);
    expect(second.method).toBe('POST');
    expect(second.headers.get('X-XSRF-TOKEN')).toBe('primed-token');
  });

  it('falls back to the token in the CSRF response body when the cookie is unreadable', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const req = input instanceof Request ? input : new Request(input as string, init);
      if (req.url.endsWith('/api/v1/auth/csrf')) {
        return json({ headerName: 'X-XSRF-TOKEN', token: 'body-token' });
      }
      return new Response(null, { status: 204 });
    });
    const client = createKyroClient({
      baseUrl: BASE,
      fetch: fetchMock as unknown as typeof fetch,
      getCookie: () => undefined,
    });
    await client.DELETE('/api/v1/auth/session');
    const last = reqOf(fetchMock.mock.calls[1] as unknown[]);
    expect(last.headers.get('X-XSRF-TOKEN')).toBe('body-token');
  });
});

describe('unwrap', () => {
  it('returns data on success', async () => {
    const fetchMock = vi.fn(async () => json({ termsVersion: 'a', privacyVersion: 'b' }));
    const client = createKyroClient({ baseUrl: BASE, fetch: fetchMock });
    const data = unwrap(await client.GET('/api/v1/policies/current'));
    expect(data).toEqual({ termsVersion: 'a', privacyVersion: 'b' });
  });

  it('throws ApiError carrying the Problem on error responses', async () => {
    const problem = {
      type: 'urn:kyro:problem:NOT_FOUND',
      title: 'Not found',
      status: 404,
      detail: 'No such request.',
      code: 'NOT_FOUND',
      correlationId: 'c-9',
    };
    const fetchMock = vi.fn(async () => json(problem, 404));
    const client = createKyroClient({ baseUrl: BASE, fetch: fetchMock });
    const res = await client.GET('/api/v1/discovery/feed/{requestId}', {
      params: { path: { requestId: '00000000-0000-0000-0000-000000000000' } },
    });
    let caught: unknown;
    try {
      unwrap(res);
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(ApiError);
    const err = caught as ApiError;
    expect(err.status).toBe(404);
    expect(err.code).toBe('NOT_FOUND');
    expect(err.correlationId).toBe('c-9');
    expect(err.problem).toEqual(problem);
    expect(err.message).toBe('No such request.');
  });

  it('wraps non-problem error bodies in a synthetic problem', () => {
    const response = new Response('boom', { status: 502, statusText: 'Bad Gateway' });
    expect(() => unwrap({ error: 'boom', response })).toThrowError(ApiError);
    try {
      unwrap({ error: 'boom', response });
    } catch (e) {
      expect((e as ApiError).code).toBe('INTERNAL_ERROR');
      expect((e as ApiError).status).toBe(502);
    }
  });

  it('isProblem recognises problem bodies only', () => {
    expect(isProblem({ type: 't', title: 'x', status: 400, code: 'C' })).toBe(true);
    expect(isProblem({ title: 'x' })).toBe(false);
    expect(isProblem(null)).toBe(false);
  });
});
