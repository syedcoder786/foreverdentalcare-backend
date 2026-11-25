package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "navigation_items")
@Getter
@Setter
@NoArgsConstructor
public class NavigationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String label;

    /** Internal path ("/about") or absolute https URL. */
    @Column(nullable = false, length = 300)
    private String url;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "open_in_new_tab", nullable = false)
    private boolean openInNewTab;
}
