package com.clinic.controller;

import com.clinic.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContentManagementIntegrationTest extends IntegrationTestBase {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginAndGetAccessToken();
    }

    @Test
    void publicSiteAndPagesAreServedFromTheDatabase() throws Exception {
        mockMvc.perform(get("/api/public/site"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinic.clinicName").exists())
                .andExpect(jsonPath("$.data.navigation.length()").value(6));
        mockMvc.perform(get("/api/public/pages/HOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sections[0].sectionKey").value("hero"))
                .andExpect(jsonPath("$.data.seo.pageKey").value("HOME"));
        mockMvc.perform(get("/api/public/pages/NOPE")).andExpect(status().isNotFound());
    }

    @Test
    void adminCanEditHideAndAddSections() throws Exception {
        MvcResult list = mockMvc.perform(asAdmin(get("/api/admin/pages/HOME/sections"), token)).andReturn();
        long heroId = json(list).path("data").get(0).path("id").asLong();
        String originalTitle = json(list).path("data").get(0).path("title").asText();

        Map<String, Object> hero = new LinkedHashMap<>();
        hero.put("title", "A brand new headline");
        hero.put("primaryButtonLabel", "Book now");
        hero.put("primaryButtonUrl", "/book");
        hero.put("items", List.of(Map.of("title", "Point one")));
        hero.put("active", true);
        mockMvc.perform(asAdmin(put("/api/admin/sections/{id}", heroId), token).content(objectMapper.writeValueAsString(hero)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/pages/HOME")).andExpect(jsonPath("$.data.sections[0].title").value("A brand new headline"));

        // Unsafe link targets are rejected.
        hero.put("primaryButtonUrl", "javascript:alert(1)");
        mockMvc.perform(asAdmin(put("/api/admin/sections/{id}", heroId), token).content(objectMapper.writeValueAsString(hero)))
                .andExpect(status().isBadRequest());

        // Custom section: add, then delete. Built-in sections cannot be deleted.
        Map<String, Object> custom = Map.of("sectionKey", "test_extra", "title", "Extra section", "body", "Hello");
        MvcResult created = mockMvc.perform(asAdmin(post("/api/admin/pages/ABOUT/sections"), token).content(objectMapper.writeValueAsString(custom)))
                .andExpect(status().isCreated()).andReturn();
        long customId = json(created).path("data").path("id").asLong();
        mockMvc.perform(asAdmin(delete("/api/admin/sections/{id}", customId), token)).andExpect(status().isOk());
        mockMvc.perform(asAdmin(delete("/api/admin/sections/{id}", heroId), token)).andExpect(status().isUnprocessableEntity());

        hero.put("title", originalTitle);
        hero.put("primaryButtonUrl", "/book");
        mockMvc.perform(asAdmin(put("/api/admin/sections/{id}", heroId), token).content(objectMapper.writeValueAsString(hero)));
    }

    @Test
    void testimonialsRequireConsentBeforePublishing() throws Exception {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("patientName", "A. K.");
        t.put("quote", "Very gentle and clear.");
        t.put("published", true);
        t.put("consentConfirmed", false);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials"), token).content(objectMapper.writeValueAsString(t)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.consentConfirmed").exists());

        t.put("consentConfirmed", true);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials"), token).content(objectMapper.writeValueAsString(t)))
                .andExpect(status().isCreated());
        t.put("patientName", "Draft Person");
        t.put("published", false);
        mockMvc.perform(asAdmin(post("/api/admin/testimonials"), token).content(objectMapper.writeValueAsString(t)));

        mockMvc.perform(get("/api/public/testimonials"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].patientName").value("A. K."));
    }

    @Test
    void imageUploadsAreValidatedByContentAndServedPublicly() throws Exception {
        MvcResult ok = mockMvc.perform(multipart("/api/admin/uploads")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        String url = json(ok).path("data").path("url").asText();
        assertThat(url).startsWith("/api/public/files/").endsWith(".png");
        mockMvc.perform(get(url)).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"));

        // A script renamed to .png is rejected (content sniffing, not file name).
        mockMvc.perform(multipart("/api/admin/uploads")
                        .file(new MockMultipartFile("file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(multipart("/api/admin/uploads").file(new MockMultipartFile("file", "photo.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/files/../../etc/passwd")).andExpect(status().is4xxClientError());
    }

    @Test
    void publicDoctorProfilesHideUnverifiedCredentials() throws Exception {
        long[] b = createUnaniDoctor(token, "16:00", "20:00", null, null);
        mockMvc.perform(get("/api/public/doctors/by-slug/test-unani"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credentialsVerified").value(false))
                .andExpect(jsonPath("$.data.qualifications.length()").value(0))
                .andExpect(jsonPath("$.data.registrationNumber").doesNotExist())
                .andExpect(jsonPath("$.data.specialties[0].slug").value("unani-medicine"));

        MvcResult admin = mockMvc.perform(asAdmin(get("/api/admin/doctors/{id}", b[0]), token)).andReturn();
        assertThat(json(admin).path("data").path("registrationNumber").asText()).isEqualTo("REG-123");

        mockMvc.perform(get("/api/public/specialties/unani-medicine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctors.length()").value(1))
                .andExpect(jsonPath("$.data.services[0].name").value("Test Unani Consultation"));
    }

    @Test
    void newAdminEndpointsRequireAuthentication() throws Exception {
        for (String url : new String[]{"/api/admin/doctors", "/api/admin/specialties", "/api/admin/calendar?from=2030-01-01&to=2030-01-02",
                "/api/admin/patients", "/api/admin/testimonials", "/api/admin/gallery", "/api/admin/seo",
                "/api/admin/navigation", "/api/admin/pages/HOME/sections", "/api/admin/audit-logs"}) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void adminChangesAreAuditLogged() throws Exception {
        Map<String, Object> spec = Map.of("name", "Test Specialty", "description", "x");
        mockMvc.perform(asAdmin(post("/api/admin/specialties"), token).content(objectMapper.writeValueAsString(spec)))
                .andExpect(status().isCreated());
        mockMvc.perform(asAdmin(get("/api/admin/audit-logs"), token))
                .andExpect(jsonPath("$.data.content[0].entityType").value("Specialty"))
                .andExpect(jsonPath("$.data.content[0].adminEmail").value(ADMIN_EMAIL));
    }

    @Test
    void patientsAreListedForAdminsOnly() throws Exception {
        createAppointment(nextWorkingDay(), "10:00");
        mockMvc.perform(asAdmin(get("/api/admin/patients"), token).param("search", "test patient"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].appointmentCount").value(1));
    }
}
