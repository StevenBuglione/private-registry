export const providerTierOptions = [
  {
    value: "official",
    label: "Official",
    description: "Official providers are owned and maintained by HashiCorp.",
  },
  {
    value: "partner",
    label: "Partner",
    description:
      "Partner providers are owned and maintained by a technology company that has gone through the partner onboarding process and maintain a direct partnership with HashiCorp.",
  },
  {
    value: "partner-premier",
    label: "Partner Premier",
    description:
      "Technology partners are third-party companies that write and maintain partner premier providers. To earn a partner premier badge, the partner must qualify",
  },
  {
    value: "community",
    label: "Community",
    description:
      "Community providers are published and maintained by individual contributors of the ecosystem.",
  },
] as const;

export const providerCategoryOptions = [
  ["asset-management", "Asset Management"],
  ["cloud-automation", "Cloud Automation"],
  ["communication-messaging", "Communication & Messaging"],
  ["container-orchestration", "Container Orchestration"],
  ["ci-cd", "Continuous Integration/Deployment (CI/CD)"],
  ["data-management", "Data Management"],
  ["database", "Database"],
  ["infrastructure", "Infrastructure (IaaS)"],
  ["logging-monitoring", "Logging & Monitoring"],
  ["networking", "Networking"],
  ["platform", "Platform (PaaS)"],
  ["security-authentication", "Security & Authentication"],
  ["utility", "Utility"],
  ["vcs", "VCS (Version Control)"],
  ["web-services", "Web Services"],
  ["hashicorp-platform", "HashiCorp Platform"],
  ["infrastructure-management", "Infrastructure Management"],
  ["public-cloud", "Public Cloud"],
] as const;

export const moduleProviderOptions = [
  ["alibaba", "Alibaba"],
  ["aws", "AWS"],
  ["azurerm", "Azure"],
  ["boundary", "Boundary"],
  ["consul", "Consul"],
  ["google", "Google"],
  ["helm", "Helm"],
  ["nomad", "Nomad"],
  ["oci", "Oracle"],
  ["vault", "Vault"],
] as const;
