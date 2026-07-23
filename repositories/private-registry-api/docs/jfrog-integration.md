# JFrog integration contract

JFrog remains the only package data plane. The catalog API and browser do not proxy module/provider archives.

Required repository groups:

```text
modules:   candidate-local -> release-local -> virtual (+ approved remote)
providers: candidate-local -> release-local -> virtual (+ network mirror where required)
catalog:   candidate-local -> release-local -> virtual
```

The combined API/worker identity is read-only and restricted to approved release/catalog repositories. Publishing and promotion identities are separate. A promoted event includes repository, normalized package identity, version, package digest, documentation digest/path, source commit, event ID, and timestamp.

The JFrog client must:

- use private routing and a short-lived/read-only credential where supported;
- reject unexpected repository keys and unsafe paths;
- verify package and documentation digests;
- enforce maximum size/file-count/decompression limits;
- retrieve evidence but never return package bytes to browsers;
- surface retryable versus terminal errors without logging secrets.

Public provider source identities remain unchanged and use mirror behavior. Internal providers use the private hostname only after verifying origin-registry support and GPG requirements in the deployed JFrog version.
