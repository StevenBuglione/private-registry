# Upstream management

## Source

- Repository: `https://github.com/opentofu/registry-ui`
- Approved commit: read `.upstream/OPEN_TOFU_COMMIT`
- Imported component: `frontend/` only
- Backend components are intentionally excluded.

## Intake procedure

1. Create a branch named `upstream/<date>-<short-sha>`.
2. Update the pinned SHA only after reviewing upstream changes since the current baseline.
3. Run dependency, malware, secret, and license scans.
4. Run `scripts/import-upstream.sh` in a clean worktree.
5. Run `scripts/apply-overlays.sh` and resolve integration conflicts intentionally.
6. Update `PATCHES.md` with affected files and rationale.
7. Run unit, API contract, visual, accessibility, CSP, and end-to-end tests.
8. Obtain required open-source/legal and security approvals.
9. Create an internal semantic version and release notes.

## Update policy

- Review quarterly and immediately for relevant security fixes.
- Do not auto-merge upstream.
- Pin production to an internal release and immutable container digest.
- Keep a provenance record with upstream SHA, import timestamp, dependency lock digest, build SHA, and ECR digest.

## Licensing and trademarks

The upstream frontend and its dependencies retain their own licenses. Import scripts copy upstream license/notice material. A production fork must complete legal review and comply with source-availability and attribution obligations. No trademark rights are assumed.
