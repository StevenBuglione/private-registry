INSERT INTO packages (
    id, kind, namespace, name, target, title, description, source_address,
    visibility, risk_tier, verification, support_level, lifecycle, created_at, updated_at
) VALUES
    (
        '33333333-3333-3333-3333-333333333333', 'module', 'cloudposse', 'label', 'null',
        'Cloud Posse Terraform Null Label',
        'Generates consistent names and tags from the module resolved through the private Artifactory cache.',
        'trialwbgt07.jfrog.io/iac-modules-public-remote__cloudposse/label/null',
        'enterprise', 'low', 'security-reviewed', 'supported', 'approved',
        '2026-07-21T18:00:00Z', '2026-07-21T18:00:00Z'
    ),
    (
        '44444444-4444-4444-4444-444444444444', 'provider', 'hashicorp', 'null', '',
        'HashiCorp Null Provider',
        'Provider binary resolved exclusively through the private Artifactory network mirror.',
        'hashicorp/null',
        'enterprise', 'low', 'security-reviewed', 'supported', 'approved',
        '2026-07-21T18:00:00Z', '2026-07-21T18:00:00Z'
    );

INSERT INTO package_owners (package_id, team_id, owner_order) VALUES
    ('33333333-3333-3333-3333-333333333333', 'cloud-platform-engineering', 0),
    ('44444444-4444-4444-4444-444444444444', 'cloud-platform-engineering', 0);

INSERT INTO package_versions (
    id, package_id, version, package_digest, documentation_digest, documentation_root,
    artifact_repository, artifact_path, source_repository, source_commit, source_tag,
    terraform_constraint, opentofu_constraint, published_at
) VALUES
    (
        '33333333-3333-3333-3333-333333333301', '33333333-3333-3333-3333-333333333333', '0.25.0',
        'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
        'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
        'fixtures/modules/cloudposse-label/0.25.0', 'iac-modules-public-remote-cache',
        'cloudposse/terraform-null-label/0.25.0', 'https://github.com/cloudposse/terraform-null-label',
        '8b8b5c0', '0.25.0', '>= 1.0', '>= 1.6', '2026-07-21T18:00:00Z'
    ),
    (
        '44444444-4444-4444-4444-444444444402', '44444444-4444-4444-4444-444444444444', '3.3.0',
        'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
        'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
        'fixtures/providers/hashicorp-null/3.3.0', 'iac-providers-public-remote-cache',
        'terraform-provider-null/3.3.0', 'https://github.com/hashicorp/terraform-provider-null',
        '3.3.0', 'v3.3.0', '>= 1.0', '>= 1.6', '2026-07-21T18:00:00Z'
    );

INSERT INTO approvals (
    package_version_id, approval_type, decision, decided_by, decided_at,
    policy_version, justification, evidence_s3_uri
) VALUES
    ('33333333-3333-3333-3333-333333333301', 'security', 'approved', 'mirror-proof', '2026-07-21T18:00:00Z', '1', 'Resolved through Artifactory', 's3://fixture/cloudposse-label-security.json'),
    ('44444444-4444-4444-4444-444444444402', 'security', 'approved', 'mirror-proof', '2026-07-21T18:00:00Z', '1', 'Resolved through Artifactory', 's3://fixture/hashicorp-null-security.json');

INSERT INTO symbols (package_version_id, kind, name, description, document_path) VALUES
    ('33333333-3333-3333-3333-333333333301', 'input', 'name', 'Workload name used to build the label.', '#inputs'),
    ('33333333-3333-3333-3333-333333333301', 'output', 'id', 'Generated normalized label.', '#outputs');

INSERT INTO documentation_pages (
    package_version_id, path, title, content_type, s3_key, digest, size_bytes, content
) VALUES
    (
        '33333333-3333-3333-3333-333333333301', 'README.md', 'Cloud Posse Terraform Null Label',
        'text/markdown; charset=utf-8', 'fixtures/modules/cloudposse-label/0.25.0/README.md',
        'sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee', 164,
        E'# Cloud Posse Terraform Null Label\n\nVersion 0.25.0 was resolved through the private Artifactory module cache.\n'
    ),
    (
        '44444444-4444-4444-4444-444444444402', 'index.md', 'HashiCorp Null Provider',
        'text/markdown; charset=utf-8', 'fixtures/providers/hashicorp-null/3.3.0/index.md',
        'sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff', 148,
        E'# HashiCorp Null Provider\n\nVersion 3.3.0 was resolved through the private Artifactory provider network mirror.\n'
    );
