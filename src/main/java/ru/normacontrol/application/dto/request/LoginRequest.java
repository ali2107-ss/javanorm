package ru.normacontrol.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Email или имя пользователя обязательно")
    private String login;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
