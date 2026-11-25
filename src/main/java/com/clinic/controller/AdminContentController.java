package com.clinic.controller;

import com.clinic.dto.ContentDtos.*;
import com.clinic.dto.response.ApiResponse;
import com.clinic.service.ContentService;
import com.clinic.service.MediaContentService;
import com.clinic.storage.ImageUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Website content management: navigation, page sections, SEO, testimonials, gallery and image uploads. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminContentController {

    private final ContentService contentService;
    private final MediaContentService mediaService;
    private final ImageUploadService uploadService;

    // ---------- navigation ----------
    @GetMapping("/navigation")
    public ApiResponse<List<NavigationItemDto>> navigation() {
        return ApiResponse.ok(contentService.navigation());
    }

    @PutMapping("/navigation")
    public ApiResponse<List<NavigationItemDto>> saveNavigation(@Valid @RequestBody NavigationRequest request) {
        return ApiResponse.ok("Navigation saved", contentService.replaceNavigation(request));
    }

    // ---------- page sections ----------
    @GetMapping("/pages/{pageKey}/sections")
    public ApiResponse<List<SectionResponse>> sections(@PathVariable String pageKey) {
        return ApiResponse.ok(contentService.sections(pageKey));
    }

    @PostMapping("/pages/{pageKey}/sections")
    public ResponseEntity<ApiResponse<SectionResponse>> createSection(@PathVariable String pageKey,
                                                                      @Valid @RequestBody SectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Section added", contentService.createSection(pageKey, request)));
    }

    @PutMapping("/sections/{id}")
    public ApiResponse<SectionResponse> updateSection(@PathVariable Long id, @Valid @RequestBody SectionRequest request) {
        return ApiResponse.ok("Section saved", contentService.updateSection(id, request));
    }

    @DeleteMapping("/sections/{id}")
    public ApiResponse<Void> deleteSection(@PathVariable Long id) {
        contentService.deleteSection(id);
        return ApiResponse.ok("Section deleted", null);
    }

    // ---------- SEO ----------
    @GetMapping("/seo")
    public ApiResponse<List<SeoResponse>> seo() {
        return ApiResponse.ok(contentService.allSeo());
    }

    @PutMapping("/seo/{pageKey}")
    public ApiResponse<SeoResponse> saveSeo(@PathVariable String pageKey, @Valid @RequestBody SeoRequest request) {
        return ApiResponse.ok("SEO settings saved", contentService.updateSeo(pageKey, request));
    }

    // ---------- testimonials ----------
    @GetMapping("/testimonials")
    public ApiResponse<List<TestimonialResponse>> testimonials() {
        return ApiResponse.ok(mediaService.allTestimonials());
    }

    @PostMapping("/testimonials")
    public ResponseEntity<ApiResponse<TestimonialResponse>> createTestimonial(@Valid @RequestBody TestimonialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Testimonial saved", mediaService.saveTestimonial(null, request)));
    }

    @PutMapping("/testimonials/{id}")
    public ApiResponse<TestimonialResponse> updateTestimonial(@PathVariable Long id, @Valid @RequestBody TestimonialRequest request) {
        return ApiResponse.ok("Testimonial saved", mediaService.saveTestimonial(id, request));
    }

    @DeleteMapping("/testimonials/{id}")
    public ApiResponse<Void> deleteTestimonial(@PathVariable Long id) {
        mediaService.deleteTestimonial(id);
        return ApiResponse.ok("Testimonial deleted", null);
    }

    // ---------- gallery ----------
    @GetMapping("/gallery")
    public ApiResponse<List<GalleryResponse>> gallery() {
        return ApiResponse.ok(mediaService.allGallery());
    }

    @PostMapping("/gallery")
    public ResponseEntity<ApiResponse<GalleryResponse>> addGallery(@Valid @RequestBody GalleryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Image added", mediaService.saveGalleryImage(null, request)));
    }

    @PutMapping("/gallery/{id}")
    public ApiResponse<GalleryResponse> updateGallery(@PathVariable Long id, @Valid @RequestBody GalleryRequest request) {
        return ApiResponse.ok("Image updated", mediaService.saveGalleryImage(id, request));
    }

    @DeleteMapping("/gallery/{id}")
    public ApiResponse<Void> deleteGallery(@PathVariable Long id) {
        mediaService.deleteGalleryImage(id);
        return ApiResponse.ok("Image deleted", null);
    }

    // ---------- uploads ----------
    @PostMapping(value = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadResponse>> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Image uploaded", uploadService.upload(file)));
    }
}
