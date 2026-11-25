package com.clinic.controller;

import com.clinic.dto.request.LoginRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.dto.response.AuthResponse;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.security.RefreshTokenCookie;
import com.clinic.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookie refreshCookie;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(authService.login(request), "Logged in successfully");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        try {
            return withRefreshCookie(authService.refresh(refreshToken), null);
        } catch (AuthenticationFailedException ex) {
            // Clear a bad cookie, then let the global handler produce the 401 body.
            throw new CookieClearingException(ex);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .body(ApiResponse.ok("Logged out", null));
    }

    @ExceptionHandler(CookieClearingException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefreshFailure(CookieClearingException ex) {
        return ResponseEntity.status(401)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.clear().toString())
                .body(ApiResponse.error("Session expired. Please log in again."));
    }

    private ResponseEntity<ApiResponse<AuthResponse>> withRefreshCookie(AuthService.AuthResult result, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(message, result.response()));
    }

    static class CookieClearingException extends RuntimeException {
        CookieClearingException(Throwable cause) {
            super(cause);
        }
    }
}
