import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'KYRO',
    short_name: 'KYRO',
    description: 'Verified needs. Meaningful impact. Sandbox demonstration with fictional data.',
    start_url: '/',
    display: 'standalone',
    background_color: '#faf8f4',
    theme_color: '#14213d',
    icons: [
      { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' },
      { src: '/icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'maskable' },
    ],
  };
}
