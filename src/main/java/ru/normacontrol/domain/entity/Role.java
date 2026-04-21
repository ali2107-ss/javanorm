package ru.normacontrol.domain.entity;

import lombok.*;
import ru.normacontrol.domain.enums.RoleName;

/**
 * Доменная сущность роли пользователя.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    private Long id;
    private RoleName name;
}
