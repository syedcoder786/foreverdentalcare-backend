package com.clinic.controller;

import com.clinic.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminManagementIntegrationTest extends IntegrationTestBase {

    private String token;

    @BeforeEach
    void login() throws Exception {
        token = loginAndGetAccessToken();
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(Object o) throws Exception {
        return objectMapper.writeValueAsString(o);
    }

    @Test
    void fullStatusLifecycle() throws Exception {
        Long id = createAppointment(nextWorkingDay(), "10:00");

        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "CONFIRMED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "COMPLETED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.reschedulable").value(false));

        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "CANCELLED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void pendingCannotJumpToCompleted() throws Exception {
        Long id = createAppointment(nextWorkingDay(), "10:00");
        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "COMPLETED"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cancellingFreesSlotForNewBooking() throws Exception {
        LocalDate day = nextWorkingDay();
        Long id = createAppointment(day, "15:00");
        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());

        Map<String, Object> again = bookingPayload(day, "15:00");
        again.put("phone", "+91 91111 22222");
        assertThat(postJson("/api/public/appointments", again).getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void rescheduleMovesAppointmentAndRejectsTakenSlot() throws Exception {
        LocalDate day = nextWorkingDay();
        Long first = createAppointment(day, "10:00");
        Map<String, Object> other = bookingPayload(day, "11:00");
        other.put("phone", "+91 93333 44444");
        postJson("/api/public/appointments", other);

        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/reschedule", first))
                        .content(body(Map.of("appointmentDate", day.toString(), "appointmentTime", "11:00"))))
                .andExpect(status().isConflict());

        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/reschedule", first))
                        .content(body(Map.of("appointmentDate", day.toString(), "appointmentTime", "16:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentTime").value("16:00"));
    }

    @Test
    void listSupportsSearchStatusDateAndPagination() throws Exception {
        LocalDate day = nextWorkingDay();
        createAppointment(day, "10:00");
        Map<String, Object> other = bookingPayload(day, "10:30");
        other.put("fullName", "Meera Iyer");
        other.put("phone", "+91 95555 66666");
        postJson("/api/public/appointments", other);

        mockMvc.perform(auth(get("/api/admin/appointments")).param("search", "meera"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].patient.fullName").value("Meera Iyer"));

        mockMvc.perform(auth(get("/api/admin/appointments")).param("search", "95555"))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(auth(get("/api/admin/appointments")).param("status", "PENDING").param("date", day.toString())
                        .param("size", "1").param("page", "0"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(auth(get("/api/admin/appointments")).param("status", "CONFIRMED"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void getUnknownAppointmentIs404() throws Exception {
        mockMvc.perform(auth(get("/api/admin/appointments/{id}", 999999))).andExpect(status().isNotFound());
    }

    @Test
    void dashboardCountsByStatus() throws Exception {
        Long id = createAppointment(nextWorkingDay(), "10:00");
        createAppointment(nextWorkingDay(), "10:30");
        mockMvc.perform(auth(patch("/api/admin/appointments/{id}/status", id)).content(body(Map.of("status", "CONFIRMED"))));

        mockMvc.perform(auth(get("/api/admin/dashboard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAppointments").value(2))
                .andExpect(jsonPath("$.data.pendingAppointments").value(1))
                .andExpect(jsonPath("$.data.confirmedAppointments").value(1));
    }

    @Test
    void serviceCrudAndSoftDeleteWhenInUse() throws Exception {
        Map<String, Object> svc = new LinkedHashMap<>();
        svc.put("name", "Test Fluoride Application");
        svc.put("description", "Application of fluoride varnish.");
        svc.put("details", List.of("Quick procedure"));
        svc.put("consultationFee", 750.50);
        svc.put("estimatedDuration", 20);
        svc.put("specialtyId", specialtyId("dentistry"));
        svc.put("doctorIds", List.of(dentistId()));

        MvcResult created = mockMvc.perform(auth(post("/api/admin/services")).content(body(svc))).andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long newId = json(created).path("data").path("id").asLong();

        mockMvc.perform(auth(post("/api/admin/services")).content(body(svc))).andExpect(status().isConflict());

        svc.put("consultationFee", 800);
        mockMvc.perform(auth(put("/api/admin/services/{id}", newId)).content(body(svc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consultationFee").value(800));

        mockMvc.perform(auth(delete("/api/admin/services/{id}", newId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Service deleted"));

        // A service with appointments is deactivated, not deleted.
        createAppointment(nextWorkingDay(), "10:00");
        Long usedId = firstServiceId();
        mockMvc.perform(auth(delete("/api/admin/services/{id}", usedId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deactivated")));
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM services WHERE id = ?", Boolean.class, usedId)).isFalse();
        jdbcTemplate.update("UPDATE services SET active = TRUE WHERE id = ?", usedId);
    }

    @Test
    void serviceValidation() throws Exception {
        Map<String, Object> bad = Map.of("name", "", "description", "x", "estimatedDuration", 0, "consultationFee", -1);
        mockMvc.perform(auth(post("/api/admin/services")).content(body(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.estimatedDuration").exists())
                .andExpect(jsonPath("$.errors.consultationFee").exists());
    }

    @Test
    void settingsUpdateValidatesHours() throws Exception {
        MvcResult current = mockMvc.perform(auth(get("/api/admin/settings"))).andReturn();
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = objectMapper.convertValue(json(current).path("data"), Map.class);
        settings.remove("timezone");

        Map<String, Object> invalid = new LinkedHashMap<>(settings);
        invalid.put("openingTime", "18:00");
        invalid.put("closingTime", "09:00");
        mockMvc.perform(auth(put("/api/admin/settings")).content(body(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.closingTime").exists());

        Map<String, Object> valid = new LinkedHashMap<>(settings);
        valid.put("address", "[PLACEHOLDER] Updated address");
        mockMvc.perform(auth(put("/api/admin/settings")).content(body(valid)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.address").value("[PLACEHOLDER] Updated address"));
    }
}
