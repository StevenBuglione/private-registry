INSERT INTO teams (id, display_name, support_url) VALUES
    ('cloud-network-engineering', 'Cloud Network Engineering', 'https://support.example.invalid/cloud-network'),
    ('cloud-platform-engineering', 'Cloud Platform Engineering', 'https://support.example.invalid/cloud-platform');

INSERT INTO packages (
    id, kind, namespace, name, target, title, description, source_address,
    visibility, risk_tier, verification, support_level, lifecycle, created_at, updated_at
) VALUES
    (
        '11111111-1111-1111-1111-111111111111', 'module', 'cloud-platform', 'vpc', 'aws',
        'AWS VPC', 'Creates an approved multi-AZ application VPC with enterprise network defaults.',
        'artifacts.example.invalid/iac-modules-virtual__cloud-platform/vpc/aws',
        'enterprise', 'medium', 'enterprise-verified', 'supported', 'approved',
        '2026-07-21T14:00:00Z', '2026-07-21T14:00:00Z'
    ),
    (
        '22222222-2222-2222-2222-222222222222', 'provider', 'platform', 'internal-cloud', '',
        'Internal Cloud Provider', 'Manages approved internal cloud platform APIs.',
        'artifacts.example.invalid/iac-providers-virtual-rt-ns-platform/internal-cloud',
        'enterprise', 'high', 'enterprise-verified', 'supported', 'approved',
        '2026-07-21T14:00:00Z', '2026-07-21T14:00:00Z'
    );

INSERT INTO package_owners (package_id, team_id, owner_order) VALUES
    ('11111111-1111-1111-1111-111111111111', 'cloud-network-engineering', 0),
    ('22222222-2222-2222-2222-222222222222', 'cloud-platform-engineering', 0);

INSERT INTO package_versions (
    id, package_id, version, package_digest, documentation_digest, documentation_root,
    artifact_repository, artifact_path, source_repository, source_commit, source_tag,
    terraform_constraint, opentofu_constraint, published_at
) VALUES
    (
        '11111111-1111-1111-1111-111111111101', '11111111-1111-1111-1111-111111111111', '2.4.1',
        'sha256:0000000000000000000000000000000000000000000000000000000000000000',
        'sha256:1111111111111111111111111111111111111111111111111111111111111111',
        'fixtures/modules/vpc/2.4.1', 'iac-modules-virtual', 'cloud-platform/vpc/aws/2.4.1',
        'https://git.example.invalid/cloud-platform/vpc', '8d08f7f', 'v2.4.1', '>= 1.6', '>= 1.8',
        '2026-07-21T14:00:00Z'
    ),
    (
        '22222222-2222-2222-2222-222222222202', '22222222-2222-2222-2222-222222222222', '3.2.0',
        'sha256:2222222222222222222222222222222222222222222222222222222222222222',
        'sha256:3333333333333333333333333333333333333333333333333333333333333333',
        'fixtures/providers/internal-cloud/3.2.0', 'iac-providers-virtual', 'platform/internal-cloud/3.2.0',
        'https://git.example.invalid/cloud-platform/internal-cloud-provider', '9d08f7f', 'v3.2.0',
        '>= 1.6', '>= 1.8', '2026-07-21T14:00:00Z'
    );

INSERT INTO approvals (
    package_version_id, approval_type, decision, decided_by, decided_at,
    policy_version, justification, evidence_s3_uri
) VALUES
    ('11111111-1111-1111-1111-111111111101', 'architecture', 'approved', 'architecture-review', '2026-07-21T14:00:00Z', '1', 'Local fixture approval', 's3://fixture/architecture.json'),
    ('11111111-1111-1111-1111-111111111101', 'security', 'approved', 'security-review', '2026-07-21T14:00:00Z', '1', 'Local fixture approval', 's3://fixture/security.json'),
    ('22222222-2222-2222-2222-222222222202', 'architecture', 'approved', 'architecture-review', '2026-07-21T14:00:00Z', '1', 'Local fixture approval', 's3://fixture/architecture.json'),
    ('22222222-2222-2222-2222-222222222202', 'security', 'approved', 'security-review', '2026-07-21T14:00:00Z', '1', 'Local fixture approval', 's3://fixture/security.json');

INSERT INTO symbols (package_version_id, kind, name, description, document_path) VALUES
    ('11111111-1111-1111-1111-111111111101', 'input', 'cidr_block', 'IPv4 CIDR range.', '#inputs'),
    ('11111111-1111-1111-1111-111111111101', 'output', 'vpc_id', 'Created VPC ID.', '#outputs'),
    ('22222222-2222-2222-2222-222222222202', 'resource', 'internal_cloud_network', 'Manages a network.', 'resources/internal_cloud_network');

INSERT INTO documentation_pages (
    package_version_id, path, title, content_type, s3_key, digest, size_bytes, content
) VALUES
    (
        '11111111-1111-1111-1111-111111111101', 'README.md', 'AWS VPC', 'text/markdown; charset=utf-8',
        'fixtures/modules/vpc/2.4.1/README.md',
        'sha256:4444444444444444444444444444444444444444444444444444444444444444', 81,
        E'# AWS VPC\n\nFixture documentation for local compatibility development.\n'
    ),
    (
        '22222222-2222-2222-2222-222222222202', 'index.md', 'Internal Cloud Provider', 'text/markdown; charset=utf-8',
        'fixtures/providers/internal-cloud/3.2.0/index.md',
        'sha256:5555555555555555555555555555555555555555555555555555555555555555', 71,
        E'# Internal Cloud Provider\n\nFixture provider documentation.\n'
    ),
    (
        '22222222-2222-2222-2222-222222222202', 'resources/internal_cloud_network.md', 'internal_cloud_network', 'text/markdown; charset=utf-8',
        'fixtures/providers/internal-cloud/3.2.0/resources/internal_cloud_network.md',
        'sha256:6666666666666666666666666666666666666666666666666666666666666666', 72,
        E'# internal_cloud_network\n\nManages an internal cloud network.\n'
    );
