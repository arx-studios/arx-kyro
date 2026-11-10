import type { Metadata, Viewport } from 'next';
import '@fontsource-variable/inter';
import type { ReactNode } from 'react';
import { AppShell } from '@/components/AppShell';
import { Providers } from '@/components/Providers';
import './globals.css';


export const metadata: Metadata = {
  title: { default: 'KYRO - Verified needs. Meaningful impact.', template: '%s | KYRO' },
  description:
    'KYRO connects donors with verified education expenses. Sandbox demonstration: fictional data, no real money.',
  applicationName: 'KYRO',
};

export const viewport: Viewport = {
  themeColor: '#14213d',
  width: 'device-width',
  initialScale: 1,
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <Providers>
          <AppShell>{children}</AppShell>
        </Providers>
      </body>
    </html>
  );
}
