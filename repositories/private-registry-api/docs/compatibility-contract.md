# OpenTofu UI compatibility contract

The internal domain model and enterprise API are not the UI compatibility contract. The exact compatibility contract is the pinned upstream OpenAPI document.

Run in a network-enabled intake branch:

```bash
./scripts/sync-opentofu-contract.sh
```

This writes `api/opentofu-compatibility-upstream.yaml` from the commit recorded in `contracts/upstream/OPEN_TOFU_COMMIT`. Commit that generated file after review. Generate response DTOs and contract tests from it, then keep internal Aurora/JFrog models behind an explicit translation layer.

Required rules:

- module lists return the upstream `ModuleList` shape (`modules`);
- provider lists return the upstream `ProviderList` shape (`providers`);
- package and version endpoints are distinct;
- version metadata preserves upstream required fields and omission/null behavior;
- search and `/top/providers` match the pinned frontend's expected response;
- enterprise-only fields stay under `/api/v1/enterprise` instead of modifying standard DTOs;
- every pin change reruns captured UI route tests before merge.

The starter handlers are intentionally small fixtures. Do not treat their current domain JSON as production compatibility evidence.
