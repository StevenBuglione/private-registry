# Runtime configuration

The same immutable UI image is promoted through all environments. At container startup, `40-runtime-config.sh` writes `/usr/share/nginx/html/config/runtime.json` from non-secret ECS environment variables.

`REGISTRY_DATA_API_URL` must normally be `/registry/docs/` with a trailing slash because the upstream UI requests paths such as `modules/index.json` relative to that prefix. `REGISTRY_ENTERPRISE_API_URL` is normally `/api/v1/enterprise`.

No credential, account identifier, signing material, database endpoint, or JFrog token belongs in runtime JSON. Browser authorization is enforced by the API; feature flags are presentation controls only.
