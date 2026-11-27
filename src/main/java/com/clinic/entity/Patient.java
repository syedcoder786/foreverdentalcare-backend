package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Normalised phone number (digits with optional leading '+'). Used to identify returning patients. */
    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    /** Receives offers and updates on WhatsApp. Every patient is included by default. */
    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn = true;

    @Column(name = "whatsapp_opt_in_at")
    private Instant whatsappOptInAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Records the opt-in time when switched on; clears it when switched off. */
    public void changeWhatsappOptIn(boolean optIn) {
        if (optIn && !whatsappOptIn) {
            whatsappOptInAt = Instant.now();
        } else if (!optIn) {
            whatsappOptInAt = null;
        }
        whatsappOptIn = optIn;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (whatsappOptIn && whatsappOptInAt == null) {
            whatsappOptInAt = createdAt;
        }
    }
}
