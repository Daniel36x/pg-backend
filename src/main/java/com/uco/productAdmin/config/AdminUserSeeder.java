package com.uco.productAdmin.config;

import com.uco.productAdmin.models.Role;
import com.uco.productAdmin.models.User;
import com.uco.productAdmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Crea un usuario ADMIN por defecto al arrancar, ya que el registro público
// (/api/v1/auth/register) solo puede crear usuarios con rol USER.
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-admin.username}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin.password}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(defaultAdminUsername)) {
            return;
        }

        User admin = new User();
        admin.setUsername(defaultAdminUsername);
        admin.setPassword(passwordEncoder.encode(defaultAdminPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        log.warn("Usuario administrador por defecto creado: '{}'. Cambia su contraseña en producción.", defaultAdminUsername);
    }
}
