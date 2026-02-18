package com.warehouse.warehouse_platform.config;

import com.warehouse.warehouse_platform.user.User;
import com.warehouse.warehouse_platform.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {

        if (userRepository.count() == 0) {
            User admin = User.builder()
                    .email("admin@system.local")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .build();

            userRepository.save(admin);
        }
    }
}
