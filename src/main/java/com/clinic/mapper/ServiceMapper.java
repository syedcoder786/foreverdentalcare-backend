package com.clinic.mapper;

import com.clinic.dto.request.ServiceRequest;
import com.clinic.dto.response.ServiceResponse;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.util.ValidationPatterns;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ServiceMapper {

    private static final String DETAILS_SEPARATOR = "|";

    /** @param publicView when true only active doctors are listed */
    public ServiceResponse toResponse(DentalService s, boolean publicView) {
        List<ServiceResponse.DoctorSummary> doctors = s.getDoctors().stream()
                .filter(d -> !publicView || d.isActive())
                .map(ServiceMapper::doctorSummary)
                .toList();
        return new ServiceResponse(
                s.getId(),
                s.getName(),
                s.getSlug(),
                s.getDescription(),
                splitDetails(s.getDetails()),
                s.getIcon(),
                s.getImageUrl(),
                s.getConsultationFee(),
                s.getEstimatedDuration(),
                s.getDisplayOrder(),
                s.isActive(),
                new ServiceResponse.SpecialtySummary(s.getSpecialty().getId(), s.getSpecialty().getName(), s.getSpecialty().getSlug()),
                doctors);
    }

    public ServiceResponse toResponse(DentalService s) {
        return toResponse(s, false);
    }

    private static ServiceResponse.DoctorSummary doctorSummary(Doctor d) {
        return new ServiceResponse.DoctorSummary(d.getId(), d.getFullName(), d.getSlug(), d.getProfessionalTitle());
    }

    /** Copies simple fields; specialty, slug and doctor assignments are handled by the service layer. */
    public void apply(ServiceRequest req, DentalService target) {
        target.setName(req.name().trim());
        target.setDescription(req.description().trim());
        target.setDetails(joinDetails(req.details()));
        target.setIcon(ValidationPatterns.trimToNull(req.icon()));
        target.setImageUrl(ValidationPatterns.trimToNull(req.imageUrl()));
        target.setConsultationFee(req.consultationFee());
        target.setEstimatedDuration(req.estimatedDuration());
        target.setDisplayOrder(req.displayOrder() == null ? 0 : req.displayOrder());
        if (req.active() != null) {
            target.setActive(req.active());
        }
    }

    private static List<String> splitDetails(String details) {
        if (details == null || details.isBlank()) {
            return List.of();
        }
        return Arrays.stream(details.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String joinDetails(List<String> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        String joined = details.stream()
                .map(d -> d.replace(DETAILS_SEPARATOR, "/").trim())
                .filter(d -> !d.isEmpty())
                .collect(Collectors.joining(DETAILS_SEPARATOR));
        return joined.isEmpty() ? null : joined;
    }
}
