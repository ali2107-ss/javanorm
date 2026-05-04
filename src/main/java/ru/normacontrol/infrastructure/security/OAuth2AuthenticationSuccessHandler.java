package ru.normacontrol.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.infrastructure.persistence.entity.RoleJpaEntity;
import ru.normacontrol.infrastructure.persistence.entity.UserJpaEntity;
import ru.normacontrol.infrastructure.persistence.repository.RoleJpaRepository;
import ru.normacontrol.infrastructure.persistence.repository.UserJpaRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        String provider = token.getAuthorizedClientRegistrationId();
        OAuth2User principal = token.getPrincipal();
        String providerId = providerId(provider, principal);
        String email = email(provider, principal, providerId);
        String displayName = displayName(provider, principal, email);

        UserJpaEntity user = userRepository.findByOauthProviderAndOauthProviderId(provider, providerId)
                .or(() -> userRepository.findByEmail(email))
                .map(existing -> updateOAuthUser(existing, provider, providerId, displayName))
                .orElseGet(() -> createOAuthUser(provider, providerId, email, displayName));

        user.setLastLoginAt(LocalDateTime.now());
        user = userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(toDomain(user));
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());
        String redirect = UriComponentsBuilder.fromPath("/oauth2-success.html")
                .queryParam("access_token", accessToken)
                .queryParam("refresh_token", refreshToken)
                .build()
                .toUriString();
        response.sendRedirect(redirect);
    }

    private UserJpaEntity createOAuthUser(String provider, String providerId, String email, String displayName) {
        RoleJpaEntity userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(RoleJpaEntity.builder().name(RoleName.ROLE_USER).build()));
        return UserJpaEntity.builder()
                .id(UUID.randomUUID())
                .email(email)
                .displayName(displayName)
                .oauthProvider(provider)
                .oauthProviderId(providerId)
                .enabled(true)
                .accountLocked(false)
                .failedLoginAttempts(0)
                .createdAt(LocalDateTime.now())
                .lastLoginAt(LocalDateTime.now())
                .roles(Set.of(userRole))
                .build();
    }

    private UserJpaEntity updateOAuthUser(UserJpaEntity user, String provider, String providerId, String displayName) {
        user.setOauthProvider(provider);
        user.setOauthProviderId(providerId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        user.setEnabled(true);
        return user;
    }

    private String providerId(String provider, OAuth2User principal) {
        Object value = "google".equals(provider)
                ? principal.getAttribute("sub")
                : principal.getAttribute("id");
        if (value == null) {
            value = principal.getName();
        }
        return String.valueOf(value);
    }

    private String email(String provider, OAuth2User principal, String providerId) {
        String email = principal.getAttribute("email");
        if (email != null && !email.isBlank()) {
            return email;
        }
        return provider + "-" + providerId + "@oauth.local";
    }

    private String displayName(String provider, OAuth2User principal, String fallback) {
        String name = principal.getAttribute("name");
        if (name != null && !name.isBlank()) {
            return name;
        }
        String login = principal.getAttribute("login");
        if (login != null && !login.isBlank()) {
            return login;
        }
        return fallback;
    }

    private User toDomain(UserJpaEntity entity) {
        return User.builder()
                .id(entity.getId())
                .email(entity.getEmail())
                .username(entity.getDisplayName())
                .fullName(entity.getDisplayName())
                .passwordHash(entity.getPasswordHash())
                .oauthProvider(entity.getOauthProvider())
                .oauthId(entity.getOauthProviderId())
                .enabled(entity.isEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getLastLoginAt())
                .roles(entity.getRoles().stream()
                        .map(role -> Role.builder().id(role.getId()).name(role.getName()).build())
                        .collect(Collectors.toSet()))
                .build();
    }
}
