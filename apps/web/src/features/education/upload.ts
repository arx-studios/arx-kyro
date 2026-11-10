import {
  ApiError,
  unwrap,
  type DocumentContentType,
  type DocumentType,
  type RequestDocument,
} from '@kyro/api-client';
import type { api as ApiClient } from '@/lib/api';

export const MAX_FILE_BYTES = 10_485_760;
export const ALLOWED_CONTENT_TYPES: DocumentContentType[] = ['application/pdf', 'image/jpeg', 'image/png'];

export const DOCUMENT_TYPES: { value: DocumentType; label: string }[] = [
  { value: 'TUITION_INVOICE', label: 'Tuition invoice' },
  { value: 'FEE_STATEMENT', label: 'Fee statement' },
  { value: 'ENROLLMENT_PROOF', label: 'Enrollment proof' },
  { value: 'OTHER', label: 'Other' },
];

/** Client-side check only; the server validates again. Returns an error message or null. */
export function validateFile(file: { name: string; type: string; size: number }): string | null {
  if (!ALLOWED_CONTENT_TYPES.includes(file.type as DocumentContentType)) {
    return 'Only PDF, JPEG or PNG files are accepted.';
  }
  if (file.size < 1) return 'This file is empty.';
  if (file.size > MAX_FILE_BYTES) return 'Files must be 10 MB or smaller.';
  if (file.name.length > 200) return 'The file name is too long (200 characters at most).';
  return null;
}

/* ---------- state machine ---------- */

export type UploadState =
  | { phase: 'idle' }
  | { phase: 'requesting'; fileName: string }
  | { phase: 'uploading'; fileName: string; progress: number }
  | { phase: 'completing'; fileName: string }
  | { phase: 'done'; fileName: string; document: RequestDocument }
  | { phase: 'error'; fileName: string; message: string; stage: 'validate' | 'request' | 'upload' | 'complete' };

export type UploadAction =
  | { type: 'start'; fileName: string }
  | { type: 'validationFailed'; fileName: string; message: string }
  | { type: 'authorized' }
  | { type: 'progress'; progress: number }
  | { type: 'uploaded' }
  | { type: 'completed'; document: RequestDocument }
  | { type: 'failed'; stage: 'request' | 'upload' | 'complete'; message: string }
  | { type: 'reset' };

export const initialUploadState: UploadState = { phase: 'idle' };

export function isBusy(s: UploadState): boolean {
  return s.phase === 'requesting' || s.phase === 'uploading' || s.phase === 'completing';
}

/** Pure reducer; invalid transitions are ignored so late events cannot corrupt the state. */
export function uploadReducer(state: UploadState, action: UploadAction): UploadState {
  switch (action.type) {
    case 'start':
      return isBusy(state) ? state : { phase: 'requesting', fileName: action.fileName };
    case 'validationFailed':
      return isBusy(state) ? state : { phase: 'error', fileName: action.fileName, message: action.message, stage: 'validate' };
    case 'authorized':
      return state.phase === 'requesting' ? { phase: 'uploading', fileName: state.fileName, progress: 0 } : state;
    case 'progress':
      return state.phase === 'uploading'
        ? { ...state, progress: Math.max(0, Math.min(100, Math.round(action.progress))) }
        : state;
    case 'uploaded':
      return state.phase === 'uploading' ? { phase: 'completing', fileName: state.fileName } : state;
    case 'completed':
      return state.phase === 'completing' ? { phase: 'done', fileName: state.fileName, document: action.document } : state;
    case 'failed':
      return state.phase === 'requesting' || state.phase === 'uploading' || state.phase === 'completing'
        ? { phase: 'error', fileName: state.fileName, message: action.message, stage: action.stage }
        : state;
    case 'reset':
      return isBusy(state) ? state : initialUploadState;
  }
}

/* ---------- orchestration ---------- */

/** PUTs the file to the presigned URL. Default implementation uses XHR to report upload progress. */
export type PutFile = (
  url: string,
  headers: Record<string, string>,
  file: Blob,
  onProgress: (percent: number) => void,
) => Promise<void>;

export const xhrPutFile: PutFile = (url, headers, file, onProgress) =>
  new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', url);
    for (const [k, v] of Object.entries(headers)) xhr.setRequestHeader(k, v);
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress((e.loaded / e.total) * 100);
    };
    xhr.onload = () =>
      xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`Upload failed (HTTP ${xhr.status}).`));
    xhr.onerror = () => reject(new Error('Network error while uploading.'));
    xhr.onabort = () => reject(new Error('Upload cancelled.'));
    xhr.send(file);
  });

export interface RunUploadDeps {
  api: typeof ApiClient;
  putFile?: PutFile;
}

function apiMessage(err: unknown, fallback: string): string {
  return err instanceof ApiError && err.status < 500 ? err.message : fallback;
}

/**
 * Full upload flow: validate, request an upload URL, PUT the bytes to storage, mark complete.
 * Reports each step through `dispatch`. Returns the completed document, or null on failure.
 * The presigned URL is used in memory only and is never rendered or stored.
 */
export async function runUpload(
  args: { requestId: string; documentType: DocumentType; file: File },
  dispatch: (a: UploadAction) => void,
  deps: RunUploadDeps,
): Promise<RequestDocument | null> {
  const { requestId, documentType, file } = args;
  const putFile = deps.putFile ?? xhrPutFile;
  const invalid = validateFile(file);
  if (invalid) {
    dispatch({ type: 'validationFailed', fileName: file.name, message: invalid });
    return null;
  }
  dispatch({ type: 'start', fileName: file.name });

  let authorization;
  try {
    authorization = unwrap(
      await deps.api.POST('/api/v1/education/requests/{requestId}/documents/upload-url', {
        params: { path: { requestId } },
        body: {
          documentType,
          fileName: file.name,
          contentType: file.type as DocumentContentType,
          sizeBytes: file.size,
        },
      }),
    );
  } catch (err) {
    dispatch({ type: 'failed', stage: 'request', message: apiMessage(err, 'Could not start the upload. Please try again.') });
    return null;
  }
  dispatch({ type: 'authorized' });

  try {
    await putFile(authorization.uploadUrl, authorization.requiredHeaders, file, (p) =>
      dispatch({ type: 'progress', progress: p }),
    );
  } catch {
    dispatch({ type: 'failed', stage: 'upload', message: 'The file could not be uploaded. Check your connection and try again.' });
    return null;
  }
  dispatch({ type: 'uploaded' });

  try {
    const document = unwrap(
      await deps.api.POST('/api/v1/education/requests/{requestId}/documents/{documentId}/complete', {
        params: { path: { requestId, documentId: authorization.document.id } },
      }),
    );
    dispatch({ type: 'completed', document });
    return document;
  } catch (err) {
    dispatch({ type: 'failed', stage: 'complete', message: apiMessage(err, 'The upload could not be confirmed. Please try again.') });
    return null;
  }
}
