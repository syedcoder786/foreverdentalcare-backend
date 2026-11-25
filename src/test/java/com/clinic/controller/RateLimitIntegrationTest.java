package com.clinic.controller;

import com.clinic.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.rate-limit.login-per-minute=3",
        "app.rate-limit.booking-per-hour=2"
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    @Test
    void loginIsRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", "bad")).getResponse().getStatus())
                    .isEqualTo(401);
        }
        MvcResult limited = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
        assertThat(json(limited).path("success").asBoolean()).isFalse();
    }

    @Test
    void bookingIsRateLimited() throws Exception {
        var day = nextWorkingDay();
        assertThat(postJson("/api/public/appointments", bookingPayload(day, "10:00")).getResponse().getStatus()).isEqualTo(201);
        assertThat(postJson("/api/public/appointments", bookingPayload(day, "10:30")).getResponse().getStatus()).isEqualTo(201);
        assertThat(postJson("/api/public/appointments", bookingPayload(day, "11:00")).getResponse().getStatus()).isEqualTo(429);
    }
}
