import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import '@fontsource-variable/inter';
import './globals.css';
import { Providers } from './providers';

export const metadata: Metadata = {
  title: { default: 'KYRO Operations', template: '%s | KYRO Operations' },
  description: 'KYRO verification console (sandbox, fictional data).',
  robots: { index: false, follow: false },
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
