package com.clinic.service;

import com.clinic.dto.CatalogDtos.ServiceDetail;
import com.clinic.dto.request.ServiceRequest;
import com.clinic.dto.response.ServiceResponse;
import com.clinic.entity.DentalService;
import com.clinic.entity.Doctor;
import com.clinic.entity.Specialty;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.mapper.ServiceMapper;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.DentalServiceRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SpecialtyRepository;
import com.clinic.util.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Manages treatments: their specialty, the doctors allowed to perform them, fees, durations and images. */
@Service
@RequiredArgsConstructor
public class DentalServiceCatalogService {

    public enum DeleteOutcome { DELETED, DEACTIVATED }

    private final DentalServiceRepository repository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceMapper mapper;
    private final DoctorMapper doctorMapper;
    private final AuditService audit;

    /** Public catalogue: active services in active specialties that at least one active doctor offers. */
    @Transactional(readOnly = true)
    public List<ServiceResponse> listActive(Long specialtyId, Long doctorId) {
        return repository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .filter(s -> s.getSpecialty().isActive())
                .filter(s -> specialtyId == null || s.getSpecialty().getId().equals(specialtyId))
                .filter(s -> s.getDoctors().stream().anyMatch(d -> d.isActive() && (doctorId == null || d.getId().equals(doctorId))))
                .map(s -> mapper.toResponse(s, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public ServiceDetail getPublicBySlug(String slug) {
        DentalService s = repository.findBySlug(slug)
                .filter(x -> x.isActive() && x.getSpecialty().isActive())
                .orElseThrow(() -> new ResourceNotFoundException("Service", slug));
        List<Doctor> doctors = s.getDoctors().stream().filter(Doctor::isActive).toList();
        return new ServiceDetail(mapper.toResponse(s, true), doctors.stream().map(doctorMapper::toPublic).toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listAll() {
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceResponse get(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public ServiceResponse create(ServiceRequest request) {
        if (repository.existsByNameIgnoreCase(request.name().trim())) {
            throw new ConflictException("A service with this name already exists");
        }
        DentalService service = new DentalService();
        mapper.apply(request, service);
        service.setSpecialty(loadSpecialty(request.specialtyId()));
        service.setSlug(Slugs.unique(request.slug(), request.name(), repository::existsBySlug));
        DentalService saved = repository.save(service);
        if (request.doctorIds() != null) {
            assignDoctors(saved, request.doctorIds());
        }
        audit.record("CREATE", "Service", saved.getId(), saved.getName());
        return mapper.toResponse(saved);
    }

    @Transactional
    public ServiceResponse update(Long id, ServiceRequest request) {
        DentalService service = load(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(request.name().trim(), id)) {
            throw new ConflictException("A service with this name already exists");
        }
        mapper.apply(request, service);
        service.setSpecialty(loadSpecialty(request.specialtyId()));
        if (request.slug() != null && !request.slug().isBlank() && !request.slug().equals(service.getSlug())) {
            if (repository.existsBySlugAndIdNot(request.slug(), id)) {
                throw new ConflictException("This URL slug is already used by another service");
            }
            service.setSlug(request.slug());
        }
        if (request.doctorIds() != null) {
            assignDoctors(service, request.doctorIds());
        } else {
            // Specialty may have changed: existing doctors must still be qualified for it.
            service.getDoctors().forEach(DoctorService::requireServicesWithinSpecialties);
        }
        audit.record("UPDATE", "Service", id, service.getName());
        return mapper.toResponse(repository.save(service));
    }

    /**
     * Hard-deletes a service that has never been booked. Services referenced by appointments are deactivated instead
     * so that appointment history remains intact.
     */
    @Transactional
    public DeleteOutcome delete(Long id) {
        DentalService service = load(id);
        if (appointmentRepository.existsByServiceId(id)) {
            service.setActive(false);
            audit.record("DEACTIVATE", "Service", id, service.getName());
            return DeleteOutcome.DEACTIVATED;
        }
        for (Doctor d : new HashSet<>(service.getDoctors())) {
            d.getServices().remove(service);
        }
        repository.delete(service);
        audit.record("DELETE", "Service", id, service.getName());
        return DeleteOutcome.DELETED;
    }

    /** Doctor.services is the owning side of the many-to-many, so assignments are written through the doctors. */
    private void assignDoctors(DentalService service, Set<Long> doctorIds) {
        List<Doctor> target = doctorRepository.findAllById(doctorIds);
        if (target.size() != doctorIds.size()) {
            throw new BusinessException("doctorIds", "One or more selected doctors do not exist");
        }
        for (Doctor d : new HashSet<>(service.getDoctors())) {
            if (!doctorIds.contains(d.getId())) {
                d.getServices().remove(service);
                service.getDoctors().remove(d);
            }
        }
        for (Doctor d : target) {
            if (d.getSpecialties().stream().noneMatch(s -> s.getId().equals(service.getSpecialty().getId()))) {
                throw new BusinessException("doctorIds", d.getFullName() + " does not have the "
                        + service.getSpecialty().getName() + " specialty. Add it to the doctor's profile first.");
            }
            d.getServices().add(service);
            service.getDoctors().add(d);
        }
    }

    private Specialty loadSpecialty(Long id) {
        return specialtyRepository.findById(id).orElseThrow(() -> new BusinessException("specialtyId", "Selected specialty does not exist"));
    }

    private DentalService load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service", id));
    }
}
