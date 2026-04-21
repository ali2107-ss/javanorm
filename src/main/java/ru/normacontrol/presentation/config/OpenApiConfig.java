package ru.normacontrol.presentation.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация OpenAPI 3.0 / Swagger UI.
 * Swagger UI доступен по адресу: /api/docs
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT access token. Формат: Bearer <token>"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ПРОГРАММАНормаКонтроль API")
                        .version("1.0.0")
                        .description("""
                                Система автоматической проверки документов на соответствие ГОСТ 19.201-78.
                                
                                **Возможности:**
                                - Загрузка документов (DOCX, PDF)
                                - Автоматическая проверка на соответствие ГОСТ 19.201-78
                                - Детализированные отчёты о нарушениях
                                - RBAC с ролями: USER, REVIEWER, ADMIN
                                - OAuth2 авторизация (GitHub, Google)
                                """)
                        .contact(new Contact()
                                .name("NormaControl Team")
                                .email("support@normacontrol.ru"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8081/api")
                                .description("Локальный сервер разработки")
                ));
    }
}
