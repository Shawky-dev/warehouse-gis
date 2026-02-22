package com.warehouse.warehouse_platform.security;

import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleAuthorityResolver roleAuthorityResolver;

    public CustomUserDetailsService(
            UserRepository userRepository,
            RoleAuthorityResolver roleAuthorityResolver) {
        this.userRepository = userRepository;
        this.roleAuthorityResolver = roleAuthorityResolver;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        boolean isActive = !Boolean.FALSE.equals(user.getActive());

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getEmail())
            .password(user.getPassword())
            .authorities(roleAuthorityResolver.resolveAuthorities(user.getRole()))
            .disabled(!isActive)
            .build();

    }
}
