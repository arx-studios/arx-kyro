'use client';

import { Alert, Button, Field, Select } from '@kyro/design-system';
import type { DocumentType } from '@kyro/api-client';
import { useReducer, useRef, useState } from 'react';
import { api } from '@/lib/api';
import { useRefreshRequest } from './hooks';
import {
  ALLOWED_CONTENT_TYPES,
  DOCUMENT_TYPES,
  initialUploadState,
  isBusy,
  runUpload,
  uploadReducer,
} from './upload';

/** Document uploader for an editable request. Never displays storage keys or upload URLs. */
export function DocumentUploader({ requestId }: { requestId: string }) {
  const [state, dispatch] = useReducer(uploadReducer, initialUploadState);
  const [documentType, setDocumentType] = useState<DocumentType>('TUITION_INVOICE');
  const fileInput = useRef<HTMLInputElement>(null);
  const refresh = useRefreshRequest(requestId);
  const busy = isBusy(state);

  async function onFile(file: File | undefined) {
    if (!file) return;
    const doc = await runUpload({ requestId, documentType, file }, dispatch, { api });
    // Refresh also on failure after the document row exists (e.g. complete failed) so the list stays truthful.
    await refresh();
    if (doc && fileInput.current) fileInput.current.value = '';
  }

  return (
    <div className="flex flex-col gap-4 rounded-lg border border-warm-300 bg-white p-4">
      <Field label="Document type">
        {(p) => (
          <Select
            {...p}
            value={documentType}
            disabled={busy}
            onChange={(e) => setDocumentType(e.target.value as DocumentType)}
          >
            {DOCUMENT_TYPES.map((t) => (
              <option key={t.value} value={t.value}>
                {t.label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field label="Choose a file" hint="PDF, JPEG or PNG, up to 10 MB. Only the KYRO verification team can see it.">
        {(p) => (
          <input
            {...p}
            ref={fileInput}
            type="file"
            accept={ALLOWED_CONTENT_TYPES.join(',')}
            disabled={busy}
            onChange={(e) => void onFile(e.target.files?.[0])}
            className="block w-full min-h-11 rounded-md border border-charcoal-600 bg-white p-2 text-sm file:mr-3 file:min-h-9 file:rounded-md file:border-0 file:bg-navy-100 file:px-3 file:text-navy-900"
          />
        )}
      </Field>

      <div aria-live="polite" className="flex flex-col gap-2">
        {state.phase === 'requesting' && <p>Preparing upload of {state.fileName}...</p>}
        {state.phase === 'uploading' && (
          <div>
            <label htmlFor="upload-progress" className="text-sm text-charcoal-700">
              Uploading {state.fileName}: {state.progress}%
            </label>
            <progress id="upload-progress" className="mt-1 block h-2 w-full" max={100} value={state.progress} />
          </div>
        )}
        {state.phase === 'completing' && <p>Finishing upload of {state.fileName}...</p>}
        {state.phase === 'done' && <Alert tone="success">{state.fileName} was uploaded.</Alert>}
        {state.phase === 'error' && (
          <Alert tone="danger" title={`Could not upload ${state.fileName}`}>
            {state.message}
          </Alert>
        )}
      </div>
      {state.phase === 'error' || state.phase === 'done' ? (
        <div>
          <Button variant="ghost" onClick={() => dispatch({ type: 'reset' })}>
            Dismiss
          </Button>
        </div>
      ) : null}
    </div>
  );
}
