package com.clinic.controller;

import com.clinic.dto.WhatsAppDtos.WhatsAppTestRequest;
import com.clinic.dto.WhatsAppTemplateDtos.AudienceResponse;
import com.clinic.dto.WhatsAppTemplateDtos.CampaignRequest;
import com.clinic.dto.WhatsAppTemplateDtos.CampaignResponse;
import com.clinic.dto.WhatsAppTemplateDtos.MetaTemplateResponse;
import com.clinic.dto.WhatsAppTemplateDtos.TemplateNameRequest;
import com.clinic.dto.WhatsAppTemplateDtos.TemplateNameResponse;
import com.clinic.dto.WhatsAppTemplateDtos.WhatsAppTemplateRequest;
import com.clinic.dto.WhatsAppTemplateDtos.WhatsAppTemplateResponse;
import com.clinic.dto.response.ApiResponse;
import com.clinic.service.WhatsAppCampaignService;
import com.clinic.service.WhatsAppTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** WhatsApp message templates (automatic appointment messages, marketing) and marketing campaigns. */
@RestController
@RequestMapping("/api/admin/whatsapp")
@RequiredArgsConstructor
public class AdminWhatsAppMessagingController {

    private final WhatsAppTemplateService templateService;
    private final WhatsAppCampaignService campaignService;

    /** Template names the admin saved for reuse. */
    @GetMapping("/template-names")
    public ApiResponse<List<TemplateNameResponse>> templateNames() {
        return ApiResponse.ok(templateService.names());
    }

    @PostMapping("/template-names")
    public ResponseEntity<ApiResponse<TemplateNameResponse>> addTemplateName(@Valid @RequestBody TemplateNameRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Template name saved", templateService.addName(request)));
    }

    @DeleteMapping("/template-names/{id}")
    public ApiResponse<Void> deleteTemplateName(@PathVariable Long id) {
        templateService.deleteName(id);
        return ApiResponse.ok("Template name removed", null);
    }

    /** The saved template's text and placeholders, read from Meta. */
    @GetMapping("/template-names/{id}/details")
    public ApiResponse<MetaTemplateResponse> templateNameDetails(@PathVariable Long id) {
        return ApiResponse.ok(templateService.nameDetails(id));
    }

    @GetMapping("/templates")
    public ApiResponse<List<WhatsAppTemplateResponse>> templates() {
        return ApiResponse.ok(templateService.list());
    }

    /** Adds an appointment message (one per event) or a marketing template. */
    @PostMapping("/templates")
    public ResponseEntity<ApiResponse<WhatsAppTemplateResponse>> create(@Valid @RequestBody WhatsAppTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Template added", templateService.create(request)));
    }

    @PutMapping("/templates/{id}")
    public ApiResponse<WhatsAppTemplateResponse> update(@PathVariable Long id, @Valid @RequestBody WhatsAppTemplateRequest request) {
        return ApiResponse.ok("Template saved", templateService.update(id, request));
    }

    @DeleteMapping("/templates/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return ApiResponse.ok("Template removed", null);
    }

    @PostMapping("/templates/{id}/test")
    public ApiResponse<Void> test(@PathVariable Long id, @Valid @RequestBody WhatsAppTestRequest request) {
        return ApiResponse.ok(templateService.sendTest(id, request.phone()), null);
    }

    @GetMapping("/audience")
    public ApiResponse<AudienceResponse> audience() {
        return ApiResponse.ok(campaignService.audience());
    }

    @GetMapping("/campaigns")
    public ApiResponse<List<CampaignResponse>> campaigns() {
        return ApiResponse.ok(campaignService.recent());
    }

    @PostMapping("/campaigns")
    public ResponseEntity<ApiResponse<CampaignResponse>> startCampaign(@Valid @RequestBody CampaignRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Campaign started", campaignService.start(request.templateId())));
    }
}
