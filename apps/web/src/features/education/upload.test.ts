import { ApiError, type RequestDocument } from '@kyro/api-client';
import { describe, expect, it, vi } from 'vitest';
import {
  initialUploadState,
  runUpload,
  uploadReducer,
  validateFile,
  type PutFile,
  type UploadAction,
  type UploadState,
} from './upload';

const doc: RequestDocument = {
  id: 'd1',
  documentType: 'TUITION_INVOICE',
  originalFileName: 'bill.pdf',
  contentType: 'application/pdf',
  sizeBytes: 5,
  status: 'UPLOADED',
  createdAt: '2026-01-01T00:00:00Z',
  uploadedAt: '2026-01-01T00:00:01Z',
};

function file(name = 'bill.pdf', type = 'application/pdf', size = 5): File {
  const f = new File(['x'.repeat(Math.min(size, 10))], name, { type });
  if (size > 10) Object.defineProperty(f, 'size', { value: size });
  return f;
}

function fakeApi(opts: { requestFails?: boolean; completeFails?: boolean } = {}) {
  const POST = vi.fn(async (path: string) => {
    if (path.endsWith('/upload-url')) {
      if (opts.requestFails) {
        return {
          response: new Response(null, { status: 422 }),
          error: { type: 'x', title: 'bad', status: 422, code: 'DOCUMENT_INVALID', detail: 'Not allowed' },
        };
      }
      return {
        response: new Response(null, { status: 201 }),
        data: {
          document: { ...doc, status: 'PENDING_UPLOAD' },
          uploadUrl: 'http://storage.test/put',
          method: 'PUT',
          requiredHeaders: { 'Content-Type': 'application/pdf' },
          expiresAt: '2026-01-01T00:10:00Z',
        },
      };
    }
    if (opts.completeFails) {
      return { response: new Response(null, { status: 500 }), error: undefined };
    }
    return { response: new Response(null, { status: 200 }), data: doc };
  });
  return { POST } as unknown as Parameters<typeof runUpload>[2]['api'];
}

function collect() {
  const actions: UploadAction[] = [];
  let state: UploadState = initialUploadState;
  return {
    dispatch: (a: UploadAction) => {
      actions.push(a);
      state = uploadReducer(state, a);
    },
    get state() {
      return state;
    },
    actions,
  };
}

describe('validateFile', () => {
  it('accepts pdf/jpeg/png within 10 MB', () => {
    expect(validateFile({ name: 'a.pdf', type: 'application/pdf', size: 10 })).toBeNull();
    expect(validateFile({ name: 'a.png', type: 'image/png', size: 10_485_760 })).toBeNull();
  });
  it('rejects other types, empty and oversized files', () => {
    expect(validateFile({ name: 'a.exe', type: 'application/x-msdownload', size: 10 })).toMatch(/PDF/);
    expect(validateFile({ name: 'a.pdf', type: 'application/pdf', size: 0 })).toMatch(/empty/);
    expect(validateFile({ name: 'a.pdf', type: 'application/pdf', size: 10_485_761 })).toMatch(/10 MB/);
  });
});

