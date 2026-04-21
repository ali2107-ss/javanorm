package ru.normacontrol.domain.entity;

import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Доменная сущность пользователя системы.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String email;
    private String username;
    private String passwordHash;
    private String fullName;
    private String oauthProvider;
    private String oauthId;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(String roleName) {
        return roles.stream()
                .anyMatch(r -> r.getName().name().equals(roleName));
    }
}
