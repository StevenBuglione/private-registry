package com.stevenbuglione.registry.security.identity;

import java.security.Principal;
import java.util.List;
import java.util.Set;

public record RegistryPrincipal(
        String subject,
        String displayName,
        String email,
        Set<String> roles,
        List<ApmEntitlement> entitlements) implements Principal {

    public RegistryPrincipal {
        roles = Set.copyOf(roles);
        entitlements = List.copyOf(entitlements);
    }

    @Override
    public String getName() {
        return subject;
    }

    public AccessContext accessContext() {
        return new AccessContext(
                subject,
                entitlements.stream().map(ApmEntitlement::apmId).collect(java.util.stream.Collectors.toUnmodifiableSet()),
                roles.contains("registry-admin"));
    }
}
