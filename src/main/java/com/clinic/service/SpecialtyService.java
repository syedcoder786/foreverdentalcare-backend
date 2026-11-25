package com.clinic.service;

import com.clinic.dto.CatalogDtos.SpecialtyDetail;
import com.clinic.dto.CatalogDtos.SpecialtyRequest;
import com.clinic.dto.CatalogDtos.SpecialtyResponse;
import com.clinic.entity.Doctor;
import com.clinic.entity.Specialty;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.DoctorMapper;
import com.clinic.mapper.ServiceMapper;
import com.clinic.repository.DentalServiceRepository;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.SpecialtyRepository;
import com.clinic.util.Slugs;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpecialtyService {

    public enum DeleteOutcome { DELETED, DEACTIVATED }

    private final SpecialtyRepository repository;
    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository serviceRepository;
    private final DoctorMapper doctorMapper;
    private final ServiceMapper serviceMapper;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listPublic() {
        List<Doctor> doctors = doctorRepository.findAllByActiveTrueOrderByDisplayOrderAscFullNameAsc();
        return repository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(s -> toResponse(s, doctors))
                .filter(s -> s.doctorCount() > 0 || s.serviceCount() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SpecialtyResponse> listAll() {
        List<Doctor> doctors = doctorRepository.findAllByOrderByDisplayOrderAscFullNameAsc();
        return repository.findAllByOrderByDisplayOrderAscNameAsc().stream().map(s -> toResponse(s, doctors)).toList();
    }

    @Transactional(readOnly = true)
    public SpecialtyDetail getPublicBySlug(String slug) {
        Specialty specialty = repository.findBySlug(slug).filter(Specialty::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Specialty", slug));
        List<Doctor> doctors = doctorRepository.findAllByActiveTrueOrderByDisplayOrderAscFullNameAsc().stream()
                .filter(d -> d.getSpecialties().stream().anyMatch(s -> s.getId().equals(specialty.getId())))
                .toList();
        var services = serviceRepository.findAllByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .filter(s -> s.getSpecialty().getId().equals(specialty.getId()))
                .map(s -> serviceMapper.toResponse(s, true))
                .toList();
        return new SpecialtyDetail(toResponse(specialty, doctors), doctors.stream().map(doctorMapper::toPublic).toList(), services);
    }

    @Transactional
    public SpecialtyResponse create(SpecialtyRequest r) {
        if (repository.existsByNameIgnoreCase(r.name().trim())) {
            throw new ConflictException("A specialty with this name already exists");
        }
        Specialty s = new Specialty();
        apply(r, s);
        s.setSlug(Slugs.unique(r.slug(), r.name(), repository::existsBySlug));
        Specialty saved = repository.save(s);
        audit.record("CREATE", "Specialty", saved.getId(), saved.getName());
        return toResponse(saved, List.of());
    }

    @Transactional
    public SpecialtyResponse update(Long id, SpecialtyRequest r) {
        Specialty s = load(id);
        if (repository.existsByNameIgnoreCaseAndIdNot(r.name().trim(), id)) {
            throw new ConflictException("A specialty with this name already exists");
        }
        apply(r, s);
        if (r.slug() != null && !r.slug().isBlank() && !r.slug().equals(s.getSlug())) {
            if (repository.existsBySlugAndIdNot(r.slug(), id)) {
                throw new ConflictException("This URL slug is already used by another specialty");
            }
            s.setSlug(r.slug());
        }
        audit.record("UPDATE", "Specialty", id, s.getName());
        return toResponse(repository.save(s), doctorRepository.findAllByOrderByDisplayOrderAscFullNameAsc());
    }

    /** Deletes an unused specialty; one that has services or doctors is deactivated to keep history intact. */
    @Transactional
    public DeleteOutcome delete(Long id) {
        Specialty s = load(id);
        if (serviceRepository.existsBySpecialtyId(id) || doctorRepository.existsBySpecialtyId(id)) {
            s.setActive(false);
            audit.record("DEACTIVATE", "Specialty", id, s.getName());
            return DeleteOutcome.DEACTIVATED;
        }
        repository.delete(s);
        audit.record("DELETE", "Specialty", id, s.getName());
        return DeleteOutcome.DELETED;
    }

    Specialty load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Specialty", id));
    }

    private void apply(SpecialtyRequest r, Specialty s) {
        s.setName(r.name().trim());
        s.setDescription(ValidationPatterns.trimToNull(r.description()));
        s.setIcon(ValidationPatterns.trimToNull(r.icon()));
        s.setImageUrl(ValidationPatterns.trimToNull(r.imageUrl()));
        s.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        if (r.active() != null) {
            s.setActive(r.active());
        }
    }

    private SpecialtyResponse toResponse(Specialty s, List<Doctor> doctors) {
        long doctorCount = doctors.stream()
                .filter(d -> d.getSpecialties().stream().anyMatch(x -> x.getId().equals(s.getId())))
                .count();
        long serviceCount = serviceRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .filter(x -> x.getSpecialty().getId().equals(s.getId()) && x.isActive())
                .count();
        return new SpecialtyResponse(s.getId(), s.getName(), s.getSlug(), s.getDescription(), s.getIcon(),
                s.getImageUrl(), s.getDisplayOrder(), s.isActive(), doctorCount, serviceCount);
    }
}
