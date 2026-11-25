package com.clinic.security;

import com.clinic.enums.Role;

/** Authenticated admin identity extracted from a validated JWT. */
public record AdminPrincipal(Long id, String email, Role role) {
}
