# syntax=docker/dockerfile:1
# Base image: node:22-alpine, dipin ke digest (dicek 2026-11-22). Perbarui digest dengan sengaja, bukan otomatis.
ARG NODE_IMAGE=node:22-alpine@sha256:1314fb30ec37592d5845e5a160d522c0e680972aa534549940b8d73bde88481a
FROM ${NODE_IMAGE} AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM ${NODE_IMAGE} AS build
WORKDIR /app
# next.config.ts membekukan rewrite /api/* saat build, jadi BACKEND_URL wajib ada di sini.
ARG BACKEND_URL
ENV BACKEND_URL=${BACKEND_URL} NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM ${NODE_IMAGE} AS runner
WORKDIR /app
ARG BACKEND_URL
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 HOSTNAME=0.0.0.0 PORT=3000 BACKEND_URL=${BACKEND_URL}
RUN addgroup -S app && adduser -S -G app -H -s /sbin/nologin app
COPY --from=build --chown=app:app /app/public ./public
COPY --from=build --chown=app:app /app/.next/standalone ./
COPY --from=build --chown=app:app /app/.next/static ./.next/static
USER app
EXPOSE 3000
CMD ["node", "server.js"]
