import type { Metadata } from 'next';
import { DiscoverFeed } from '@/features/discovery/DiscoverFeed';

export const metadata: Metadata = { title: 'Discover verified requests · KYRO' };

export default function DiscoverPage() {
  return (
    <>
      <h1 className="mb-1 text-2xl font-semibold text-navy-900">Verified requests</h1>
      <p className="mb-6 text-charcoal-700">
        Every request here was reviewed by the KYRO verification team. Sandbox: no real money moves.
      </p>
      <DiscoverFeed />
    </>
  );
}
