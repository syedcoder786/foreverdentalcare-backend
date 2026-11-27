package com.clinic.controller;

import com.clinic.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDatabaseIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginAndGetAccessToken();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Object> connection(String host) {
        return Map.of("host", host, "port", 5432, "database", "postgres", "username", "postgres",
                "password", "secret", "schema", "dental_clinic", "sslMode", "require");
    }

    @Test
    void statusShowsTheEnvironmentDatabaseWithoutPasswords() throws Exception {
        mockMvc.perform(auth(get("/api/admin/database")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.connected").value(true))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void requiresAdminSignIn() throws Exception {
        mockMvc.perform(get("/api/admin/database")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidConnectionFields() throws Exception {
        Map<String, Object> bad = Map.of("host", "bad host;", "port", 99999, "database", "x y", "username", "u",
                "schema", "Bad-Schema", "sslMode", "maybe");
        mockMvc.perform(auth(post("/api/admin/database/test")).content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.host").exists())
                .andExpect(jsonPath("$.errors.port").exists())
                .andExpect(jsonPath("$.errors.schema").exists())
                .andExpect(jsonPath("$.errors.sslMode").exists());
    }

    @Test
    void unknownHostGivesAClearMessage() throws Exception {
        mockMvc.perform(auth(post("/api/admin/database/test"))
                        .content(objectMapper.writeValueAsString(connection("no-such-host.invalid"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("The host no-such-host.invalid was not found. Check the host name."));
    }

    @Test
    void switchRequiresTheAdminPassword() throws Exception {
        Map<String, Object> request = Map.of("connection", connection("no-such-host.invalid"), "copyData", true,
                "currentPassword", "wrong-password");
        mockMvc.perform(auth(post("/api/admin/database/switch")).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.currentPassword").value("That password is not correct"));
    }
}
