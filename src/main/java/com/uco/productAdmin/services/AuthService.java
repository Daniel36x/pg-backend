package com.uco.productAdmin.services;

import com.uco.productAdmin.dto.AuthResponseDTO;
import com.uco.productAdmin.dto.LoginRequestDTO;
import com.uco.productAdmin.dto.RegisterRequestDTO;
import com.uco.productAdmin.exceptions.InvalidCredentialsException;
import com.uco.productAdmin.exceptions.UserAlreadyExistsException;
import com.uco.productAdmin.models.Role;
import com.uco.productAdmin.models.User;
import com.uco.productAdmin.repository.UserRepository;
import com.uco.productAdmin.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponseDTO register(RegisterRequestDTO dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new UserAlreadyExistsException("El nombre de usuario ya está en uso: " + dto.getUsername());
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(Role.USER);

        User savedUser = userRepository.save(user);
        String token = jwtService.generateToken(savedUser);

        return new AuthResponseDTO(token, "Bearer", savedUser.getUsername(), savedUser.getRole().name());
    }

    public AuthResponseDTO login(LoginRequestDTO dto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword())
            );
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Usuario o contraseña incorrectos");
        }

        User user = userRepository.findByUsername(dto.getUsername())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario o contraseña incorrectos"));

        String token = jwtService.generateToken(user);

        return new AuthResponseDTO(token, "Bearer", user.getUsername(), user.getRole().name());
    }
}
