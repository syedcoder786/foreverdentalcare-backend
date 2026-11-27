package com.clinic.controller;

import com.clinic.storage.StorageSettingsStore;
import com.clinic.storage.SwitchableFileStorage;
import com.clinic.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.file.Files;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStorageIntegrationTest extends IntegrationTestBase {

    private static final String ADMIN_PASSWORD = "TestPassword123!";

    @Autowired
    private StorageSettingsStore store;
    @Autowired
    private SwitchableFileStorage storage;

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginAndGetAccessToken();
    }

    @AfterEach
    void restoreEnvironmentStorage() {
        store.delete();
        storage.swap(storage.fromEnvironment());
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    void statusShowsLocalStorageFromEnvironment() throws Exception {
        mockMvc.perform(auth(get("/api/admin/storage")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("ENVIRONMENT"))
                .andExpect(jsonPath("$.data.provider").value("local"))
                .andExpect(jsonPath("$.data.environmentProvider").value("local"))
                .andExpect(jsonPath("$.data.apiSecret").doesNotExist());
    }

    @Test
    void requiresAdminSignIn() throws Exception {
        mockMvc.perform(get("/api/admin/storage")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidCloudinaryFields() throws Exception {
        Map<String, Object> bad = Map.of("cloudName", "bad cloud;", "apiKey", "", "apiSecret", "has space", "folder", "../up");
        mockMvc.perform(auth(post("/api/admin/storage/test")).content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.cloudName").exists())
                .andExpect(jsonPath("$.errors.apiKey").exists())
                .andExpect(jsonPath("$.errors.apiSecret").exists())
                .andExpect(jsonPath("$.errors.folder").exists());
    }

    @Test
    void testNeedsASecretWhenNoneIsSaved() throws Exception {
        Map<String, Object> request = Map.of("cloudName", "demo", "apiKey", "123456789012345", "apiSecret", "");
        mockMvc.perform(auth(post("/api/admin/storage/test")).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.apiSecret").value("Enter the API secret"));
    }

    @Test
    void saveRequiresTheAdminPassword() throws Exception {
        Map<String, Object> request = Map.of("provider", "local", "currentPassword", "wrong-password");
        mockMvc.perform(auth(put("/api/admin/storage")).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.currentPassword").value("That password is not correct"));
        assertThat(Files.exists(store.file())).isFalse();
    }

    @Test
    void savesLocalStorageAndGoesBackToEnvironment() throws Exception {
        Map<String, Object> request = Map.of("provider", "local", "currentPassword", ADMIN_PASSWORD);
        mockMvc.perform(auth(put("/api/admin/storage")).content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        assertThat(Files.exists(store.file())).isTrue();
        mockMvc.perform(auth(get("/api/admin/storage")))
                .andExpect(jsonPath("$.data.source").value("ADMIN_PANEL"))
                .andExpect(jsonPath("$.data.savedBy").value("admin@test.local"));

        mockMvc.perform(auth(post("/api/admin/storage/use-environment"))
                        .content(objectMapper.writeValueAsString(Map.of("currentPassword", ADMIN_PASSWORD))))
                .andExpect(status().isOk());
        assertThat(Files.exists(store.file())).isFalse();
        mockMvc.perform(auth(get("/api/admin/storage"))).andExpect(jsonPath("$.data.source").value("ENVIRONMENT"));
    }
}
