package com.clinic.security;

import com.clinic.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads admins by email (case-insensitive) for Spring Security's username/password authentication. */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        return adminRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .map(AdminUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown admin"));
    }
}
