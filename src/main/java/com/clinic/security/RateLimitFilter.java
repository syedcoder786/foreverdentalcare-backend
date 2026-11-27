package com.clinic.security;

import com.clinic.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * Per-client-IP rate limiting for abuse-prone public endpoints: appointment submission, login and token refresh.
 * Client IP is {@code request.getRemoteAddr()}; behind a reverse proxy set FORWARD_HEADERS_STRATEGY=native so that
 * the real client address is used.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(String method, String path, String name, int limit, Duration window) {
    }

    private final RateLimiter limiter;
    private final JsonSecurityHandlers handlers;
    private final List<Rule> rules;

    public RateLimitFilter(RateLimiter limiter, JsonSecurityHandlers handlers, AppProperties properties) {
        this.limiter = limiter;
        this.handlers = handlers;
        AppProperties.RateLimit cfg = properties.rateLimit();
        this.rules = List.of(
                new Rule("POST", "/api/public/appointments", "booking", cfg.bookingPerHour(), Duration.ofHours(1)),
                new Rule("POST", "/api/auth/login", "login", cfg.loginPerMinute(), Duration.ofMinutes(1)),
                new Rule("POST", "/api/auth/refresh", "refresh", cfg.refreshPerMinute(), Duration.ofMinutes(1)),
                // Patient sign-in (per IP; per-number limits are enforced in OtpService).
                new Rule("POST", "/api/patient-auth/otp/request", "otp-request", 10, Duration.ofHours(1)),
                new Rule("POST", "/api/patient-auth/otp/verify", "otp-verify", 30, Duration.ofMinutes(10)),
                new Rule("POST", "/api/patient-auth/signup", "patient-signup", 10, Duration.ofHours(1)),
                new Rule("POST", "/api/patient-auth/refresh", "patient-refresh", cfg.refreshPerMinute(), Duration.ofMinutes(1)),
                new Rule("POST", "/api/patient/appointments", "patient-booking", cfg.bookingPerHour(), Duration.ofHours(1)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (Rule rule : rules) {
            if (rule.method.equalsIgnoreCase(request.getMethod()) && rule.path.equals(path)) {
                RateLimiter.Decision decision =
                        limiter.tryAcquire(rule.name + ":" + request.getRemoteAddr(), rule.limit, rule.window);
                if (!decision.allowed()) {
                    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
                    handlers.write(response, HttpStatus.TOO_MANY_REQUESTS,
                            "Too many requests. Please wait a little and try again.");
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
