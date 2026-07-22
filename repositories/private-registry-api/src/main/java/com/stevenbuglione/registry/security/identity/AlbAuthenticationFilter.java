package com.stevenbuglione.registry.security.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AlbAuthenticationFilter extends OncePerRequestFilter {

  static final String IDENTITY_DATA_HEADER = "x-amzn-oidc-data";
  static final String IDENTITY_HEADER = "x-amzn-oidc-identity";
  static final String ACCESS_TOKEN_HEADER = "x-amzn-oidc-accesstoken";

  private final AlbTokenVerifier verifier;
  private final RegistryIdentityService identities;
  private final SecurityContextHolderStrategy securityContextHolder =
      SecurityContextHolder.getContextHolderStrategy();

  public AlbAuthenticationFilter(AlbTokenVerifier verifier, RegistryIdentityService identities) {
    this.verifier = verifier;
    this.identities = identities;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var identityData = request.getHeader(IDENTITY_DATA_HEADER);
    var identity = request.getHeader(IDENTITY_HEADER);
    var accessToken = request.getHeader(ACCESS_TOKEN_HEADER);
    if (isBlank(identityData) && isBlank(identity) && isBlank(accessToken)) {
      filterChain.doFilter(request, response);
      return;
    }
    if (isBlank(identityData) || isBlank(identity) || isBlank(accessToken)) {
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Incomplete ALB OIDC identity headers");
      return;
    }

    try {
      var verified = verifier.verify(identityData, identity);
      var principal = identities.fromAlb(verified, accessToken);
      var authorities =
          principal.roles().stream()
              .map(role -> role.toUpperCase(java.util.Locale.ROOT).replace('-', '_'))
              .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
              .toList();
      var authentication = new PreAuthenticatedAuthenticationToken(principal, "", authorities);
      securityContextHolder.setContext(new SecurityContextImpl(authentication));
      filterChain.doFilter(request, response);
    } catch (IdentityProviderUnavailableException exception) {
      securityContextHolder.clearContext();
      response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "Identity provider unavailable");
    } catch (AuthenticationException exception) {
      securityContextHolder.clearContext();
      response.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid ALB OIDC identity");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