describe('uploadReducer', () => {
  it('walks the happy path idle -> requesting -> uploading -> completing -> done', () => {
    let s = uploadReducer(initialUploadState, { type: 'start', fileName: 'bill.pdf' });
    expect(s.phase).toBe('requesting');
    s = uploadReducer(s, { type: 'authorized' });
    expect(s).toEqual({ phase: 'uploading', fileName: 'bill.pdf', progress: 0 });
    s = uploadReducer(s, { type: 'progress', progress: 42.4 });
    expect(s).toMatchObject({ phase: 'uploading', progress: 42 });
    s = uploadReducer(s, { type: 'uploaded' });
    expect(s.phase).toBe('completing');
    s = uploadReducer(s, { type: 'completed', document: doc });
    expect(s.phase).toBe('done');
  });

  it('ignores a second start while busy and out-of-order events', () => {
    const busy = uploadReducer(initialUploadState, { type: 'start', fileName: 'a.pdf' });
    expect(uploadReducer(busy, { type: 'start', fileName: 'b.pdf' })).toBe(busy);
    expect(uploadReducer(busy, { type: 'completed', document: doc })).toBe(busy);
    expect(uploadReducer(busy, { type: 'reset' })).toBe(busy);
    expect(uploadReducer(initialUploadState, { type: 'progress', progress: 50 })).toBe(initialUploadState);
  });

  it('moves to error from a busy phase and can be reset', () => {
    const busy = uploadReducer(initialUploadState, { type: 'start', fileName: 'a.pdf' });
    const err = uploadReducer(busy, { type: 'failed', stage: 'request', message: 'nope' });
    expect(err).toMatchObject({ phase: 'error', message: 'nope', stage: 'request' });
    expect(uploadReducer(err, { type: 'reset' })).toBe(initialUploadState);
  });
});

describe('runUpload (mocked client)', () => {
  it('uploads: request URL, PUT with required headers, complete', async () => {
    const api = fakeApi();
    const putFile = vi.fn<PutFile>(async (_u, _h, _f, onProgress) => onProgress(100));
    const c = collect();
    const result = await runUpload({ requestId: 'r1', documentType: 'TUITION_INVOICE', file: file() }, c.dispatch, {
      api,
      putFile,
    });
    expect(result).toEqual(doc);
    expect(c.state.phase).toBe('done');
    expect(putFile).toHaveBeenCalledWith(
      'http://storage.test/put',
      { 'Content-Type': 'application/pdf' },
      expect.any(File),
      expect.any(Function),
    );
  });

  it('fails fast on client validation without calling the API', async () => {
    const api = fakeApi();
    const c = collect();
    const result = await runUpload(
      { requestId: 'r1', documentType: 'OTHER', file: file('a.exe', 'application/x-msdownload') },
      c.dispatch,
      { api, putFile: vi.fn() },
    );
    expect(result).toBeNull();
    expect(c.state).toMatchObject({ phase: 'error', stage: 'validate' });
    expect((api as unknown as { POST: ReturnType<typeof vi.fn> }).POST).not.toHaveBeenCalled();
  });

  it('shows the server message when the upload URL is refused', async () => {
    const c = collect();
    await runUpload({ requestId: 'r1', documentType: 'OTHER', file: file() }, c.dispatch, {
      api: fakeApi({ requestFails: true }),
      putFile: vi.fn(),
    });
    expect(c.state).toMatchObject({ phase: 'error', stage: 'request', message: 'Not allowed' });
  });

  it('reports a storage failure without calling complete', async () => {
    const api = fakeApi();
    const c = collect();
    const result = await runUpload({ requestId: 'r1', documentType: 'OTHER', file: file() }, c.dispatch, {
      api,
      putFile: async () => {
        throw new Error('boom');
      },
    });
    expect(result).toBeNull();
    expect(c.state).toMatchObject({ phase: 'error', stage: 'upload' });
    expect((api as unknown as { POST: ReturnType<typeof vi.fn> }).POST).toHaveBeenCalledTimes(1);
  });

  it('reports a failed completion with a generic message for 5xx', async () => {
    const c = collect();
    await runUpload({ requestId: 'r1', documentType: 'OTHER', file: file() }, c.dispatch, {
      api: fakeApi({ completeFails: true }),
      putFile: async () => {},
    });
    expect(c.state).toMatchObject({ phase: 'error', stage: 'complete' });
  });

  it('never exposes the upload url in state', async () => {
    const c = collect();
    await runUpload({ requestId: 'r1', documentType: 'OTHER', file: file() }, c.dispatch, {
      api: fakeApi(),
      putFile: async () => {},
    });
    expect(JSON.stringify(c.state)).not.toContain('storage.test');
    expect(ApiError).toBeDefined();
  });
});
