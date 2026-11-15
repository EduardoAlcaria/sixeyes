package com.sixeyes.config;

import com.sixeyes.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates from the session cookie. Replaces the old Bearer-token filter: the token
 * is no longer readable by JavaScript, so an XSS in the dashboard cannot lift it.
 */
@Component
@RequiredArgsConstructor
public class SessionFilter extends OncePerRequestFilter {

    private final SessionService sessionService;

    @Value("${session.cookie-name:sixeyes_session}")
    private String cookieName;

    @Value("${cloudflare.access.require-assertion:false}")
    private boolean requireAccessAssertion;

    /** Set by Cloudflare Access on every request it forwards. */
    private static final String CF_ASSERTION_HEADER = "Cf-Access-Jwt-Assertion";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Refuse anything that did not come through Access. Off by default: while the
        // origin is bound to loopback, only this machine can reach it at all, and a local
        // attacker has already won. Turn on if server.address is widened past loopback.
        if (requireAccessAssertion && request.getHeader(CF_ASSERTION_HEADER) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Cloudflare Access required");
            return;
        }

        readCookie(request)
                .flatMap(sessionService::validate)
                .ifPresent(session -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            session.getUsername(), null, List.of());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });

        chain.doFilter(request, response);
    }

    private Optional<String> readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }
}
