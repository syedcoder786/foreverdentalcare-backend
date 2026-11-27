package com.clinic.controller;

import com.clinic.dto.PatientDtos.OtpRequest;
import com.clinic.dto.PatientDtos.OtpSentResponse;
import com.clinic.dto.PatientDtos.OtpVerifyRequest;
import com.clinic.dto.PatientDtos.OtpVerifyResponse;
import com.clinic.dto.PatientDtos.PatientAuthResponse;
import com.clinic.dto.PatientDtos.SignupRequest;
import com.clinic.dto.response.ApiResponse;
import com.clinic.exception.AuthenticationFailedException;
import com.clinic.security.PatientRefreshTokenCookie;
import com.clinic.service.OtpService;
import com.clinic.service.PatientAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Patient sign-in with a WhatsApp one-time code:
 * <ol>
 *   <li>POST /otp/request {phone}: sends the code</li>
 *   <li>POST /otp/verify {phone, code}: returning patients are signed in; new numbers get a sign-up token</li>
 *   <li>POST /signup {signupToken, fullName, email?}: creates the profile and signs in</li>
 * </ol>
 * Sessions continue with POST /refresh (HttpOnly cookie) and end with POST /logout.
 */
@RestController
@RequestMapping("/api/patient-auth")
@RequiredArgsConstructor
public class PatientAuthController {

    private final OtpService otpService;
    private final PatientAuthService authService;
    private final PatientRefreshTokenCookie cookie;

    @PostMapping("/otp/request")
    public ApiResponse<OtpSentResponse> requestCode(@Valid @RequestBody OtpRequest request) {
        OtpService.OtpSent sent = otpService.send(request.phone());
        return ApiResponse.ok("We've sent a code to your WhatsApp",
                new OtpSentResponse(sent.phone(), sent.maskedPhone(), sent.expiresInSeconds(), sent.resendAfterSeconds()));
    }

    @PostMapping("/otp/verify")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verify(@Valid @RequestBody OtpVerifyRequest request) {
        PatientAuthService.VerifyResult result = authService.verifyCode(request.phone(), request.code());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            builder.header(HttpHeaders.SET_COOKIE, cookie.create(result.refreshToken()).toString());
        }
        return builder.body(ApiResponse.ok(result.response().newPatient() ? "Number verified" : "Signed in", result.response()));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<PatientAuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return withCookie(authService.completeSignup(request), "Welcome! Your profile is ready.");
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<PatientAuthResponse>> refresh(
            @CookieValue(name = PatientRefreshTokenCookie.NAME, required = false) String refreshToken) {
        try {
            return withCookie(authService.refresh(refreshToken), null);
        } catch (AuthenticationFailedException ex) {
            return ResponseEntity.status(401)
                    .header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = PatientRefreshTokenCookie.NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.clear().toString())
                .body(ApiResponse.ok("Signed out", null));
    }

    private ResponseEntity<ApiResponse<PatientAuthResponse>> withCookie(PatientAuthService.PatientAuthResult result, String message) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.create(result.refreshToken()).toString())
                .body(ApiResponse.ok(message, result.response()));
    }
}
