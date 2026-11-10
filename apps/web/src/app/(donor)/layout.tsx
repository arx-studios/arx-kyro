import type { ReactNode } from 'react';

export default function DonorLayout({ children }: { children: ReactNode }) {
  return <div className="mx-auto w-full max-w-2xl px-4 py-8">{children}</div>;
}
