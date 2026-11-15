package com.sixeyes.controller;

import com.sixeyes.service.PythonClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies every backend route is served under {@code /api/v1} and that Spring Security
 * still applies.
 *
 * <p>The prefix now comes from {@code WebConfig.configurePathMatch} rather than
 * {@code server.servlet.context-path}, because the context path would have dragged the
 * static SPA under {@code /api/v1} too. Same external URLs, different mechanism - which is
 * exactly why these assertions are worth keeping.
 *
 * <p>{@code RANDOM_PORT} + {@link TestRestTemplate} so requests traverse the real embedded
 * Tomcat and the true runtime routes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "admin.password=test-pass")
@ActiveProfiles("dev")
class RoutingIT {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @MockitoBean
    PythonClientService pythonClient;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void healthEndpointIsUnderApiV1() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/v1/torrents/test"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void loginEndpointIsUnderApiV1AndPublic() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{\"username\":\"x\",\"password\":\"y\"}", headers);

        ResponseEntity<String> response = rest.postForEntity(url("/api/v1/auth/login"), request, String.class);

        // Reaches the controller (permitAll) and returns 401 for bad credentials,
        // proving the route is public and lives under /api/v1.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void torrentsListRequiresAuth() {
        ResponseEntity<String> response = rest.getForEntity(url("/api/v1/torrents/get"), String.class);
        // 401, not 403: there is no session at all, so this is an authentication failure.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void apiIsNotReachableWithoutThePrefix() {
        // Non-API paths belong to the SPA now, so this may 404 (no build present) or serve
        // index.html (build present). What it must never do is answer as the API.
        ResponseEntity<String> response = rest.getForEntity(url("/torrents/test"), String.class);
        assertThat(response.getBody()).doesNotContain("\"service\"");
    }
}
