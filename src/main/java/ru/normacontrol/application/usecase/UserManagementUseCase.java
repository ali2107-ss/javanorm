package ru.normacontrol.application.usecase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.normacontrol.application.dto.response.UserResponse;
import ru.normacontrol.application.mapper.UserMapper;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.repository.UserRepository;

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

    /**
     * Получить профиль пользователя.
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден: " + userId));
        return userMapper.toResponse(user);
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
}
