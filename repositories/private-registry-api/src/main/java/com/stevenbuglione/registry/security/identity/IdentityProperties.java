package com.stevenbuglione.registry.security.identity;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("registry.security")
public record IdentityProperties(
        boolean permitAll,
        String allowedAlbSignerArn,
        String allowedOidcClientId,
        String allowedOidcIssuer,
        String albRegion,
        URI graphEndpoint,
        Duration graphTimeout,
        Duration membershipCacheTtl,
        String postLogoutRedirectUri,
        String apmGroupMappings,
        String adminGroupId) {

    public IdentityProperties {
        allowedAlbSignerArn = blankToEmpty(allowedAlbSignerArn);
        allowedOidcClientId = blankToEmpty(allowedOidcClientId);
        allowedOidcIssuer = blankToEmpty(allowedOidcIssuer);
        albRegion = blankToDefault(albRegion, "us-east-1");
        graphEndpoint = graphEndpoint == null
                ? URI.create("https://graph.microsoft.com/v1.0/me/checkMemberGroups")
                : graphEndpoint;
        graphTimeout = graphTimeout == null ? Duration.ofSeconds(5) : graphTimeout;
        membershipCacheTtl = membershipCacheTtl == null ? Duration.ofSeconds(60) : membershipCacheTtl;
        postLogoutRedirectUri = blankToDefault(postLogoutRedirectUri, "/");
        apmGroupMappings = blankToEmpty(apmGroupMappings);
        adminGroupId = blankToEmpty(adminGroupId);
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
