import { createKyroClient } from '@kyro/api-client';

/**
 * The single API client for the web app. Same-origin (`/api/...` is proxied by the Next rewrite
 * in next.config.ts), session cookie + CSRF header handled by the client. Browser use only.
 */
export const api = createKyroClient();
