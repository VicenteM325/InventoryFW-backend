package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.UserCreateDto;
import com.vm325.inventory_back.dtos.UserResponseDto;
import com.vm325.inventory_back.dtos.UserUpdateDto;
import com.vm325.inventory_back.entities.Role;
import com.vm325.inventory_back.entities.User;
import com.vm325.inventory_back.enums.RoleList;
import com.vm325.inventory_back.repositories.RoleRepository;
import com.vm325.inventory_back.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserResponseDto> findAll() {
        return userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponseDto create(UserCreateDto dto) {
        if (userRepository.existsByUserName(dto.getUserName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe un usuario con el nombre de usuario " + dto.getUserName());
        }

        String email = normalizeEmail(dto.getEmail());
        assertEmailAvailable(email, null);

        User user = new User();
        user.setName(dto.getName());
        user.setUserName(dto.getUserName());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRole(resolveRole(dto.getRoleName()));
        user.setActive(true);
        user.setEmail(email);
        // Con correo registrado la cuenta nace protegida con verificación en dos pasos.
        user.setTwoFactorEnabled(email != null);

        return toDto(userRepository.save(user));
    }

    @Override
    public UserResponseDto update(UUID id, UserUpdateDto dto) {
        User user = getUserOrThrow(id);
        String email = normalizeEmail(dto.getEmail());
        assertEmailAvailable(email, user);

        Role newRole = resolveRole(dto.getRoleName());
        if (user.getRole().getName() == RoleList.ROLE_ADMIN && newRole.getName() != RoleList.ROLE_ADMIN) {
            // Quitar el rol admin equivale a perder acceso de administración.
            assertNotSelf(user, "No puede quitarse a sí mismo el rol de administrador");
            assertNotLastAdmin(user, "Debe existir al menos un administrador activo");
        }

        user.setName(dto.getName());
        user.setRole(newRole);
        user.setEmail(email);
        if (email == null) {
            // Sin correo no hay a dónde enviar el código: 2FA no puede seguir activa.
            user.setTwoFactorEnabled(false);
        }
        return toDto(userRepository.save(user));
    }

    @Override
    public UserResponseDto setActive(UUID id, boolean active) {
        User user = getUserOrThrow(id);
        if (!active) {
            assertNotSelf(user, "No puede desactivar su propia cuenta");
            assertNotLastAdmin(user, "No se puede desactivar al último administrador activo");
        }
        user.setActive(active);
        return toDto(userRepository.save(user));
    }

    private void assertNotSelf(User target, String message) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && target.getUserName().equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private void assertNotLastAdmin(User target, String message) {
        boolean activeAdmin = target.isActive() && target.getRole().getName() == RoleList.ROLE_ADMIN;
        if (activeAdmin && userRepository.countByRole_NameAndActiveTrue(RoleList.ROLE_ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase();
    }

    private void assertEmailAvailable(String email, User current) {
        if (email == null) {
            return;
        }
        userRepository.findByEmailIgnoreCase(email).ifPresent(other -> {
            if (current == null || !other.getUserId().equals(current.getUserId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe un usuario con el correo " + email);
            }
        });
    }

    private Role resolveRole(String roleName) {
        RoleList roleList;
        try {
            roleList = RoleList.valueOf(roleName.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rol inválido: " + roleName);
        }
        return roleRepository.findByName(roleList)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Rol no configurado en el sistema: " + roleName));
    }

    private User getUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Usuario no encontrado con id " + id));
    }

    private UserResponseDto toDto(User user) {
        return UserResponseDto.from(user);
    }
}
