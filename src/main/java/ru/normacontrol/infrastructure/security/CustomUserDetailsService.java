package ru.normacontrol.infrastructure.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.util.stream.Collectors;

/**
 * Кастомная реализация UserDetailsService для Spring Security.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userJpaRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        UserJpaEntity user = userJpaRepository.findByEmail(usernameOrEmail)
                .or(() -> userJpaRepository.findByUsername(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь не найден: " + usernameOrEmail));

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
