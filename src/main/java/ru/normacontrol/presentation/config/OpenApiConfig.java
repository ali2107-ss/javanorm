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
 * OpenAPI and Swagger UI configuration.
 */
@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT access token in Bearer format"
)
public class OpenApiConfig {

    /**
     * Build OpenAPI metadata for Swagger UI.
     *
     * @return OpenAPI model
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("НормаКонтроль API")
                        .version("1.0.0")
                        .description("REST API автоматической проверки документов по ГОСТ 19.201-78")
                        .contact(new Contact().name("NormaControl Team").email("support@normacontrol.local"))
                        .license(new License().name("MIT License").url("https://opensource.org/licenses/MIT")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Локальный сервер разработки")));
    }
}
