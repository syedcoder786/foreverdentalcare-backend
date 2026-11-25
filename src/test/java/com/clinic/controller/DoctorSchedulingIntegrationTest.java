package com.clinic.controller;

import com.clinic.dto.request.AppointmentRequest;
import com.clinic.exception.SlotUnavailableException;
import com.clinic.service.AppointmentService;
import com.clinic.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Independent per-doctor calendars. Doctor A = seeded dentist (Mon-Sat 10:00-19:00, break 13:30-14:30).
 * Doctor B = Unani practitioner created per test (Mon-Sat 10:00-20:00, break 14:00-16:00).
 */
class DoctorSchedulingIntegrationTest extends IntegrationTestBase {

    @Autowired
    private AppointmentService appointmentService;

    private String token;
    private Long dentist;
    private Long dentalConsult;
    private Long rootCanal;
    private long unaniDoctor;
    private long unaniService;
    private LocalDate day;

    @BeforeEach
    void setUp() throws Exception {
        token = loginAndGetAccessToken();
        dentist = dentistId();
        dentalConsult = serviceIdByName("General Dental Consultation");
        rootCanal = serviceIdByName("Root Canal Treatment");
        long[] b = createUnaniDoctor(token, "10:00", "20:00", "14:00", "16:00");
        unaniDoctor = b[0];
        unaniService = b[1];
        day = nextWorkingDay();
    }

    private int book(Long doctorId, Long serviceId, String time, String phone) throws Exception {
        return postJson("/api/public/appointments", booking(doctorId, serviceId, day, time, phone)).getResponse().getStatus();
    }

