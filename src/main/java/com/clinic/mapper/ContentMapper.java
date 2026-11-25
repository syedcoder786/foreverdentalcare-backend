package com.clinic.mapper;

import com.clinic.dto.ContentDtos.*;
import com.clinic.entity.*;
import com.clinic.util.ValidationPatterns;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class ContentMapper {

    /**
     * Sections the website renders with bespoke layouts. They can be edited and hidden, but not deleted.
     * Any other section key is a custom section rendered with a generic layout.
     */
    public static final Set<String> CORE_SECTIONS = Set.of(
            "HOME:hero", "HOME:intro", "HOME:doctors", "HOME:specialties", "HOME:services", "HOME:why_choose_us",
            "HOME:testimonials", "HOME:gallery", "HOME:timings", "HOME:cta",
            "ABOUT:intro", "ABOUT:mission", "ABOUT:vision", "ABOUT:facilities", "ABOUT:equipment",
            "DOCTORS:intro", "SPECIALTIES:intro", "SERVICES:intro", "CONTACT:intro",
            "BOOK:intro", "BOOK:consent", "BOOK:emergency");

    public static boolean isCore(String pageKey, String sectionKey) {
        return CORE_SECTIONS.contains(pageKey + ":" + sectionKey);
    }

    public NavigationItemDto toDto(NavigationItem n) {
        return new NavigationItemDto(n.getId(), n.getLabel(), n.getUrl(), n.getDisplayOrder(), n.isActive(), n.isOpenInNewTab());
    }

    public SectionResponse toResponse(WebsiteSection s) {
        return new SectionResponse(s.getId(), s.getPageKey(), s.getSectionKey(), isCore(s.getPageKey(), s.getSectionKey()),
                s.getTitle(), s.getSubtitle(), s.getBody(), s.getImageUrl(), s.getPrimaryButtonLabel(),
                s.getPrimaryButtonUrl(), s.getSecondaryButtonLabel(), s.getSecondaryButtonUrl(),
                s.getItems().stream().map(i -> new SectionItemDto(i.icon(), i.title(), i.text(), i.imageUrl())).toList(),
                s.getDisplayOrder(), s.isActive(), s.getUpdatedAt());
    }

    public void apply(SectionRequest r, WebsiteSection s) {
        s.setTitle(ValidationPatterns.trimToNull(r.title()));
        s.setSubtitle(ValidationPatterns.trimToNull(r.subtitle()));
        s.setBody(ValidationPatterns.trimToNull(r.body()));
        s.setImageUrl(ValidationPatterns.trimToNull(r.imageUrl()));
        s.setPrimaryButtonLabel(ValidationPatterns.trimToNull(r.primaryButtonLabel()));
        s.setPrimaryButtonUrl(ValidationPatterns.trimToNull(r.primaryButtonUrl()));
        s.setSecondaryButtonLabel(ValidationPatterns.trimToNull(r.secondaryButtonLabel()));
        s.setSecondaryButtonUrl(ValidationPatterns.trimToNull(r.secondaryButtonUrl()));
        List<JsonValues.SectionItem> items = r.items() == null ? List.of() : r.items().stream()
                .map(i -> new JsonValues.SectionItem(ValidationPatterns.trimToNull(i.icon()), ValidationPatterns.trimToNull(i.title()),
                        ValidationPatterns.trimToNull(i.text()), ValidationPatterns.trimToNull(i.imageUrl())))
                .filter(i -> i.title() != null || i.text() != null || i.imageUrl() != null)
                .toList();
        s.setItems(new java.util.ArrayList<>(items));
        if (r.displayOrder() != null) {
            s.setDisplayOrder(r.displayOrder());
        }
        if (r.active() != null) {
            s.setActive(r.active());
        }
    }

    public SeoResponse toResponse(PageSeo p) {
        return new SeoResponse(p.getPageKey(), p.getMetaTitle(), p.getMetaDescription(), p.getOgImageUrl(), p.isIndexable());
    }

    public TestimonialResponse toResponse(Testimonial t) {
        return new TestimonialResponse(t.getId(), t.getPatientName(), t.getContext(), t.getQuote(), t.getRating(),
                t.isConsentConfirmed(), t.isPublished(), t.getDisplayOrder(), t.getCreatedAt());
    }

    public GalleryResponse toResponse(GalleryImage g) {
        return new GalleryResponse(g.getId(), g.getImageUrl(), g.getCaption(), g.getCategory(), g.getDisplayOrder(),
                g.isActive(), g.getCreatedAt());
    }
}
