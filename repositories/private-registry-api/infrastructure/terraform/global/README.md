# Global and cross-Region stack

Apply only after the primary and DR regional stacks exist. It configures ECR replication, selected S3 bucket replication, and Route 53 private failover records. Validate destination KMS/bucket policies before applying.

Private Route 53 failover must be tested from corporate resolvers. If your health model cannot evaluate an internal ALB across Regions, replace this record strategy with the enterprise DNS/traffic-management standard and documented operator-controlled failover.
