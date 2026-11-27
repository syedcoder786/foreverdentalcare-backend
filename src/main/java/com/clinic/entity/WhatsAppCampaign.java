package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One marketing broadcast to patients, with delivery counts. */
@Entity
@Table(name = "whatsapp_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppCampaign {

    public enum Status { SENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private WhatsAppTemplate template;

    @Column(name = "template_label", nullable = false, length = 100)
    private String templateLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.SENDING;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int sent;

    @Column(nullable = false)
    private int failed;

    @Column(name = "created_by", length = 150)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
