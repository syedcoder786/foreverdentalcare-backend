package com.clinic.dto;

import com.clinic.dto.response.ScheduleDtos;
import com.clinic.dto.response.ServiceResponse;
import com.clinic.util.Slugs;
import com.clinic.util.ValidationPatterns;
import jakarta.validation.constraints.*;

import java.util.List;

/** Specialty request/response models. */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    public record SpecialtyRequest(
            @NotBlank(message = "Name is required") @Size(max = 100) String name,
            @Pattern(regexp = "^$|" + Slugs.PATTERN, message = "Slug may contain lowercase letters, numbers and dashes")
            @Size(max = 120) String slug,
            @Size(max = 2000) String description,
            @Size(max = 50) String icon,
            @Size(max = 500) @Pattern(regexp = ValidationPatterns.IMAGE_URL, message = "Upload an image or use an https URL")
            String imageUrl,
            @Min(0) Integer displayOrder,
            Boolean active) {
    }

    public record SpecialtyResponse(Long id, String name, String slug, String description, String icon,
                                    String imageUrl, Integer displayOrder, boolean active,
                                    long doctorCount, long serviceCount) {
    }

    /** Public specialty page: the specialty with its active doctors and services. */
    public record SpecialtyDetail(SpecialtyResponse specialty, List<DoctorDtos.DoctorPublicResponse> doctors,
                                  List<ServiceResponse> services) {
    }

    /** Public service page. */
    public record ServiceDetail(ServiceResponse service, List<DoctorDtos.DoctorPublicResponse> doctors) {
    }

    public record DoctorAppointments(ScheduleDtos.DoctorRef doctor,
                                     List<com.clinic.dto.response.AppointmentResponse> appointments) {
    }
}
