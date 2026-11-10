import path from 'node:path';
import { fileURLToPath } from 'node:url';
import type { NextConfig } from 'next';

const here = path.dirname(fileURLToPath(import.meta.url));
// Monorepo root: the standalone server then lands at apps/web/server.js.
const monorepoRoot = path.resolve(here, '../..');

// Resolved at BUILD time by Next (see D-006): also pass it as a Docker build arg.
const apiOrigin = (process.env.KYRO_API_ORIGIN ?? 'http://localhost:8080').replace(/\/+$/, '');

const config: NextConfig = {
  output: 'standalone',
  outputFileTracingRoot: monorepoRoot,
  turbopack: { root: monorepoRoot },
  transpilePackages: ['@kyro/design-system', '@kyro/api-client'],
  poweredByHeader: false,
  async rewrites() {
    return [{ source: '/api/:path*', destination: `${apiOrigin}/api/:path*` }];
  },
};

export default config;
