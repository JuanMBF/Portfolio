package com.juanbarros.helpdesk.auth;

import com.juanbarros.helpdesk.common.BusinessRuleException;
import com.juanbarros.helpdesk.security.JwtService;
import com.juanbarros.helpdesk.user.Role;
import com.juanbarros.helpdesk.user.User;
import com.juanbarros.helpdesk.user.UserRepository;
import com.juanbarros.helpdesk.user.UserResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Todo registro público crea un usuario con rol USER; los roles de staff los asigna un ADMIN. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("Ya existe una cuenta con ese email");
        }
        User user = userRepository.save(new User(
                request.fullName().trim(),
                request.email().trim(),
                passwordEncoder.encode(request.password()),
                Role.USER));
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        return toResponse(user);
    }

    private AuthResponse toResponse(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.getExpirationSeconds(), UserResponse.from(user));
    }
}
