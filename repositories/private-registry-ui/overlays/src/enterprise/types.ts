export type SupportLevel =
  | "supported"
  | "maintenance"
  | "experimental"
  | "deprecated"
  | "revoked"
  | "archived";

export type Lifecycle =
  | "draft"
  | "candidate"
  | "approved"
  | "maintenance"
  | "deprecated"
  | "revoked"
  | "archived";

export type EnterprisePackageMetadata = {
  packageId: string;
  owners: string[];
  supportLevel: SupportLevel;
  lifecycle: Lifecycle;
  riskTier: "low" | "medium" | "high" | "critical";
  verification: "enterprise-verified" | "security-reviewed" | "unverified";
  approvals: Array<{ type: string; status: string; decidedAt?: string }>;
  sourceAddress?: string;
  versionConstraint?: string;
  supportUrl?: string;
  sourceRepositoryUrl?: string;
  jfrogConsoleUrl?: string;
};
