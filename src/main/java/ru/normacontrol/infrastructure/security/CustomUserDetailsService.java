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
 * Spring Security adapter for loading users from persistence.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserJpaRepository userJpaRepository;

    /**
     * Load user by email or display name.
     *
     * @param usernameOrEmail login value
     * @return Spring Security user
     * @throws UsernameNotFoundException when user is absent
     */
    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        UserJpaEntity user = userJpaRepository.findByEmail(usernameOrEmail)
                .or(() -> userJpaRepository.findByDisplayName(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + usernameOrEmail));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                        .collect(Collectors.toList()))
                .disabled(!user.isEnabled())
                .accountLocked(user.isAccountLocked())
                .build();
    }
}
