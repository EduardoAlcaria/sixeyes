package com.sixeyes.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret",
                "c2l4ZXllcy1sb2NhbC1kZXZlbG9wbWVudC1zZWNyZXQtZG8tbm90LXVzZS1pbi1wcm9kdWN0aW9u");
        ReflectionTestUtils.setField(jwtService, "expirationMs", 86_400_000L);
    }

    @Test
    void generatedTokenIsValidAndCarriesUsername() {
        String token = jwtService.generate("eduardo");

        assertThat(jwtService.isValid(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("eduardo");
    }

    @Test
    void malformedSignatureIsRejected() {
        assertThat(jwtService.isValid("not.a.jwt")).isFalse();
    }

    @Test
    void blankTokenIsRejectedInsteadOfThrowing() {
        // Jwts.parser() rejects an empty string with IllegalArgumentException,
        // not JwtException. isValid() must swallow that too, or JwtFilter turns
        // a blank/malformed Authorization header into a 500 instead of a 401.
        assertThat(jwtService.isValid("")).isFalse();
    }
}
