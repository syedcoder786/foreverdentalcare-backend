package com.clinic.service;

import com.clinic.dto.ContentDtos.*;
import com.clinic.entity.NavigationItem;
import com.clinic.entity.PageSeo;
import com.clinic.entity.WebsiteSection;
import com.clinic.exception.BusinessException;
import com.clinic.exception.ConflictException;
import com.clinic.exception.ResourceNotFoundException;
import com.clinic.mapper.ContentMapper;
import com.clinic.repository.NavigationItemRepository;
import com.clinic.repository.PageSeoRepository;
import com.clinic.repository.WebsiteSectionRepository;
import com.clinic.util.ValidationPatterns;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Website content management: navigation, page sections and per-page SEO. */
@Service
@RequiredArgsConstructor
public class ContentService {

    public static final List<String> PAGE_KEYS = List.of("HOME", "ABOUT", "DOCTORS", "SPECIALTIES", "SERVICES", "CONTACT", "BOOK");

    private final NavigationItemRepository navigationRepository;
    private final WebsiteSectionRepository sectionRepository;
    private final PageSeoRepository seoRepository;
    private final ClinicSettingsService settingsService;
    private final ContentMapper mapper;
    private final AuditService audit;

    // ---------- public ----------

    @Transactional(readOnly = true)
    public SiteResponse site() {
        return new SiteResponse(settingsService.getSettingsResponse(),
                navigationRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc().stream().map(mapper::toDto).toList());
    }

    /** Only active sections are returned publicly. */
    @Transactional(readOnly = true)
    public PageContent publicPage(String pageKey) {
        String key = requirePageKey(pageKey);
        return new PageContent(key,
                sectionRepository.findAllByPageKeyAndActiveTrueOrderByDisplayOrderAscIdAsc(key).stream().map(mapper::toResponse).toList(),
                seo(key));
    }

    // ---------- navigation ----------

    @Transactional(readOnly = true)
    public List<NavigationItemDto> navigation() {
        return navigationRepository.findAllByOrderByDisplayOrderAscIdAsc().stream().map(mapper::toDto).toList();
    }

    /** Replaces the whole menu (simple ordered-list editor). */
    @Transactional
    public List<NavigationItemDto> replaceNavigation(NavigationRequest request) {
        Map<Long, NavigationItem> existing = new HashMap<>();
        navigationRepository.findAll().forEach(n -> existing.put(n.getId(), n));
        Set<Long> kept = new HashSet<>();
        int order = 1;
        for (NavigationItemDto dto : request.items()) {
            NavigationItem item = dto.id() != null && existing.containsKey(dto.id()) ? existing.get(dto.id()) : new NavigationItem();
            item.setLabel(dto.label().trim());
            item.setUrl(dto.url().trim());
            item.setDisplayOrder(dto.displayOrder() == null ? order : dto.displayOrder());
            item.setActive(dto.active());
            item.setOpenInNewTab(dto.openInNewTab());
            NavigationItem saved = navigationRepository.save(item);
            kept.add(saved.getId());
            order++;
        }
        existing.values().stream().filter(n -> !kept.contains(n.getId())).forEach(navigationRepository::delete);
        audit.record("UPDATE", "Navigation", null, request.items().size() + " menu items");
        return navigation();
    }

    // ---------- sections ----------

    @Transactional(readOnly = true)
    public List<SectionResponse> sections(String pageKey) {
        return sectionRepository.findAllByPageKeyOrderByDisplayOrderAscIdAsc(requirePageKey(pageKey)).stream()
                .map(mapper::toResponse).toList();
    }

    @Transactional
    public SectionResponse createSection(String pageKey, SectionRequest r) {
        String key = requirePageKey(pageKey);
        if (r.sectionKey() == null || r.sectionKey().isBlank()) {
            throw new BusinessException("sectionKey", "A section key is required for new sections");
        }
        if (sectionRepository.existsByPageKeyAndSectionKey(key, r.sectionKey())) {
            throw new ConflictException("This page already has a section with that key");
        }
        WebsiteSection s = new WebsiteSection();
        s.setPageKey(key);
        s.setSectionKey(r.sectionKey());
        s.setDisplayOrder(100);
        mapper.apply(r, s);
        WebsiteSection saved = sectionRepository.save(s);
        audit.record("CREATE", "WebsiteSection", saved.getId(), key + ":" + saved.getSectionKey());
        return mapper.toResponse(saved);
    }

    @Transactional
    public SectionResponse updateSection(Long id, SectionRequest r) {
        WebsiteSection s = sectionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section", id));
        mapper.apply(r, s);
        audit.record("UPDATE", "WebsiteSection", id, s.getPageKey() + ":" + s.getSectionKey());
        return mapper.toResponse(sectionRepository.saveAndFlush(s));
    }

    /** Built-in sections can only be hidden (deactivated); custom sections can be deleted. */
    @Transactional
    public void deleteSection(Long id) {
        WebsiteSection s = sectionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Section", id));
        if (ContentMapper.isCore(s.getPageKey(), s.getSectionKey())) {
            throw new BusinessException("sectionKey", "Built-in sections cannot be deleted. Hide them instead.");
        }
        sectionRepository.delete(s);
        audit.record("DELETE", "WebsiteSection", id, s.getPageKey() + ":" + s.getSectionKey());
    }

    // ---------- SEO ----------

    @Transactional(readOnly = true)
    public List<SeoResponse> allSeo() {
        Map<String, SeoResponse> byKey = new HashMap<>();
        seoRepository.findAllByOrderByPageKeyAsc().forEach(p -> byKey.put(p.getPageKey(), mapper.toResponse(p)));
        return PAGE_KEYS.stream().map(k -> byKey.getOrDefault(k, new SeoResponse(k, null, null, null, true))).toList();
    }

    @Transactional
    public SeoResponse updateSeo(String pageKey, SeoRequest r) {
        String key = requirePageKey(pageKey);
        PageSeo seo = seoRepository.findByPageKey(key).orElseGet(() -> {
            PageSeo created = new PageSeo();
            created.setPageKey(key);
            return created;
        });
        seo.setMetaTitle(ValidationPatterns.trimToNull(r.metaTitle()));
        seo.setMetaDescription(ValidationPatterns.trimToNull(r.metaDescription()));
        seo.setOgImageUrl(ValidationPatterns.trimToNull(r.ogImageUrl()));
        seo.setIndexable(r.indexable());
        audit.record("UPDATE", "PageSeo", key, r.indexable() ? "indexable" : "noindex");
        return mapper.toResponse(seoRepository.save(seo));
    }

    private SeoResponse seo(String key) {
        return seoRepository.findByPageKey(key).map(mapper::toResponse).orElse(new SeoResponse(key, null, null, null, true));
    }

    private static String requirePageKey(String pageKey) {
        String key = pageKey == null ? "" : pageKey.toUpperCase(Locale.ROOT);
        if (!PAGE_KEYS.contains(key)) {
            throw new ResourceNotFoundException("Page", pageKey);
        }
        return key;
    }
}
