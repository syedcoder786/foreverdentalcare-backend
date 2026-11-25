package com.clinic.controller;

import com.clinic.dto.request.AppointmentRequest;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.service.AppointmentService;
import com.clinic.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicBookingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppointmentService appointmentService;

    @Test
    void publicCatalogueAndClinicInfoAreAccessible() throws Exception {
        mockMvc.perform(get("/api/public/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(8));
        mockMvc.perform(get("/api/public/clinic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clinicName").exists())
                .andExpect(jsonPath("$.data.openingTime").value("10:00"));
    }

    @Test
    void bookingCreatesPendingAppointment() throws Exception {
        MvcResult result = postJson("/api/public/appointments", bookingPayload(nextWorkingDay(), "10:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = json(result);
        assertThat(body.path("success").asBoolean()).isTrue();
        assertThat(body.path("data").path("status").asText()).isEqualTo("PENDING");
        assertThat(body.path("data").path("appointmentTime").asText()).isEqualTo("10:00");
        // Public response must not echo patient personal data.
        assertThat(body.path("data").has("patient")).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT phone FROM patients", String.class)).isEqualTo("+919876543210");
    }

    @Test
    void duplicateSlotIsRejectedWith409() throws Exception {
        LocalDate day = nextWorkingDay();
        assertThat(postJson("/api/public/appointments", bookingPayload(day, "11:00")).getResponse().getStatus()).isEqualTo(201);

        Map<String, Object> second = bookingPayload(day, "11:00");
        second.put("phone", "+91 90000 11111");
        MvcResult result = postJson("/api/public/appointments", second);

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(json(result).path("success").asBoolean()).isFalse();
    }

    @Test
    void availabilityReflectsBookedSlot() throws Exception {
        LocalDate day = nextWorkingDay();
        postJson("/api/public/appointments", bookingPayload(day, "10:30"));

        MvcResult result = mockMvc.perform(get("/api/public/doctors/{id}/availability", dentistId()).param("date", day.toString())).andReturn();
        JsonNode slots = json(result).path("data").path("slots");
        boolean tenThirtyAvailable = true;
        for (JsonNode s : slots) {
            if (s.path("time").asText().equals("10:30")) {
                tenThirtyAvailable = s.path("available").asBoolean();
            }
        }
        assertThat(tenThirtyAvailable).isFalse();
    }

    @Test
    void validationErrorsReturn400WithFieldMessages() throws Exception {
        Map<String, Object> body = bookingPayload(nextWorkingDay(), "10:00");
        body.put("fullName", "");
        body.put("phone", "12ab");
        body.put("consent", false);

        MvcResult result = postJson("/api/public/appointments", body);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode errors = json(result).path("errors");
        assertThat(errors.has("fullName")).isTrue();
        assertThat(errors.has("phone")).isTrue();
        assertThat(errors.has("consent")).isTrue();
    }

    @Test
    void pastDateIsRejected() throws Exception {
        MvcResult result = postJson("/api/public/appointments", bookingPayload(LocalDate.now().minusDays(2), "10:00"));

        assertThat(result.getResponse().getStatus()).isEqualTo(422);
        assertThat(json(result).path("errors").has("appointmentDate")).isTrue();
    }

    @Test
    void closedDayIsRejected() throws Exception {
        MvcResult result = postJson("/api/public/appointments", bookingPayload(nextSunday(), "10:00"));
        assertThat(result.getResponse().getStatus()).isEqualTo(422);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        Map<String, Object> body = bookingPayload(nextWorkingDay(), "not-a-time");
        assertThat(postJson("/api/public/appointments", body).getResponse().getStatus()).isEqualTo(400);
    }

    /** Fires many simultaneous requests for the same slot: exactly one must win, the rest get SlotUnavailable. */
    @Test
    void concurrentBookingsForSameSlotOnlyOneSucceeds() throws Exception {
        LocalDate day = nextWorkingDay();
        Long serviceId = firstServiceId();
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String phone = "+9190000000" + String.format("%02d", i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    appointmentService.requestAppointment(new AppointmentRequest(
                            "Patient " + phone, phone, null, serviceId, null, day, LocalTime.of(12, 0), null, true));
                    successes.incrementAndGet();
                } catch (SlotUnavailableException e) {
                    conflicts.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE appointment_date = ? AND slot_active = TRUE", Long.class, day))
                .isEqualTo(1L);
    }
}
