package com.clinic.controller;

import com.clinic.dto.CatalogDtos.ServiceDetail;
import com.clinic.dto.CatalogDtos.SpecialtyDetail;
import com.clinic.dto.CatalogDtos.SpecialtyResponse;
import com.clinic.dto.ContentDtos.*;
import com.clinic.dto.DoctorDtos.DoctorPublicResponse;
import com.clinic.dto.DoctorDtos.ServiceRef;
import com.clinic.dto.request.AppointmentRequest;
import com.clinic.dto.response.*;
import com.clinic.dto.response.ScheduleDtos.DayAvailability;
import com.clinic.dto.response.ScheduleDtos.SlotView;
import com.clinic.exception.BusinessException;
import com.clinic.service.*;
import com.clinic.storage.FileStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/** Unauthenticated endpoints used by the public website. Returns only active / published content, no patient data. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicController {

    private final DentalServiceCatalogService catalogService;
    private final SpecialtyService specialtyService;
    private final DoctorService doctorService;
    private final CalendarService calendarService;
    private final ClinicSettingsService settingsService;
    private final ContentService contentService;
    private final MediaContentService mediaService;
    private final AppointmentService appointmentService;
    private final FileStorage fileStorage;

    // ---------- site & pages ----------

    @GetMapping("/clinic")
    public ApiResponse<ClinicSettingsResponse> clinic() {
        return ApiResponse.ok(settingsService.getSettingsResponse());
    }

    @GetMapping("/site")
    public ApiResponse<SiteResponse> site() {
        return ApiResponse.ok(contentService.site());
    }

    @GetMapping("/pages/{pageKey}")
    public ApiResponse<PageContent> page(@PathVariable String pageKey) {
        return ApiResponse.ok(contentService.publicPage(pageKey));
    }

    @GetMapping("/testimonials")
    public ApiResponse<List<TestimonialResponse>> testimonials() {
        return ApiResponse.ok(mediaService.publishedTestimonials());
    }

    @GetMapping("/gallery")
    public ApiResponse<List<GalleryResponse>> gallery() {
        return ApiResponse.ok(mediaService.publicGallery());
    }

    // ---------- catalogue ----------

    @GetMapping("/specialties")
    public ApiResponse<List<SpecialtyResponse>> specialties() {
        return ApiResponse.ok(specialtyService.listPublic());
    }

    @GetMapping("/specialties/{slug}")
    public ApiResponse<SpecialtyDetail> specialty(@PathVariable String slug) {
        return ApiResponse.ok(specialtyService.getPublicBySlug(slug));
    }

    @GetMapping("/services")
    public ApiResponse<List<ServiceResponse>> services(@RequestParam(required = false) Long specialtyId,
                                                       @RequestParam(required = false) Long doctorId) {
        return ApiResponse.ok(catalogService.listActive(specialtyId, doctorId));
    }

    @GetMapping("/services/{slug}")
    public ApiResponse<ServiceDetail> service(@PathVariable String slug) {
        return ApiResponse.ok(catalogService.getPublicBySlug(slug));
    }

    // ---------- doctors & availability ----------

    @GetMapping("/doctors")
    public ApiResponse<List<DoctorPublicResponse>> doctors(@RequestParam(required = false) Long specialtyId) {
        return ApiResponse.ok(doctorService.listPublic(specialtyId));
    }

    @GetMapping("/doctors/{doctorId:\\d+}")
    public ApiResponse<DoctorPublicResponse> doctor(@PathVariable Long doctorId) {
        return ApiResponse.ok(doctorService.getPublic(doctorId));
    }

    @GetMapping("/doctors/by-slug/{slug}")
    public ApiResponse<DoctorPublicResponse> doctorBySlug(@PathVariable String slug) {
        return ApiResponse.ok(doctorService.getPublicBySlug(slug));
    }

    /** Only services assigned to this doctor. */
    @GetMapping("/doctors/{doctorId}/services")
    public ApiResponse<List<ServiceRef>> doctorServices(@PathVariable Long doctorId,
                                                        @RequestParam(required = false) Long specialtyId) {
        return ApiResponse.ok(doctorService.publicServices(doctorId, specialtyId));
    }

    /** The doctor's day: hours, break and every slot with an available flag. */
    @GetMapping("/doctors/{doctorId}/availability")
    public ApiResponse<DayAvailability> doctorAvailability(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long serviceId) {
        return ApiResponse.ok(calendarService.availability(doctorId, date, serviceId));
    }

    /** Only the bookable slots for the doctor (and service duration) on that date. */
    @GetMapping("/doctors/{doctorId}/available-slots")
    public ApiResponse<List<SlotView>> availableSlots(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long serviceId) {
        return ApiResponse.ok(calendarService.availableSlots(doctorId, date, serviceId));
    }

    /** Backwards-compatible availability endpoint; requires doctorId (or a service offered by exactly one doctor). */
    @GetMapping("/availability")
    public ApiResponse<DayAvailability> availability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) Long serviceId) {
        Long resolved = doctorId;
        if (resolved == null && serviceId != null) {
            List<ServiceResponse.DoctorSummary> doctors = catalogService.listActive(null, null).stream()
                    .filter(s -> s.id().equals(serviceId)).findFirst().map(ServiceResponse::doctors).orElse(List.of());
            if (doctors.size() == 1) {
                resolved = doctors.getFirst().id();
            }
        }
        if (resolved == null) {
            throw new BusinessException("doctorId", "Please choose a doctor");
        }
        return ApiResponse.ok(calendarService.availability(resolved, date, serviceId));
    }

    // ---------- booking ----------

    @PostMapping("/appointments")
    public ResponseEntity<ApiResponse<BookingConfirmationResponse>> requestAppointment(
            @Valid @RequestBody AppointmentRequest request) {
        BookingConfirmationResponse result = appointmentService.requestAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Your appointment request has been received. The clinic will contact you to confirm.", result));
    }

    // ---------- uploaded images ----------

    @GetMapping("/files/{fileName}")
    public ResponseEntity<Resource> file(@PathVariable String fileName) {
        return fileStorage.load(fileName)
                .map(resource -> ResponseEntity.ok()
                        .contentType(MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
                        .header("X-Content-Type-Options", "nosniff")
                        .body(resource))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
