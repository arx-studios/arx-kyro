import { createKyroClient } from '@kyro/api-client';

/** Same-origin client; requests are proxied to the API by the Next.js rewrite. */
export const api = createKyroClient();
