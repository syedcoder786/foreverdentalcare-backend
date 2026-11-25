package com.clinic.entity;

import com.clinic.enums.Gender;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A practitioner with an independent schedule, specialties and the services they are allowed to receive bookings for. */
@Entity
@Table(name = "doctors")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "professional_title", nullable = false, length = 150)
    private String professionalTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender = Gender.UNSPECIFIED;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "short_bio", length = 500)
    private String shortBio;

    @Column(length = 10000)
    private String biography;

    @Convert(converter = JsonValues.QualificationListConverter.class)
    @Column(nullable = false, length = 5000)
    private List<JsonValues.Qualification> qualifications = new ArrayList<>();

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "registration_authority", length = 200)
    private String registrationAuthority;

    /** Qualifications and registration details are published only once an admin confirms they were verified. */
    @Column(name = "credentials_verified", nullable = false)
    private boolean credentialsVerified;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "consultation_fee", precision = 10, scale = 2)
    private BigDecimal consultationFee;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private boolean active = true;

    /** Shown in the home page "Meet our doctors" section. */
    @Column(nullable = false)
    private boolean featured = true;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToMany
    @JoinTable(name = "doctor_specialties",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "specialty_id"))
    @OrderBy("displayOrder ASC, name ASC")
    private Set<Specialty> specialties = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "doctor_services",
            joinColumns = @JoinColumn(name = "doctor_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    @OrderBy("displayOrder ASC, name ASC")
    private Set<DentalService> services = new LinkedHashSet<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DoctorAvailability> availability = new ArrayList<>();

    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("specialDate ASC")
    private List<DoctorSpecialHours> specialHours = new ArrayList<>();

    public boolean offers(Long serviceId) {
        return services.stream().anyMatch(s -> s.getId().equals(serviceId));
    }
}
