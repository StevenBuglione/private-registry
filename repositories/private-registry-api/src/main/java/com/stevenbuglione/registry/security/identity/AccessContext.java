package com.stevenbuglione.registry.security.identity;

import java.util.Set;

public record AccessContext(String subject, Set<String> apmIds, boolean registryAdmin) {

    public AccessContext {
        apmIds = Set.copyOf(apmIds);
    }

    public static AccessContext localAdministrator() {
        return new AccessContext("local-development", Set.of(), true);
    }

    public boolean mayUseApm(String apmId) {
        return registryAdmin || apmIds.contains(apmId);
    }

    public AccessContext scopedToApm(String apmId) {
        if (apmId == null || apmId.isBlank()) {
            return this;
        }
        var selectedApmId = apmId.trim();
        return mayUseApm(selectedApmId)
                ? new AccessContext(subject, Set.of(selectedApmId), false)
                : new AccessContext(subject, Set.of(), false);
    }
}
