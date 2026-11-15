package com.sixeyes.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final SessionFilter sessionFilter;

    /** API routes carry this prefix (added in WebConfig, not by a servlet context path,
     *  so the SPA can live at the origin root). Matchers see the full path. */
    @Value("${api.prefix:/api/v1}")
    private String apiPrefix;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF is covered by SameSite=Strict on the session cookie: a cross-site
                // request never carries it. Re-enable CookieCsrfTokenRepository here if
                // SameSite is ever relaxed to Lax.
                .csrf(AbstractHttpConfigurer::disable)
                // No CORS config: the SPA is served by this application, so every API call
                // is same-origin.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                apiPrefix + "/auth/login",
                                apiPrefix + "/auth/logout",
                                apiPrefix + "/torrents/test").permitAll()
                        // Everything else under the API prefix, device approval included.
                        .requestMatchers(apiPrefix + "/**").authenticated()
                        // The SPA shell itself is public static content; it holds no data.
                        .anyRequest().permitAll()
                )
                // Without an entry point Spring answers 403 for anonymous requests, which
                // the dashboard cannot tell apart from a genuine permission failure. 401
                // means "no session" - that is what drives the redirect to the login page.
                // Spring already sends nosniff, X-Frame-Options and HSTS. The CSP is what
                // stops an injected script from running on the login page at all.
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "script-src 'self'",
                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
                                "font-src 'self' data: https://fonts.gstatic.com",
                                "img-src 'self' data: https:",
                                "connect-src 'self'",
                                "object-src 'none'",
                                "base-uri 'self'",
                                "form-action 'self'",
                                "frame-ancestors 'none'")))
                        .referrerPolicy(r -> r.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(e -> e.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(sessionFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
