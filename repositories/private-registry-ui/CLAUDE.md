# Claude Instructions — UI Repository

1. Read `README.md`, `UPSTREAM.md`, and `PATCHES.md`.
2. Confirm `.upstream/OPEN_TOFU_COMMIT` is still the approved baseline. Do not silently change it.
3. Run `scripts/import-upstream.sh`; inspect imported notices and licenses.
4. Run `scripts/apply-overlays.sh`.
5. Integrate the overlay entry points into the imported React application while keeping enterprise code under `app/src/enterprise/`.
6. Replace upstream public branding and submission/community links. Do not use OpenTofu trademarks without approval.
7. Keep standard module/provider pages on the compatibility API. Use `/api/v1/enterprise` only for governance extensions.
8. Treat ALB-authenticated identity as untrusted input until the API validates it; the UI only controls presentation.
9. Build and test the Nginx image locally.
10. Use the GitHub Actions release workflow with GitHub OIDC to push an immutable image to ECR and update the ECS service.
11. Never put JFrog tokens, AWS credentials, OIDC client secrets, or database values in runtime JSON.
12. Record every divergence from upstream in `PATCHES.md` and preserve attribution.

The UI is not the authorization boundary. Protected data must be filtered by the API.
