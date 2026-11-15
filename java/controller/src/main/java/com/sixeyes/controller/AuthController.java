package com.sixeyes.controller;

import com.sixeyes.dto.request.LoginRequest;
import com.sixeyes.model.TrustedDevice;
import com.sixeyes.service.DeviceService;
import com.sixeyes.service.LoginRateLimiter;
import com.sixeyes.service.Secrets;
import com.sixeyes.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SessionService sessionService;
    private final DeviceService deviceService;
    private final LoginRateLimiter rateLimiter;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${session.cookie-name:sixeyes_session}")
    private String cookieName;

    @Value("${session.cookie-secure:true}")
    private boolean cookieSecure;

    @Value("${session.absolute-timeout-days:30}")
    private long absoluteTimeoutDays;

    /** The client generates this once and keeps it; it identifies the browser, not the user. */
    private static final String DEVICE_HEADER = "X-SixEyes-Device-Id";

    private static final long FAILURE_DELAY_MS = 1000;
    private static final String DEFAULT_PASSWORD = "admin";

    public record MeResponse(String username) {
    }

    public record DeviceResponse(Long id, String label, String status, Instant createdAt,
                                 Instant lastSeenAt, boolean current) {
        static DeviceResponse of(TrustedDevice d, String currentHash) {
            return new DeviceResponse(d.getId(), d.getLabel(), d.getStatus().name(),
                    d.getCreatedAt(), d.getLastSeenAt(), d.getDeviceHash().equals(currentHash));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId,
                                   @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                   HttpServletRequest http) {

        // The yml fallback is admin/admin; never accept it on an internet-facing login.
        if (DEFAULT_PASSWORD.equals(adminPassword)) {
            log.error("Login refused: ADMIN_PASSWORD is unset (default credentials)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Server is not configured. Set ADMIN_PASSWORD."));
        }

        String clientKey = clientIp(http);
        if (rateLimiter.isBlocked(clientKey)) {
            log.warn("Login blocked (rate limit) from {}", clientKey);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Too many attempts. Try again later."));
        }

        // Evaluate both before branching so a wrong username costs the same as a wrong password.
        boolean validUsername = Secrets.constantTimeEquals(request.username(), adminUsername);
        boolean validPassword = isPasswordMatch(request.password(), adminPassword);

        if (!validUsername || !validPassword) {
            rateLimiter.recordFailure(clientKey);
            log.warn("Failed login for '{}' from {}",
                    request.username().replaceAll("\\p{Cntrl}", "_"), clientKey);
            slowDown();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        rateLimiter.recordSuccess(clientKey);

        if (deviceId == null || deviceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Missing device identifier"));
        }

        // Correct password is not sufficient: the browser must also be one you approved.
        TrustedDevice device = deviceService.resolve(deviceId, userAgent);
        if (device.getStatus() != TrustedDevice.Status.TRUSTED) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "code", "PENDING_APPROVAL",
                    "message", "This device is awaiting approval. Approve it from a device "
                            + "you have already signed in on.",
                    "device", device.getLabel()));
        }

        String token = sessionService.issue(adminUsername, device.getDeviceHash());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie(token, Duration.ofDays(absoluteTimeoutDays)).toString())
                .body(new MeResponse(adminUsername));
    }

    /** Who am I - the SPA cannot read an httpOnly cookie, so it asks. */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new MeResponse(authentication.getName()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${session.cookie-name:sixeyes_session}", required = false) String token) {
        if (token != null) {
            sessionService.revoke(token);
        }
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString())
                .build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, Integer>> logoutAll(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int revoked = sessionService.revokeAllForUser(authentication.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO).toString())
                .body(Map.of("revoked", revoked));
    }

    /** Flags the caller's own browser so the UI can warn before it removes itself. */
    @GetMapping("/devices")
    public List<DeviceResponse> devices(
            @RequestHeader(value = DEVICE_HEADER, required = false) String deviceId) {
        String currentHash = deviceId == null ? "" : Secrets.sha256(deviceId);
        return deviceService.list().stream()
                .map(d -> DeviceResponse.of(d, currentHash))
                .toList();
    }

    @PostMapping("/devices/{id}/approve")
    public DeviceResponse approveDevice(@PathVariable Long id) {
        return DeviceResponse.of(deviceService.approve(id), "");
    }

    /** Removing a device also kills whatever sessions it still holds. */
    @DeleteMapping("/devices/{id}")
    public Map<String, Integer> removeDevice(@PathVariable Long id) {
        String deviceHash = deviceService.remove(id);
        return Map.of("revokedSessions", sessionService.revokeAllForDevice(deviceHash));
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)          // unreadable from JavaScript, so XSS cannot lift it
                .secure(cookieSecure)
                .sameSite("Strict")      // never sent on a cross-site request, which covers CSRF
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private boolean isPasswordMatch(String raw, String stored) {
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            return passwordEncoder.matches(raw, stored);
        }
        return Secrets.constantTimeEquals(raw, stored);
    }

    /**
     * Behind the tunnel every request arrives from 127.0.0.1, so keying on the socket address
     * made the limiter global: one attacker locked the owner out. Cloudflare overwrites
     * CF-Connecting-IP at the edge, so it cannot be forged through the tunnel.
     */
    // ponytail: trusts the header from any peer - fine while bound to loopback; with -Lan a
    // LAN client could rotate it to dodge the limiter (device approval still blocks entry).
    private static String clientIp(HttpServletRequest http) {
        String cf = http.getHeader("CF-Connecting-IP");
        return cf != null && !cf.isBlank() ? cf.trim() : http.getRemoteAddr();
    }

    /** Flat delay on failure: makes online guessing slow without leaking anything. */
    private static void slowDown() {
        try {
            Thread.sleep(FAILURE_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
