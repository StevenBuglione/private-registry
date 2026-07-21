# Patch strategy

Runtime bootstrapping and enterprise package-panel hooks are applied by `scripts/patch-upstream.py`. The script matches exact reviewed source fragments from the pinned upstream commit and fails closed when upstream changes.

Do not store a broad long-lived patch that obscures upstream changes. For every pin update:

1. review the upstream diff;
2. update the deterministic patch script intentionally;
3. record touched files in `PATCHES.md`;
4. rerun visual, accessibility, security, and compatibility tests.
