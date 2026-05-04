# Сверка проекта «НормаКонтроль» с техническим заданием

## Итог

Проект закрывает основной сценарий ТЗ: запуск через Docker Compose, регистрация и вход, JWT/OAuth2, загрузка DOCX/PDF/TXT/MD, асинхронная проверка через Kafka, сохранение файлов в MinIO, отчёты HTML/PDF/JSON, автоисправление DOCX, история проверок, статистика, webhook, Swagger/OpenAPI, Prometheus/Grafana, CI/CD и Kubernetes-манифесты.

## Функциональные требования

| Требование ТЗ | Статус | Где смотреть |
|---|---:|---|
| Регистрация пользователя | Выполнено | `POST /api/v1/auth/register`, `AuthController` |
| Вход по email/password | Выполнено | `POST /api/v1/auth/login` |
| OAuth2 GitHub/Google | Выполнено | `/oauth2/authorization/github`, `/oauth2/authorization/google`, `OAuth2AuthenticationSuccessHandler` |
| Logout с инвалидацией refresh token | Выполнено | `POST /api/v1/auth/logout`, `RefreshTokenService` |
| Редактирование профиля | Выполнено | `PATCH /api/v1/users/me` |
| История документов и проверок | Выполнено | `GET /api/v1/documents`, `GET /api/v1/check-results/document/{id}/history` |
| GDPR-экспорт персональных данных | Выполнено | `GET /api/v1/users/me/export` |
| GDPR-анонимизация аккаунта | Выполнено | `DELETE /api/v1/users/me` |
| Загрузка DOCX/PDF/TXT/MD до 50 МБ | Выполнено | `DocumentController`, `application.yml` |
| Фильтрация документов по статусу, типу, дате | Выполнено | `GET /api/v1/documents?status=CHECKED&type=docx&createdFrom=2026-05-01` |
| Карточка документа | Выполнено | `GET /api/v1/documents/{id}` |
| Скачивание оригинального документа | Выполнено | `GET /api/v1/documents/{id}/download` |
| Скачивание отчёта | Выполнено | `GET /api/v1/documents/{id}/report/download` |
| Мягкое удаление документа | Выполнено | `DELETE /api/v1/documents/{id}`, поле `deleted` |
| Запуск проверки по правилам ГОСТ | Выполнено | `POST /api/v1/documents/{id}/check`, `GostRuleEngine` |
| Проверка структуры, форматирования, таблиц, рисунков, языка, ссылок | Выполнено | `src/main/java/ru/normacontrol/domain/checker/strategy` |
| Асинхронная обработка через Kafka | Выполнено | `DocumentCheckProducer`, `DocumentCheckConsumer` |
| Статусы документа | Выполнено | `DocumentStatus`, `documents.status` |
| Управление правилами | Выполнено | `GET/PATCH /api/v1/rule-settings` |
| Импорт/экспорт правил JSON | Выполнено | `GET /api/v1/rule-settings/export`, `POST /api/v1/rule-settings/import` |
| Отчёт HTML/PDF/JSON | Выполнено | `/report/html`, `/report/download`, `/report/json` |
| Нарушения с критичностью, местом и рекомендацией | Выполнено | `Violation`, `ViolationResponse`, `ReportGenerator` |
| Сводная статистика | Выполнено | `StatsController`, `AdminController` |
| CSV-экспорт статистики | Выполнено | `GET /api/v1/stats/export.csv` |
| REST API для внешних систем | Выполнено | `/api/v1/**`, Swagger `/api/docs` |
| Webhook после завершения проверки | Выполнено | `WebhookController`, `WebhookNotificationService` |
| GitHub Actions / Docker image | Выполнено базово | `.github/workflows/ci.yml`, `Dockerfile` |

## Надёжность и безопасность

| Требование ТЗ | Статус | Где смотреть |
|---|---:|---|
| Jakarta Bean Validation | Выполнено | DTO request-классы, `@Valid` |
| FAILED при ошибке проверки | Выполнено | `CheckDocumentUseCase`, Kafka consumer |
| Kafka at-least-once | Выполнено базово | Kafka consumer group, идемпотентное сохранение результата по документу |
| Redis fallback / cache resilience | Выполнено базово | основная логика не зависит от Redis-кэша, Redis используется для security/rate limit |
| Автоматический backup БД каждые 6 часов, хранение 30 дней | Выполнено | `postgres-backup` в `docker-compose.yml` |
| Graceful shutdown | Выполнено | `server.shutdown=graceful`, Spring Boot |
| JWT access/refresh | Выполнено | `JwtTokenProvider`, `RefreshTokenService` |
| RBAC USER/REVIEWER/ADMIN | Выполнено | `SecurityConfig`, `@PreAuthorize` |
| bcrypt >= 12 | Выполнено | `PasswordEncoder` |
| HTTPS redirect | Выполнено конфигурируемо | `ForceHttpsFilter`, `APP_FORCE_HTTPS=true`, `k8s/ingress-tls.yaml` |
| pgcrypto | Выполнено базово | `V14__enable_pgcrypto.sql` |
| Rate limit 100 запросов/мин | Выполнено | `RateLimitFilter` |
| Аудит действий | Выполнено | `AuditService`, `audit_logs` |
| XSS/SQL injection защита | Выполнено базово | JPA repositories, DTO validation, HTML escaping in report |

## Архитектура и DevOps

| Требование ТЗ | Статус | Где смотреть |
|---|---:|---|
| Java 17, Spring Boot 3.x | Выполнено | `build.gradle` |
| Clean/Hexagonal Architecture | Выполнено | `domain`, `application`, `infrastructure`, `presentation` |
| PostgreSQL 15+, Liquibase | Выполнено | `docker-compose.yml`, `db/changelog` |
| Docker Compose одной командой | Выполнено | `start.bat`, `docker-compose.yml` |
| Kubernetes 1.28+ и HPA | Выполнено базово | `k8s/deployment.yaml`, `k8s/hpa.yaml`, `k8s/ingress-tls.yaml` |
| 12-Factor env config | Выполнено | `application.yml`, `docker-compose.yml` |
| Prometheus metrics | Выполнено | `/actuator/prometheus` |
| Grafana dashboards | Выполнено | `monitoring/grafana`, `http://localhost:3000` |
| JSON logging traceId | Выполнено | `logging.pattern.console`, `TraceIdFilter` |
| CI/CD GitHub Actions | Выполнено базово | `.github/workflows/ci.yml` |
| Gatling load test | Выполнено базово | `load-tests/gatling/NormaControlSimulation.scala` |
| Jacoco coverage | Выполнено | `build.gradle`, `jacocoTestReport` |

## Что честно сказать на защите

Самый сильный ответ: «Все основные функции ТЗ реализованы и запускаются локально через Docker Compose. Enterprise-пункты вроде TLS, Kubernetes, CI/CD, Gatling и бэкапов представлены рабочими конфигурациями/манифестами; для реального продакшена нужно только подставить секреты, домен, OAuth client id/secret и подключить кластер».

## Быстрая проверка перед сдачей

```bash
docker compose up -d
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/docs
```

Проверенные вручную URL: `http://localhost:8080/actuator/health`, `http://localhost:8080/api/docs`, отчёты `/report/json`, `/report/html`, `/stats/export.csv`, `/rule-settings`.
