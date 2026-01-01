package com.sixeyes.controller;

import com.sixeyes.service.PythonClientService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that every backend route is served under the {@code /api/v1} context-path
 * and that Spring Security rules still apply after the context-path is stripped.
 *
 * <p>Uses {@code RANDOM_PORT} + {@link TestRestTemplate} so requests go through the real
 * embedded Tomcat, which honours {@code server.servlet.context-path} (standalone MockMvc
 * does not), making these assertions exercise the true runtime routes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rootPathIsNotMapped() {
        // Without the /api/v1 prefix the endpoint must not resolve.
        ResponseEntity<String> response = rest.getForEntity(url("/torrents/test"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
