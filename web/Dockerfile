# Bun installs and builds; the final stage runs the adapter-node output on Node.
# `docker compose up --watch` uses the `dev` stage (Vite with hot reload).

FROM oven/bun:1.3.11-slim AS deps
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

FROM deps AS dev
COPY . .
EXPOSE 5173
CMD ["bun", "run", "dev"]

FROM deps AS build
COPY . .
# `$env/static/public` inlines this at build time, so changing it needs a rebuild.
ARG PUBLIC_SITE_URL=https://shuuen.xyz
ENV PUBLIC_SITE_URL=$PUBLIC_SITE_URL
RUN bun run build

FROM node:24-alpine AS prod
WORKDIR /app
ENV NODE_ENV=production HOST=0.0.0.0 PORT=3000
# adapter-node bundles the devDependencies into build/, so no node_modules is
# needed. package.json only supplies `"type": "module"`.
COPY --from=build /app/package.json ./
COPY --from=build /app/build ./build
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --start-interval=1s --retries=3 \
    CMD wget -qO- http://127.0.0.1:3000/ >/dev/null || exit 1
CMD ["node", "build"]
