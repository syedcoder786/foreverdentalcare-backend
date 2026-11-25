package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * An editable block of copy on a public page, identified by (pageKey, sectionKey), e.g. (HOME, hero).
 * Known section keys get bespoke layouts on the website; any other key renders as a generic content section.
 */
@Entity
@Table(name = "website_sections")
@Getter
@Setter
@NoArgsConstructor
public class WebsiteSection extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_key", nullable = false, length = 30)
    private String pageKey;

    @Column(name = "section_key", nullable = false, length = 50)
    private String sectionKey;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(length = 10000)
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "primary_button_label", length = 50)
    private String primaryButtonLabel;

    @Column(name = "primary_button_url", length = 300)
    private String primaryButtonUrl;

    @Column(name = "secondary_button_label", length = 50)
    private String secondaryButtonLabel;

    @Column(name = "secondary_button_url", length = 300)
    private String secondaryButtonUrl;

    @Convert(converter = JsonValues.SectionItemListConverter.class)
    @Column(nullable = false, length = 20000)
    private List<JsonValues.SectionItem> items = new ArrayList<>();

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
