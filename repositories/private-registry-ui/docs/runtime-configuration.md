# Runtime configuration

The same immutable UI image is promoted through all environments. At container startup, `40-runtime-config.sh` writes `/usr/share/nginx/html/config/runtime.json` from non-secret environment variables.

The API base must be a same-origin path, normally `/api/v1`. The browser never receives OIDC client secrets, delegated tokens, JFrog credentials, signing material, group membership responses, or database endpoints.

Local Docker Compose uses `deploy/nginx/local.conf` to proxy API and OAuth routes to the Java service. Production routes those paths through the load balancer.
