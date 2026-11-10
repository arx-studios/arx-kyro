import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { NextConfig } from 'next';

const here = path.dirname(fileURLToPath(import.meta.url));
// Rewrites are evaluated at build time (see D-006): KYRO_API_ORIGIN is also a Docker build arg.
const apiOrigin = process.env.KYRO_API_ORIGIN ?? 'http://localhost:8080';

const nextConfig: NextConfig = {
  output: 'standalone',
  // Monorepo root, so the standalone server lands at apps/admin/server.js.
  outputFileTracingRoot: path.resolve(here, '../..'),
  transpilePackages: ['@kyro/design-system', '@kyro/api-client'],
  poweredByHeader: false,
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${apiOrigin}/api/:path*` }];
  },
  async headers() {
    return [
      {
        source: '/:path*',
        headers: [
          { key: 'Cache-Control', value: 'no-store' },
          { key: 'X-Frame-Options', value: 'DENY' },
          { key: 'Referrer-Policy', value: 'no-referrer' },
          { key: 'X-Content-Type-Options', value: 'nosniff' },
          { key: 'X-Robots-Tag', value: 'noindex, nofollow' },
        ],
      },
    ];
  },
};

export default nextConfig;
