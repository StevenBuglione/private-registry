package com.stevenbuglione.registry.model;

import java.util.List;

public record Governance(
        String packageId,
        List<String> owners,
        String supportLevel,
        String lifecycle,
        String riskTier,
        String verification,
        List<Approval> approvals,
        String sourceAddress,
        String versionConstraint,
        String supportUrl,
        String sourceRepositoryUrl,
        String jfrogConsoleUrl) {}
