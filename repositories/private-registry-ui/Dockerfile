# syntax=docker/dockerfile:1.7
FROM node:22-bookworm-slim AS build
WORKDIR /workspace
RUN corepack enable
COPY web/package.json web/pnpm-lock.yaml ./web/
WORKDIR /workspace/web
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    SKIP_INSTALL_SIMPLE_GIT_HOOKS=1 pnpm install --frozen-lockfile
COPY web/ ./
COPY public/ ../public/
ARG VITE_BUILD_SHA=unknown
ENV VITE_BUILD_SHA=${VITE_BUILD_SHA}
RUN pnpm build

FROM nginx:1.29-alpine
ARG NGINX_CONFIG=deploy/nginx/default.conf
RUN apk upgrade --no-cache \
    && apk add --no-cache curl gettext \
    && mkdir -p /usr/share/nginx/html/config /opt/registry \
    && chown -R nginx:nginx /usr/share/nginx/html /opt/registry
COPY ${NGINX_CONFIG} /etc/nginx/conf.d/default.conf
COPY deploy/nginx/runtime.json.template /opt/registry/runtime.json.template
COPY deploy/docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /workspace/web/dist/ /usr/share/nginx/html/
RUN chmod 0755 /docker-entrypoint.d/40-runtime-config.sh \
    && chown -R nginx:nginx /usr/share/nginx/html /opt/registry
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1
