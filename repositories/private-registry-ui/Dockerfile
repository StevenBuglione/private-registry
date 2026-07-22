# syntax=docker/dockerfile:1.7
FROM alpine:3.22 AS source
RUN apk add --no-cache git python3
WORKDIR /source
ARG OPEN_TOFU_REPOSITORY=https://github.com/opentofu/registry-ui.git
ARG OPEN_TOFU_COMMIT=dada44d53ae664f2ac2ace573e1175730541aeb7
RUN mkdir -p /tmp/upstream app \
    && git -C /tmp/upstream init -q \
    && git -C /tmp/upstream remote add origin "${OPEN_TOFU_REPOSITORY}" \
    && git -C /tmp/upstream fetch --depth 1 origin "${OPEN_TOFU_COMMIT}" \
    && git -C /tmp/upstream checkout -q --detach FETCH_HEAD \
    && test "$(git -C /tmp/upstream rev-parse HEAD)" = "${OPEN_TOFU_COMMIT}" \
    && test -d /tmp/upstream/frontend \
    && cp -a /tmp/upstream/frontend/. app/ \
    && cp /tmp/upstream/LICENSE app/UPSTREAM_LICENSE
COPY scripts/patch-upstream.py scripts/patch-upstream.py
COPY overlays/src/enterprise/ overlays/src/enterprise/
RUN mkdir -p app/src/enterprise \
    && cp -a overlays/src/enterprise/. app/src/enterprise/ \
    && python3 scripts/patch-upstream.py

FROM node:22-bookworm-slim AS build
WORKDIR /workspace
RUN corepack enable
COPY --from=source /source/app/package.json /source/app/pnpm-lock.yaml ./
RUN --mount=type=cache,target=/root/.local/share/pnpm/store pnpm install --frozen-lockfile
COPY --from=source /source/app/ ./
ARG VITE_BUILD_SHA=unknown
ENV VITE_BUILD_SHA=${VITE_BUILD_SHA}
RUN pnpm build

FROM nginx:1.27-alpine
RUN apk add --no-cache curl gettext \
    && mkdir -p /usr/share/nginx/html/config /opt/private-registry \
    && chown -R nginx:nginx /usr/share/nginx/html /opt/private-registry
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY deploy/nginx/runtime.json.template /opt/private-registry/runtime.json.template
COPY deploy/docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /workspace/dist/ /usr/share/nginx/html/
RUN chmod 0755 /docker-entrypoint.d/40-runtime-config.sh \
    && chown -R nginx:nginx /usr/share/nginx/html /opt/private-registry
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/healthz || exit 1
