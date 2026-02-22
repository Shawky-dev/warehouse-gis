package com.warehouse.warehouse_platform.security;

import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleAuthorityResolver roleAuthorityResolver;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void loadUserByUsername_shouldLoadAuthoritiesFromRole() {
        User user = new User();
        user.setEmail("admin@system.local");
        user.setPassword("encoded");
        user.setRole("ADMIN");
        user.setActive(true);

        when(userRepository.findByEmail("admin@system.local")).thenReturn(Optional.of(user));
        when(roleAuthorityResolver.resolveAuthorities("ADMIN")).thenReturn(List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("landlord.users.view")));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin@system.local");

        assertTrue(userDetails.isEnabled());
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN")));
        assertTrue(userDetails.getAuthorities().contains(new SimpleGrantedAuthority("landlord.users.view")));
    }

    @Test
    void loadUserByUsername_shouldDisableInactiveUsers() {
        User user = new User();
        user.setEmail("manager@system.local");
        user.setPassword("encoded");
        user.setRole("MANAGER");
        user.setActive(false);

        when(userRepository.findByEmail("manager@system.local")).thenReturn(Optional.of(user));
        when(roleAuthorityResolver.resolveAuthorities("MANAGER")).thenReturn(List.of(
                new SimpleGrantedAuthority("ROLE_MANAGER")));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("manager@system.local");

        assertFalse(userDetails.isEnabled());
    }

    @Test
    void loadUserByUsername_shouldThrowWhenUserMissing() {
        when(userRepository.findByEmail("missing@system.local")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("missing@system.local"));
    }
}
