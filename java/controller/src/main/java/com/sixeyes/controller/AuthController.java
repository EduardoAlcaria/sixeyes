package com.sixeyes.controller;

import com.sixeyes.dto.request.LoginRequest;
import com.sixeyes.dto.response.TokenResponse;
import com.sixeyes.service.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        boolean validUsername = request.username().equals(adminUsername);
        boolean validPassword = isPasswordMatch(request.password(), adminPassword);

        if (!validUsername || !validPassword) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(new TokenResponse(jwtService.generate(request.username())));
    }

    private boolean isPasswordMatch(String raw, String stored) {
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$")) {
            return passwordEncoder.matches(raw, stored);
        }
        return raw.equals(stored);
    }
}
