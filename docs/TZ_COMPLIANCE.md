# Сверка проекта «НормаКонтроль» с техническим заданием

## Итог

Проект закрывает основной сценарий защиты: запуск через Docker Compose, вход в систему, загрузка документа, асинхронная проверка, отображение результата, PDF-отчёт, автоисправление DOCX, статистика, Swagger/OpenAPI, мониторинг Prometheus/Grafana.

## Что реализовано

| Требование ТЗ | Статус | Где смотреть |
|---|---:|---|
| Java 17, Spring Boot 3.x | Выполнено | `build.gradle`, `NormaControlApplication.java` |
| Clean/Hexagonal Architecture | Выполнено | `domain`, `application`, `infrastructure`, `presentation` |
| PostgreSQL 15+ | Выполнено | `docker-compose.yml`, `application.yml` |
| Liquibase-миграции | Выполнено | `src/main/resources/db/changelog` |
| Docker Compose | Выполнено | `docker-compose.yml`, `start.bat` |
| Загрузка DOCX/PDF/TXT/MD до 50 МБ | Выполнено | `DocumentController`, `DocumentUseCase`, `application.yml` |
| Хранение файлов в MinIO | Выполнено | `MinioStorageService`, `MinioConfig` |
| Асинхронная проверка через Kafka | Выполнено | `DocumentCheckProducer`, `DocumentCheckConsumer` |
| Проверки STRUCT/FMT/TBL/FIG/LANG/REF | Выполнено | `domain/checker/strategy` |
| Антиплагиат по базе документов | Выполнено | `infrastructure/plagiarism` |
| PDF-отчёт | Выполнено | `ReportGenerator`, `/api/v1/documents/{id}/report/download` |
| Автоисправление DOCX | Выполнено | `DocumentAutoFixService`, `FixController` |
| Скачивание исходного документа | Выполнено | `/api/v1/documents/{id}/download` |
| История документов и результатов | Выполнено частично | `documents.html`, `CheckResultController` |
| JWT + refresh token | Выполнено | `JwtTokenProvider`, `RefreshTokenService` |
| RBAC USER/REVIEWER/ADMIN | Выполнено | `SecurityConfig`, `@PreAuthorize` |
| Защита от brute-force | Выполнено | `BruteForceService` |
| Аудит действий | Выполнено | `infrastructure/audit` |
| OpenAPI/Swagger UI | Выполнено | `/api/docs`, `OpenApiConfig` |
| Prometheus/Grafana | Выполнено | `/actuator/prometheus`, `monitoring`, `docker-compose.yml` |
| Настройки правил | Выполнено | `RuleSettingsController`, `check_strategy_settings` |
| CI/CD | Базово выполнено | `.github/workflows/ci.yml` |
| Kubernetes/HPA | Базово выполнено | `k8s/*.yaml` |

## Что частично или не полностью реализовано

| Требование | Комментарий для защиты |
|---|---|
| OAuth2 GitHub/Google | В `.env` есть переменные, но полноценный OAuth2 login не подключён. На защите лучше говорить, что основная сдаваемая авторизация — JWT email/password, OAuth2 оставлен как расширение. |
| Webhook после проверки | Не реализован отдельный webhook callback. Есть Kafka-событие и REST/Web UI для получения результата. |
| HTML/JSON-отчёт как отдельные файлы | JSON доступен через API результата проверки; PDF генерируется как файл. HTML-отчёт отдельным файлом не выделен. |
| Полное GDPR-удаление пользователя | Есть управление профилем и документами, но полного endpoint'а анонимизации аккаунта нет. |
| HTTPS и pgcrypto | Для локальной учебной среды используется HTTP. В продакшене закрывается reverse proxy/TLS и шифрованием на уровне БД. |
| Автоматические бэкапы каждые 6 часов | Не заведён отдельный backup-job. Можно описать как DevOps-расширение. |
| Gatling-нагрузочные тесты | В ТЗ указаны, в проекте сейчас основной упор на unit/integration tests. |

## Рекомендация для защиты

Не обещать, что закрыты абсолютно все enterprise-требования. Делать акцент на том, что реализован полный рабочий учебный MVP с промышленными компонентами: Spring Boot, PostgreSQL, Liquibase, Kafka, Redis, MinIO, JWT, Swagger, Prometheus, Grafana, Docker и базовые Kubernetes/CI файлы.
