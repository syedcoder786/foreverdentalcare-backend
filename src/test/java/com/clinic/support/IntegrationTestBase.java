package com.clinic.support;

import com.clinic.security.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "TestPassword123!";
    protected static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected RateLimiter rateLimiter;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM appointments");
        jdbcTemplate.update("DELETE FROM patients");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM doctor_blocked_dates");
        jdbcTemplate.update("DELETE FROM doctor_special_hours");
        jdbcTemplate.update("DELETE FROM testimonials");
        jdbcTemplate.update("DELETE FROM gallery_images");
        jdbcTemplate.update("DELETE FROM doctors WHERE slug LIKE 'test-%'");
        jdbcTemplate.update("DELETE FROM services WHERE name LIKE 'Test %'");
        jdbcTemplate.update("DELETE FROM specialties WHERE name LIKE 'Test %'");
        jdbcTemplate.update("DELETE FROM website_sections WHERE section_key LIKE 'test_%'");
        jdbcTemplate.update("UPDATE services SET active = TRUE");
        jdbcTemplate.update("UPDATE doctors SET active = TRUE");
        jdbcTemplate.update("UPDATE specialties SET active = TRUE");
        rateLimiter.reset();
    }

    /** A future Monday-Saturday date, safely inside the booking window. */
    protected LocalDate nextWorkingDay() {
        LocalDate d = LocalDate.now(ZONE).plusDays(3);
        while (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    protected LocalDate nextSunday() {
        LocalDate d = LocalDate.now(ZONE).plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** The seeded dentist (migrated from the original single-doctor setup). */
    protected Long dentistId() {
        return jdbcTemplate.queryForObject("SELECT id FROM doctors WHERE slug = 'dentist'", Long.class);
    }

    protected Long firstServiceId() {
        return jdbcTemplate.queryForObject("SELECT MIN(id) FROM services WHERE active = TRUE AND name NOT LIKE 'Test %'", Long.class);
    }

    protected Long serviceIdByName(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM services WHERE name = ?", Long.class, name);
    }

    protected Long specialtyId(String slug) {
        return jdbcTemplate.queryForObject("SELECT id FROM specialties WHERE slug = ?", Long.class, slug);
    }

    protected Map<String, Object> bookingPayload(LocalDate date, String time) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("fullName", "Test Patient");
        body.put("phone", "+91 98765 43210");
        body.put("email", "patient@example.com");
        body.put("serviceId", firstServiceId());
        body.put("doctorId", dentistId());
        body.put("appointmentDate", date.toString());
        body.put("appointmentTime", time);
        body.put("message", "Mild tooth pain");
        body.put("consent", true);
        return body;
    }

    protected Map<String, Object> booking(Long doctorId, Long serviceId, LocalDate date, String time, String phone) {
        Map<String, Object> body = bookingPayload(date, time);
        body.put("doctorId", doctorId);
        body.put("serviceId", serviceId);
        body.put("phone", phone);
        return body;
    }

    protected MvcResult postJson(String url, Object body) throws Exception {
        return mockMvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    protected MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    protected JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected String loginAndGetAccessToken() throws Exception {
        MvcResult result = postJson("/api/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD));
        return json(result).path("data").path("accessToken").asText();
    }

    protected Long createAppointment(LocalDate date, String time) throws Exception {
        MvcResult result = postJson("/api/public/appointments", bookingPayload(date, time));
        return json(result).path("data").path("referenceId").asLong();
    }

    /**
     * Creates a second practitioner ("test-unani") in the Unani Medicine specialty with her own schedule and one
     * Unani service. Returns [doctorId, serviceId].
     */
    protected long[] createUnaniDoctor(String token, String start, String end, String breakStart, String breakEnd) throws Exception {
        Long unani = specialtyId("unani-medicine");
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("name", "Test Unani Consultation");
        service.put("specialtyId", unani);
        service.put("description", "Placeholder test service");
        service.put("estimatedDuration", 30);
        MvcResult s = mockMvc.perform(asAdmin(post("/api/admin/services"), token).content(objectMapper.writeValueAsString(service))).andReturn();
        long serviceId = json(s).path("data").path("id").asLong();

        Map<String, Object> doctor = new LinkedHashMap<>();
        doctor.put("fullName", "Test Unani Doctor");
        doctor.put("slug", "test-unani");
        doctor.put("professionalTitle", "Unani Physician (BUMS)");
        doctor.put("gender", "FEMALE");
        doctor.put("specialtyIds", List.of(unani));
        doctor.put("serviceIds", List.of(serviceId));
        doctor.put("qualifications", List.of(Map.of("degree", "BUMS", "institution", "Test College")));
        doctor.put("registrationNumber", "REG-123");
        doctor.put("credentialsVerified", false);
        MvcResult d = mockMvc.perform(asAdmin(post("/api/admin/doctors"), token).content(objectMapper.writeValueAsString(doctor))).andReturn();
        long doctorId = json(d).path("data").path("id").asLong();

        List<Map<String, Object>> weekly = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("dayOfWeek", day.name());
            w.put("active", day != DayOfWeek.SUNDAY);
            w.put("startTime", start);
            w.put("endTime", end);
            w.put("breakStartTime", breakStart);
            w.put("breakEndTime", breakEnd);
            w.put("slotDurationMinutes", 30);
            weekly.add(w);
        }
        mockMvc.perform(asAdmin(put("/api/admin/doctors/{id}/schedule", doctorId), token)
                .content(objectMapper.writeValueAsString(Map.of("weekly", weekly, "specialHours", List.of()))));
        return new long[]{doctorId, serviceId};
    }
}
