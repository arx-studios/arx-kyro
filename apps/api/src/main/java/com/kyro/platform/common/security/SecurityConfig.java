package com.kyro.platform.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;

/**
 * Session-cookie security (PLAN section 5). CSRF is on for every mutating request (SPA cookie + header);
 * unauthenticated -> 401 problem, denied -> 403 problem; no redirects.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            RateLimiter rateLimiter,
            ProblemResponseWriter problems)
            throws Exception {
        http.securityContext(c -> c.securityContextRepository(securityContextRepository))
                .csrf(c -> c.spa())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(c -> c.disable())
                .cors(Customizer.withDefaults())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                problems.write(req, res, 401, "UNAUTHENTICATED", "Authentication is required."))
                        .accessDeniedHandler((req, res, ex) -> {
                            if (ex instanceof CsrfException) {
                                problems.write(req, res, 403, "CSRF_INVALID", "Missing or invalid CSRF token.");
                            } else {
                                problems.write(
                                        req, res, 403, "FORBIDDEN", "You do not have permission to perform this action.");
                            }
                        }))
                .addFilterBefore(new RateLimitFilter(rateLimiter, problems), CsrfFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/verify-email/resend",
                                "/api/v1/auth/session")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/current")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/discovery/feed", "/api/v1/discovery/feed/*")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/api/v1/admin/**")
                        .hasAnyRole("VERIFIER", "ADMIN")
                        .requestMatchers("/api/v1/education/**", "/api/v1/me/education/**")
                        .hasRole("RECIPIENT")
                        .requestMatchers("/api/v1/discovery/requests/*/swipes", "/api/v1/discovery/saved/**")
                        .hasRole("DONOR")
                        .anyRequest()
                        .authenticated());
        return http.build();
    }
}
