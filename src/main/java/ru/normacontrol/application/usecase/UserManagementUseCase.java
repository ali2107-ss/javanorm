package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.request.ChangePasswordRequest;
import ru.normacontrol.application.dto.request.UpdateProfileRequest;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.mapper.UserMapper;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Use Case: Управление пользователями (профиль, блокировка, назначение ролей).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementUseCase {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Получить профиль пользователя.
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (request.email() != null && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(request.email().trim().toLowerCase());
        }

        if (request.fullName() != null && !request.fullName().isBlank()) {
            String fullName = request.fullName().trim();
            user.setFullName(fullName);
            user.setUsername(fullName);
        }

        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    /**
     * Заблокировать / разблокировать пользователя (ADMIN).
     */
    @Transactional
    public UserResponse toggleEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        user.setEnabled(enabled);
        userRepository.save(user);
        log.info("Пользователь {} {}заблокирован", userId, enabled ? "раз" : "");
        return userMapper.toResponse(user);
    }
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            throw new IllegalArgumentException("Для внешнего аккаунта пароль меняется у провайдера входа");
        }
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Текущий пароль указан неверно");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Пользователь {} изменил пароль", userId);
    }

    @Transactional
    public void anonymizeAccount(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        String suffix = userId.toString().replace("-", "");
        user.setEmail("deleted-" + suffix + "@anon.invalid");
        user.setUsername("deleted-user-" + suffix.substring(0, 8));
        user.setFullName("Deleted user");
        user.setPasswordHash(null);
        user.setEnabled(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
        log.info("Пользователь {} анонимизирован по GDPR-запросу", userId);
    }
}
