package com.claimflow.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new organisation together with its first ADMIN user")
    public TokenResponse register(@Valid @RequestBody RegisterOrgRequest request) {
        return service.registerOrganization(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email and password for an access + refresh token pair")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token (sign out). Always 204, even for unknown tokens.")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        service.logout(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token; the presented token is revoked")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return service.refresh(request);
    }
}
