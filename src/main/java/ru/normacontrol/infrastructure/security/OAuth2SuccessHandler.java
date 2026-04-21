package ru.normacontrol.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import ru.normacontrol.domain.entity.Role;
import ru.normacontrol.domain.entity.User;
import ru.normacontrol.domain.enums.RoleName;
import ru.normacontrol.domain.repository.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Обработчик успешной OAuth2-аутентификации.
 * Создаёт пользователя в БД (если его нет) и генерирует JWT-токены.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String provider = determineProvider(request);

        if (email == null) {
            log.error("OAuth2: email не получен от провайдера {}", provider);
            response.sendRedirect("/login?error=email_required");
            return;
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .email(email)
                            .username(email.split("@")[0] + "_" + UUID.randomUUID().toString().substring(0, 4))
                            .fullName(name)
                            .oauthProvider(provider)
                            .oauthId(oAuth2User.getAttribute("sub") != null
                                    ? oAuth2User.getAttribute("sub").toString()
                                    : oAuth2User.getAttribute("id").toString())
                            .enabled(true)
                            .roles(Set.of(Role.builder().id(1L).name(RoleName.ROLE_USER).build()))
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return userRepository.save(newUser);
                });

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId());

        log.info("OAuth2 вход: {} через {}", email, provider);

        // Редирект с токенами (в реальном приложении — на фронтенд)
        String redirectUrl = String.format(
                "/api/auth/oauth2/success?access_token=%s&refresh_token=%s",
                accessToken, refreshToken);
        response.sendRedirect(redirectUrl);
    }

    private String determineProvider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("github")) return "github";
        if (uri.contains("google")) return "google";
        return "unknown";
    }
}
