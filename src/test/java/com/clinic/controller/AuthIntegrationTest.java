package com.clinic.controller;

import com.clinic.security.RefreshTokenCookie;
import com.clinic.support.IntegrationTestBase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestBase {

    @Test
    void adminEndpointsRequireAuthentication() throws Exception {
        for (String url : new String[]{"/api/admin/dashboard", "/api/admin/appointments", "/api/admin/services", "/api/admin/settings"}) {
            mockMvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer not.a.valid.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordReturns401WithGenericMessage() throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", "wrong-password"));
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(result).path("message").asText()).isEqualTo("Invalid email or password");

        MvcResult unknown = postJson("/api/auth/login", Map.of("email", "nobody@test.local", "password", "whatever"));
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(unknown).path("message").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    void loginReturnsTokenAndHttpOnlyRefreshCookieWithoutPassword() throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2a$");
        assertThat(json(result).path("data").path("accessToken").asText()).isNotBlank();
        assertThat(json(result).path("data").path("admin").path("email").asText()).isEqualTo(ADMIN_EMAIL);

        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains(RefreshTokenCookie.NAME + "=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void validTokenGrantsAccessToAdminApis() throws Exception {
        String token = loginAndGetAccessToken();
        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAppointments").value(0));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() throws Exception {
        MvcResult login = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        Cookie original = login.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(original).isNotNull();

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh").cookie(original)).andReturn();
        assertThat(refreshed.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(refreshed).path("data").path("accessToken").asText()).isNotBlank();
        Cookie rotated = refreshed.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        // Reusing the old (rotated-out) token fails and revokes the whole session family.
        mockMvc.perform(post("/api/auth/refresh").cookie(original)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(rotated)).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieIs401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        MvcResult login = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        Cookie cookie = login.getResponse().getCookie(RefreshTokenCookie.NAME);

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(cookie)).andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(logout.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        mockMvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownEndpointsAreDenied() throws Exception {
        mockMvc.perform(get("/api/something-else")).andExpect(status().is4xxClientError());
    }
}
