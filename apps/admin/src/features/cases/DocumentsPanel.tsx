'use client';

import { useState } from 'react';
import { unwrap, type RequestDocument } from '@kyro/api-client';
import { Alert, Badge, Button, DateText, EmptyState } from '@kyro/design-system';
import { api } from '@/lib/api';
import { describeError, type FriendlyProblem } from '@/lib/problems';

const TYPE_LABELS: Record<RequestDocument['documentType'], string> = {
  TUITION_INVOICE: 'Tuition invoice',
  FEE_STATEMENT: 'Fee statement',
  ENROLLMENT_PROOF: 'Enrollment proof',
  OTHER: 'Other',
};

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Private documents are only reachable through a short-lived view URL issued by the API (and
 * audited server-side). The URL is used once, straight from the response: it is never kept in
 * state, in the query cache or in storage.
 */
export function DocumentsPanel({ caseId, documents }: { caseId: string; documents: RequestDocument[] }) {
  const [busyId, setBusyId] = useState<string | null>(null);
  const [problem, setProblem] = useState<FriendlyProblem | null>(null);

  async function view(doc: RequestDocument) {
    setBusyId(doc.id);
    setProblem(null);
    try {
      const { url } = unwrap(
        await api.POST('/api/v1/admin/verification/cases/{caseId}/documents/{documentId}/view-url', {
          params: { path: { caseId, documentId: doc.id } },
        }),
      );
      window.open(url, '_blank', 'noopener');
    } catch (err) {
      setProblem(describeError(err));
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section aria-labelledby="documents-heading" className="space-y-3">
      <h2 id="documents-heading" className="text-lg font-semibold text-navy-900">
        Documents
      </h2>
      <p className="text-sm text-charcoal-700">
        Each time you open a document, a short-lived link is issued and the access is logged against your account.
      </p>
      {problem ? (
        <Alert tone={problem.tone} title={problem.title}>
          <p>{problem.message}</p>
        </Alert>
      ) : null}
      {documents.length === 0 ? (
        <EmptyState title="No documents" description="The recipient has not attached any documents." />
      ) : (
        <ul className="divide-y divide-warm-200 rounded-lg border border-warm-200 bg-white">
          {documents.map((doc) => (
            <li key={doc.id} className="flex flex-wrap items-center justify-between gap-3 p-3">
              <div className="min-w-0">
                <p className="break-all text-sm font-medium">{doc.originalFileName}</p>
                <p className="text-xs text-charcoal-700">
                  {TYPE_LABELS[doc.documentType]} · {doc.contentType} · {formatBytes(doc.sizeBytes)}
                  {doc.uploadedAt ? (
                    <>
                      {' '}
                      · uploaded <DateText value={doc.uploadedAt} withTime />
                    </>
                  ) : null}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <Badge tone={doc.status === 'UPLOADED' ? 'success' : 'neutral'}>{doc.status.replace('_', ' ')}</Badge>
                <Button
                  variant="secondary"
                  disabled={doc.status !== 'UPLOADED'}
                  loading={busyId === doc.id}
                  onClick={() => view(doc)}
                  aria-label={`View ${doc.originalFileName} (opens in a new tab, access is logged)`}
                >
                  View
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
