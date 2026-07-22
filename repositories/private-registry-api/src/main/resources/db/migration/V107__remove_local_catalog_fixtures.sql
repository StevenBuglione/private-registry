-- The local V100/V101 fixtures made the starter browsable before a real
-- Artifactory seed existed. The Compose acceptance path now bootstraps the
-- governed catalog, so retain no synthetic packages or versions at runtime.
-- Exact fixture UUIDs make this cleanup safe and idempotent in every profile.
DELETE FROM packages
 WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444'
 );
