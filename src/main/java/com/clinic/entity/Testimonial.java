package com.clinic.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Genuine patient feedback. Can only be published after the patient's consent has been recorded (DB-enforced). */
@Entity
@Table(name = "testimonials")
@Getter
@Setter
@NoArgsConstructor
public class Testimonial extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "patient_name", nullable = false, length = 100)
    private String patientName;

    @Column(length = 150)
    private String context;

    @Column(nullable = false, length = 2000)
    private String quote;

    private Integer rating;

    @Column(name = "consent_confirmed", nullable = false)
    private boolean consentConfirmed;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
