package com.clinic.dto.response;

import com.clinic.enums.Role;

/** Admin profile. Deliberately excludes the password hash. */
public record AdminResponse(Long id, String name, String email, Role role) {
}
