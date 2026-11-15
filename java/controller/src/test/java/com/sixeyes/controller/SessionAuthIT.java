package com.sixeyes.controller;

import com.sixeyes.repo.TrustedDeviceRepository;
import com.sixeyes.repo.UserSessionRepository;
import com.sixeyes.service.PythonClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The point of replacing JWT with a session table was that logout actually invalidates.
 * This proves it: the same cookie that worked before logout is rejected after.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "admin.password=test-pass")
@ActiveProfiles("dev")
class SessionAuthIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    PythonClientService pythonClient;

    @Autowired
    TrustedDeviceRepository devices;

    @Autowired
    UserSessionRepository sessions;

    /** The first device ever seen is trusted on sight, so every test needs to start from
     *  an empty table or the outcome depends on execution order. */
    @BeforeEach
    void reset() {
        sessions.deleteAll();
        devices.deleteAll();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> login(String deviceId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-SixEyes-Device-Id", deviceId);
        return rest.postForEntity(
                url("/api/v1/auth/login"),
                new HttpEntity<>("{\"username\":\"admin\",\"password\":\"test-pass\"}", headers),
                String.class);
    }

    private ResponseEntity<String> getWithCookie(String path, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static String sessionCookie(ResponseEntity<?> response) {
        List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream()
                .filter(c -> c.startsWith("sixeyes_session="))
                .findFirst()
                .orElseThrow()
                .split(";")[0];
    }

    @Test
    void loginIssuesAnHttpOnlySameSiteCookieAndLogoutRevokesIt() {
        ResponseEntity<String> loginResponse = login("device-lifecycle-test");
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        String rawSetCookie = loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(rawSetCookie).contains("HttpOnly");   // unreadable to JavaScript
        assertThat(rawSetCookie).contains("SameSite=Strict"); // this is what covers CSRF

        String cookie = sessionCookie(loginResponse);
        assertThat(getWithCookie("/api/v1/auth/me", cookie).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie);
        rest.exchange(url("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);

        // The same cookie value, replayed. A JWT would still verify here.
        assertThat(getWithCookie("/api/v1/auth/me", cookie).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedRouteIsRejectedWithoutACookie() {
        assertThat(getWithCookie("/api/v1/auth/me", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void unknownApiRouteIs404RatherThanTheSpaShell() {
        // The SPA fallback must not swallow API 404s - otherwise every mistyped endpoint
        // would answer 200 with HTML and look like it worked. Needs a session, since
        // security would otherwise reject before routing ever happens.
        String cookie = sessionCookie(login("device-404-probe"));

        ResponseEntity<String> response = getWithCookie("/api/v1/does-not-exist", cookie);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void secondDeviceIsHeldForApproval() {
        login("device-first-trusted-on-use");

        ResponseEntity<String> second = login("device-second-must-wait");

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(second.getBody()).contains("PENDING_APPROVAL");
    }
}
