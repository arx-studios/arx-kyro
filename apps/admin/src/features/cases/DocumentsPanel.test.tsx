import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RequestDocument } from '@kyro/api-client';

const post = vi.fn();
vi.mock('@/lib/api', () => ({ api: { POST: (...args: unknown[]) => post(...args) } }));

import { DocumentsPanel } from './DocumentsPanel';

const doc: RequestDocument = {
  id: 'd1',
  documentType: 'TUITION_INVOICE',
  originalFileName: 'invoice.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  status: 'UPLOADED',
  createdAt: '2026-11-01T10:00:00Z',
  uploadedAt: '2026-11-01T10:01:00Z',
};

describe('DocumentsPanel', () => {
  beforeEach(() => {
    post.mockReset();
    vi.restoreAllMocks();
  });

  it('requests a view URL and opens it in a new tab without opener, persisting nothing', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    post.mockResolvedValue({
      data: { url: 'http://minio.local/signed?sig=abc', expiresAt: '2026-11-10T10:05:00Z' },
      response: new Response(null, { status: 200 }),
    });
    const user = userEvent.setup();
    render(<DocumentsPanel caseId="c1" documents={[doc]} />);
    expect(screen.getByText(/access .* logged/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /View invoice\.pdf/ }));

    await waitFor(() =>
      expect(open).toHaveBeenCalledWith('http://minio.local/signed?sig=abc', '_blank', 'noopener'),
    );
    expect(post).toHaveBeenCalledWith(
      '/api/v1/admin/verification/cases/{caseId}/documents/{documentId}/view-url',
      { params: { path: { caseId: 'c1', documentId: 'd1' } } },
    );
    expect(document.body.innerHTML).not.toContain('signed?sig');
    expect(JSON.stringify(window.localStorage)).not.toContain('signed');
    expect(JSON.stringify(window.sessionStorage)).not.toContain('signed');
  });

  it('shows an alert when the URL cannot be issued', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    post.mockResolvedValue({
      error: { type: 't', title: 'Forbidden', status: 403, code: 'FORBIDDEN' },
      response: new Response(null, { status: 403 }),
    });
    const user = userEvent.setup();
    render(<DocumentsPanel caseId="c1" documents={[doc]} />);
    await user.click(screen.getByRole('button', { name: /View invoice\.pdf/ }));
    expect(await screen.findByText('Not permitted')).toBeInTheDocument();
    expect(open).not.toHaveBeenCalled();
  });
});
