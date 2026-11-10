# syntax=docker/dockerfile:1
# KYRO web (Next.js standalone) image. Build context: repository root.
#   docker build -f infrastructure/docker/web.Dockerfile -t kyro-web .
#
# Requirements on apps/web/next.config.ts (WEB-1):
#   output: 'standalone' and outputFileTracingRoot set to the monorepo root, so the
#   standalone server lands at apps/web/server.js inside the standalone folder.
# Next evaluates `rewrites()` at BUILD time, so KYRO_API_ORIGIN is a build arg.

FROM node:24-alpine AS base
ENV NEXT_TELEMETRY_DISABLED=1 \
    PNPM_HOME=/pnpm
ENV PATH="${PNPM_HOME}:${PATH}"
RUN npm install -g pnpm@12.11.2
WORKDIR /repo

FROM base AS build
ARG KYRO_API_ORIGIN=http://api:8080
ENV KYRO_API_ORIGIN=${KYRO_API_ORIGIN}
COPY . .
RUN --mount=type=cache,id=pnpm-store,target=/pnpm/store \
    pnpm install --frozen-lockfile --filter "@kyro/web..." --store-dir /pnpm/store
RUN pnpm --filter "@kyro/web..." run build \
    && mkdir -p apps/web/public

FROM node:24-alpine AS runtime
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
WORKDIR /app
COPY --from=build --chown=node:node /repo/apps/web/.next/standalone ./
COPY --from=build --chown=node:node /repo/apps/web/.next/static ./apps/web/.next/static
COPY --from=build --chown=node:node /repo/apps/web/public ./apps/web/public
USER 1000:1000
EXPOSE 3000
CMD ["node", "apps/web/server.js"]
