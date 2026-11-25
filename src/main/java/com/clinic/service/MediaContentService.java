package com.clinic.service;

import com.clinic.dto.ContentDtos.*;
import com.clinic.entity.GalleryImage;
import com.clinic.entity.Testimonial;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.ContentMapper;
import com.clinic.repository.GalleryImageRepository;
import com.clinic.repository.TestimonialRepository;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Testimonials and gallery images. */
@Service
@RequiredArgsConstructor
public class MediaContentService {

    private final TestimonialRepository testimonialRepository;
    private final GalleryImageRepository galleryRepository;
    private final ContentMapper mapper;
    private final AuditService audit;

    // ---------- testimonials ----------

    /** Public: only published testimonials with recorded patient consent. */
    @Transactional(readOnly = true)
    public List<TestimonialResponse> publishedTestimonials() {
        return testimonialRepository.findAllByPublishedTrueAndConsentConfirmedTrueOrderByDisplayOrderAscIdAsc().stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonialResponse> allTestimonials() {
        return testimonialRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public TestimonialResponse saveTestimonial(Long id, TestimonialRequest r) {
        if (r.published() && !r.consentConfirmed()) {
            throw new BusinessException("consentConfirmed",
                    "Record the patient's permission before publishing this testimonial");
        }
        Testimonial t = id == null ? new Testimonial()
                : testimonialRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Testimonial", id));
        t.setPatientName(r.patientName().trim());
        t.setContext(ValidationPatterns.trimToNull(r.context()));
        t.setQuote(r.quote().trim());
        t.setRating(r.rating());
        t.setConsentConfirmed(r.consentConfirmed());
        t.setPublished(r.published());
        t.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        Testimonial saved = testimonialRepository.save(t);
        audit.record(id == null ? "CREATE" : "UPDATE", "Testimonial", saved.getId(),
                (saved.isPublished() ? "published" : "draft") + " - " + saved.getPatientName());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteTestimonial(Long id) {
        Testimonial t = testimonialRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Testimonial", id));
        testimonialRepository.delete(t);
        audit.record("DELETE", "Testimonial", id, t.getPatientName());
    }

    // ---------- gallery ----------

    @Transactional(readOnly = true)
    public List<GalleryResponse> publicGallery() {
        return galleryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GalleryResponse> allGallery() {
        return galleryRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public GalleryResponse saveGalleryImage(Long id, GalleryRequest r) {
        GalleryImage g = id == null ? new GalleryImage()
                : galleryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gallery image", id));
        g.setImageUrl(r.imageUrl().trim());
        g.setCaption(ValidationPatterns.trimToNull(r.caption()));
        g.setCategory(ValidationPatterns.trimToNull(r.category()));
        g.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
        if (r.active() != null) {
            g.setActive(r.active());
        }
        GalleryImage saved = galleryRepository.save(g);
        audit.record(id == null ? "CREATE" : "UPDATE", "GalleryImage", saved.getId(), saved.getCaption());
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteGalleryImage(Long id) {
        GalleryImage g = galleryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gallery image", id));
        galleryRepository.delete(g);
        audit.record("DELETE", "GalleryImage", id, g.getCaption());
    }
}
