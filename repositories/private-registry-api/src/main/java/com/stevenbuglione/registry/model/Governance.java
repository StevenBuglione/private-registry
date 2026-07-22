package com.stevenbuglione.registry.model;

import java.util.List;
import org.jspecify.annotations.Nullable;

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
    @Nullable String supportUrl,
    @Nullable String sourceRepositoryUrl,
    @Nullable String jfrogConsoleUrl) {}
