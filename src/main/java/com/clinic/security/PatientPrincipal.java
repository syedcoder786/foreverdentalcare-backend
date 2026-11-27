package com.clinic.security;

/** Authenticated patient identity extracted from a validated JWT. */
public record PatientPrincipal(Long id, String phone) {
}
