package com.stevenbuglione.registry.config;

import com.stevenbuglione.registry.security.identity.AlbAuthenticationFilter;
import com.stevenbuglione.registry.security.identity.IdentityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            IdentityProperties properties,
            AlbAuthenticationFilter albAuthenticationFilter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations) throws Exception {
        var csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        http.csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/internal/webhooks/**"))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.pathPattern("/api/**")))
                .addFilterBefore(albAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/health/**", "/actuator/health/**", "/actuator/info")
                            .permitAll();
                    authorize.requestMatchers("/oauth2/**", "/login/**").permitAll();
                    authorize.requestMatchers(HttpMethod.POST, "/internal/webhooks/jfrog").permitAll();
                    if (properties.permitAll()) {
                        authorize.anyRequest().permitAll();
                    } else {
                        authorize.anyRequest().authenticated();
                    }
                });
        if (clientRegistrations.getIfAvailable() != null) {
            http.oauth2Login(Customizer.withDefaults());
        }
        return http.build();
    }
}