    private List<String> availableSlots(long doctorId, Long serviceId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/public/doctors/{id}/available-slots", doctorId)
                .param("date", day.toString()).param("serviceId", String.valueOf(serviceId))).andReturn();
        List<String> times = new ArrayList<>();
        json(r).path("data").forEach(n -> times.add(n.path("time").asText()));
        return times;
    }

    // 1
    @Test
    void twoDifferentDoctorsCanBeBookedAtTheSameTime() throws Exception {
        assertThat(book(dentist, dentalConsult, "10:00", "+919000000001")).isEqualTo(201);
        assertThat(book(unaniDoctor, unaniService, "10:00", "+919000000002")).isEqualTo(201);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE appointment_time = '10:00:00'", Long.class))
                .isEqualTo(2L);
    }

    // 2
    @Test
    void sameDoctorCannotHaveOverlappingAppointments() throws Exception {
        // Root canal (60 min) occupies 10:00-11:00 in the dentist's calendar.
        assertThat(book(dentist, rootCanal, "10:00", "+919000000003")).isEqualTo(201);
        assertThat(book(dentist, dentalConsult, "10:30", "+919000000004")).isEqualTo(409);
        assertThat(book(dentist, dentalConsult, "10:00", "+919000000005")).isEqualTo(409);
        assertThat(book(dentist, dentalConsult, "11:00", "+919000000006")).isEqualTo(201);
        // ...while the other doctor is completely unaffected at 10:30.
        assertThat(book(unaniDoctor, unaniService, "10:30", "+919000000007")).isEqualTo(201);
        assertThat(availableSlots(dentist, dentalConsult)).doesNotContain("10:00", "10:30", "11:00");
    }

    // 3
    @Test
    void doctorCannotBeBookedOutsideWorkingHours() throws Exception {
        assertThat(book(dentist, dentalConsult, "09:00", "+919000000008")).isEqualTo(422);
        assertThat(book(unaniDoctor, unaniService, "20:00", "+919000000009")).isEqualTo(422);
        assertThat(book(unaniDoctor, unaniService, "10:10", "+919000000010")).isEqualTo(422); // off the slot grid
        // Sunday is the dentist's weekly off day.
        MvcResult sunday = postJson("/api/public/appointments", booking(dentist, dentalConsult, nextSunday(), "10:00", "+919000000011"));
        assertThat(sunday.getResponse().getStatus()).isEqualTo(422);
    }

    // 4
    @Test
    void breakTimesAreNotBookable() throws Exception {
        assertThat(book(unaniDoctor, unaniService, "14:30", "+919000000013")).isEqualTo(422);
        assertThat(book(dentist, dentalConsult, "13:30", "+919000000014")).isEqualTo(422);
        List<String> unaniSlots = availableSlots(unaniDoctor, unaniService);
        assertThat(unaniSlots).contains("13:30", "16:00").doesNotContain("14:00", "14:30", "15:30");
        // A 60-minute treatment cannot start at 13:00 for the dentist because it would run into the 13:30 break.
        assertThat(availableSlots(dentist, rootCanal)).contains("12:30").doesNotContain("13:00");
    }

    // 5
    @Test
    void blockedDatesAndTimesPreventBooking() throws Exception {
        // Full-day block for the Unani doctor only.
        mockMvc.perform(asAdmin(post("/api/admin/doctors/{id}/blocked-dates", unaniDoctor), token)
                        .content(objectMapper.writeValueAsString(Map.of("date", day.toString(), "reason", "Conference"))))
                .andExpect(status().isCreated());
        assertThat(book(unaniDoctor, unaniService, "10:00", "+919000000015")).isEqualTo(422);
        assertThat(book(dentist, dentalConsult, "10:00", "+919000000016")).isEqualTo(201);

        // Partial block 11:00-12:00 for the dentist.
        mockMvc.perform(asAdmin(post("/api/admin/doctors/{id}/blocked-dates", dentist), token)
                        .content(objectMapper.writeValueAsString(Map.of("date", day.toString(), "startTime", "11:00", "endTime", "12:00"))))
                .andExpect(status().isCreated());
        assertThat(book(dentist, dentalConsult, "11:30", "+919000000017")).isEqualTo(422);
        assertThat(book(dentist, dentalConsult, "12:00", "+919000000018")).isEqualTo(201);

        MvcResult closed = mockMvc.perform(get("/api/public/doctors/{id}/availability", unaniDoctor).param("date", day.toString())).andReturn();
        assertThat(json(closed).path("data").path("open").asBoolean()).isFalse();
        assertThat(json(closed).path("data").path("reason").asText()).isEqualTo("Conference");
    }

    @Test
    void clinicWideHolidayBlocksEveryDoctor() throws Exception {
        mockMvc.perform(asAdmin(post("/api/admin/holidays"), token)
                        .content(objectMapper.writeValueAsString(Map.of("date", day.toString(), "reason", "Public holiday"))))
                .andExpect(status().isCreated());
        assertThat(book(dentist, dentalConsult, "10:00", "+919000000019")).isEqualTo(422);
        assertThat(book(unaniDoctor, unaniService, "10:00", "+919000000020")).isEqualTo(422);
    }

    // 6
    @Test
    void cancelledAppointmentsReleaseTheirSlot() throws Exception {
        MvcResult first = postJson("/api/public/appointments", booking(dentist, dentalConsult, day, "15:00", "+919000000021"));
        long id = json(first).path("data").path("referenceId").asLong();
        assertThat(availableSlots(dentist, dentalConsult)).doesNotContain("15:00");

        mockMvc.perform(asAdmin(patch("/api/admin/appointments/{id}/status", id), token)
                        .content(objectMapper.writeValueAsString(Map.of("status", "CANCELLED"))))
                .andExpect(status().isOk());

        assertThat(availableSlots(dentist, dentalConsult)).contains("15:00");
        assertThat(book(dentist, dentalConsult, "15:00", "+919000000022")).isEqualTo(201);
    }

    // 7
    @Test
    void reschedulingMovesTheReservation() throws Exception {
        MvcResult first = postJson("/api/public/appointments", booking(dentist, dentalConsult, day, "15:00", "+919000000023"));
        long id = json(first).path("data").path("referenceId").asLong();

        mockMvc.perform(asAdmin(patch("/api/admin/appointments/{id}/reschedule", id), token)
                        .content(objectMapper.writeValueAsString(Map.of("appointmentDate", day.toString(), "appointmentTime", "16:00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointmentTime").value("16:00"))
                .andExpect(jsonPath("$.data.endTime").value("16:30"));

        List<String> slots = availableSlots(dentist, dentalConsult);
        assertThat(slots).contains("15:00").doesNotContain("16:00");
        assertThat(book(dentist, dentalConsult, "15:00", "+919000000024")).isEqualTo(201);
        assertThat(book(dentist, dentalConsult, "16:00", "+919000000025")).isEqualTo(409);
    }

    // 8
    @Test
    void concurrentRequestsCannotDoubleBookADoctor() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        Map<Long, AtomicInteger> perDoctor = new ConcurrentHashMap<>();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            // Half target the dentist, half the Unani doctor - same date and time for everyone.
            boolean dental = i % 2 == 0;
            Long doctorId = dental ? dentist : unaniDoctor;
            Long serviceId = dental ? dentalConsult : unaniService;
            String phone = String.format("+9191000000%02d", i);
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    appointmentService.requestAppointment(new AppointmentRequest(
                            "Patient " + phone, phone, null, serviceId, doctorId, day, LocalTime.of(12, 0), null, true));
                    successes.incrementAndGet();
                    perDoctor.computeIfAbsent(doctorId, k -> new AtomicInteger()).incrementAndGet();
                } catch (SlotUnavailableException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        // Exactly one booking per doctor wins; doctors never block each other.
        assertThat(successes.get()).isEqualTo(2);
        assertThat(rejected.get()).isEqualTo(threads - 2);
        assertThat(perDoctor.get(dentist).get()).isEqualTo(1);
        assertThat(perDoctor.get(unaniDoctor).get()).isEqualTo(1);
    }

    @Test
    void doctorOnlyReceivesBookingsForAssignedServices() throws Exception {
        assertThat(book(unaniDoctor, dentalConsult, "10:00", "+919000000026")).isEqualTo(422);
        assertThat(book(dentist, unaniService, "10:00", "+919000000027")).isEqualTo(422);

        MvcResult services = mockMvc.perform(get("/api/public/doctors/{id}/services", unaniDoctor)).andReturn();
        JsonNode list = json(services).path("data");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("name").asText()).isEqualTo("Test Unani Consultation");
    }

    @Test
    void servicesOutsideDoctorSpecialtiesCannotBeAssigned() throws Exception {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("fullName", "Test Unani Doctor");
        update.put("slug", "test-unani");
        update.put("professionalTitle", "Unani Physician (BUMS)");
        update.put("specialtyIds", List.of(specialtyId("unani-medicine")));
        update.put("serviceIds", List.of(unaniService, dentalConsult));
        mockMvc.perform(asAdmin(put("/api/admin/doctors/{id}", unaniDoctor), token).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.serviceIds").exists());
    }

    @Test
    void scheduleChangeForOneDoctorDoesNotAffectAnother() throws Exception {
        assertThat(book(dentist, dentalConsult, "10:00", "+919000000028")).isEqualTo(201);
        // Move the Unani doctor to evenings only (16:00-20:00).
        createUnaniDoctorScheduleEvening();
        assertThat(book(unaniDoctor, unaniService, "10:00", "+919000000029")).isEqualTo(422);
        assertThat(book(unaniDoctor, unaniService, "16:00", "+919000000030")).isEqualTo(201);
        assertThat(availableSlots(dentist, dentalConsult)).contains("10:30").doesNotContain("10:00");
    }

    private void createUnaniDoctorScheduleEvening() throws Exception {
        List<Map<String, Object>> weekly = new ArrayList<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) {
            weekly.add(Map.of("dayOfWeek", d.name(), "active", d != java.time.DayOfWeek.SUNDAY,
                    "startTime", "16:00", "endTime", "20:00", "slotDurationMinutes", 30));
        }
        mockMvc.perform(asAdmin(put("/api/admin/doctors/{id}/schedule", unaniDoctor), token)
                        .content(objectMapper.writeValueAsString(Map.of("weekly", weekly))))
                .andExpect(status().isOk());
    }

    @Test
    void specialHoursOverrideTheWeeklySchedule() throws Exception {
        List<Map<String, Object>> weekly = new ArrayList<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) {
            weekly.add(Map.of("dayOfWeek", d.name(), "active", d != java.time.DayOfWeek.SUNDAY,
                    "startTime", "10:00", "endTime", "20:00", "slotDurationMinutes", 30));
        }
        List<Map<String, Object>> special = List.of(Map.of("date", day.toString(), "startTime", "08:00", "endTime", "09:00",
                "slotDurationMinutes", 15, "note", "Early clinic"));
        mockMvc.perform(asAdmin(put("/api/admin/doctors/{id}/schedule", unaniDoctor), token)
                        .content(objectMapper.writeValueAsString(Map.of("weekly", weekly, "specialHours", special))))
                .andExpect(status().isOk());
        assertThat(availableSlots(unaniDoctor, unaniService)).containsExactly("08:00", "08:15", "08:30");
        assertThat(book(unaniDoctor, unaniService, "10:00", "+919000000031")).isEqualTo(422);
        assertThat(book(unaniDoctor, unaniService, "08:15", "+919000000032")).isEqualTo(201);
    }

    @Test
    void adminCalendarsShowEachDoctorSeparately() throws Exception {
        book(dentist, dentalConsult, "10:00", "+919000000033");
        book(unaniDoctor, unaniService, "10:00", "+919000000034");

        mockMvc.perform(asAdmin(get("/api/admin/doctors/{id}/day", unaniDoctor), token).param("date", day.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointments.length()").value(1))
                .andExpect(jsonPath("$.data.slots[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.data.breakStartTime").value("14:00"));

        mockMvc.perform(asAdmin(get("/api/admin/calendar"), token).param("from", day.toString()).param("to", day.plusDays(6).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appointments.length()").value(2));

        mockMvc.perform(asAdmin(get("/api/admin/appointments"), token).param("doctorId", String.valueOf(unaniDoctor)))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].doctor.id").value(unaniDoctor));

        mockMvc.perform(asAdmin(get("/api/admin/appointments"), token).param("specialtyId", String.valueOf(specialtyId("dentistry"))))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void adminCanCreateConfirmedAppointmentManually() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("doctorId", unaniDoctor);
        body.put("serviceId", unaniService);
        body.put("appointmentDate", day.toString());
        body.put("appointmentTime", "17:00");
        body.put("fullName", "Phone Patient");
        body.put("phone", "+919000000035");
        body.put("initialStatus", "CONFIRMED");
        mockMvc.perform(asAdmin(post("/api/admin/appointments"), token).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.doctor.id").value(unaniDoctor));
        assertThat(book(unaniDoctor, unaniService, "17:00", "+919000000036")).isEqualTo(409);
    }
}
