# Документ для защиты проекта «НормаКонтроль»

## 1. Короткая речь

«НормаКонтроль» — Java/Spring Boot система для автоматической проверки документов по требованиям ГОСТ 19 и ГОСТ 7.32. Пользователь загружает DOCX, PDF, TXT или MD, файл сохраняется в MinIO, задача проверки уходит в Kafka, движок правил анализирует структуру, оформление, таблицы, рисунки, язык и ссылки. После проверки система показывает нарушения, итоговый балл 0–100, рекомендации и формирует отчёты HTML, PDF и JSON. Дополнительно есть автоисправление DOCX, антиплагиат по базе документов, статистика, аудит, JWT/OAuth2, Swagger, Prometheus, Grafana, Docker, Kubernetes и CI/CD.

## 2. Как показать проект

1. Запуск:

```bat
start.bat
```

или:

```bash
docker compose up -d
```

2. Открыть приложение:

```text
http://localhost:8080
```

3. Войти:

```text
admin@demo.ru / Admin1234!
```

У этого пользователя уже есть документы для демонстрации.

4. Показать сценарий:

- страница «Документы»;
- фильтр документов по статусу/типу/дате;
- скачивание исходного файла;
- запуск проверки;
- просмотр результата;
- скачивание PDF/HTML/JSON отчёта;
- автоисправление DOCX;
- статистика и CSV-экспорт;
- Swagger API.

5. Swagger:

```text
http://localhost:8080/api/docs
```

6. Grafana:

```text
http://localhost:3000
```

## 3. Где что лежит

| Часть | Путь |
|---|---|
| Точка входа | `src/main/java/ru/normacontrol/NormaControlApplication.java` |
| REST-контроллеры | `src/main/java/ru/normacontrol/presentation/controller` |
| Use Case'ы | `src/main/java/ru/normacontrol/application/usecase` |
| DTO | `src/main/java/ru/normacontrol/application/dto` |
| Домен | `src/main/java/ru/normacontrol/domain/entity` |
| Правила проверки ГОСТ | `src/main/java/ru/normacontrol/domain/checker/strategy` |
| Движок правил | `src/main/java/ru/normacontrol/domain/service/GostRuleEngine.java` |
| JPA-сущности | `src/main/java/ru/normacontrol/infrastructure/persistence/entity` |
| Репозитории | `src/main/java/ru/normacontrol/infrastructure/persistence/repository` |
| Kafka | `src/main/java/ru/normacontrol/infrastructure/kafka` |
| MinIO | `src/main/java/ru/normacontrol/infrastructure/minio` |
| Redis/security/rate limit | `src/main/java/ru/normacontrol/infrastructure/security`, `infrastructure/web` |
| OAuth2 | `OAuth2AuthenticationSuccessHandler`, `SecurityConfig` |
| PDF-отчёт | `src/main/java/ru/normacontrol/infrastructure/report/ReportGenerator.java` |
| Автоисправление | `src/main/java/ru/normacontrol/infrastructure/export/DocumentAutoFixService.java` |
| Антиплагиат | `src/main/java/ru/normacontrol/infrastructure/plagiarism` |
| Webhook | `src/main/java/ru/normacontrol/infrastructure/webhook` |
| WebSocket progress | `src/main/java/ru/normacontrol/infrastructure/websocket` |
| UI | `src/main/resources/static` |
| Конфиг | `src/main/resources/application.yml` |
| Миграции БД | `src/main/resources/db/changelog` |
| Docker | `Dockerfile`, `docker-compose.yml` |
| Backup БД | `postgres-backup` в `docker-compose.yml` |
| Kubernetes | `k8s` |
| CI/CD | `.github/workflows/ci.yml` |
| Gatling | `load-tests/gatling` |

## 4. База данных

Используется PostgreSQL. Схема создаётся Liquibase-миграциями.

Основные таблицы:

| Таблица | Назначение |
|---|---|
| `users` | пользователи |
| `roles`, `user_roles` | RBAC-роли |
| `documents` | метаданные загруженных документов |
| `check_results` | результаты проверок |
| `violations` | найденные нарушения |
| `refresh_tokens` | refresh-токены |
| `audit_logs` | журнал аудита |
| `check_strategy_settings` | включение/выключение правил |
| `document_text_hashes` | данные антиплагиата |
| `document_webhooks` | webhook URL для документов |

Показать таблицы:

```bash
docker compose exec postgres psql -U norma -d normacontrol -c "\dt"
```

Показать документы по пользователям:

```bash
docker compose exec postgres psql -U norma -d normacontrol -c "select u.email, count(d.id) from users u left join documents d on d.owner_id = u.id and d.deleted = false group by u.email;"
```

## 5. Библиотеки

| Библиотека | Зачем |
|---|---|
| Spring Boot Web | REST API и статический UI |
| Spring Security | JWT, RBAC, OAuth2 |
| Spring Data JPA + Hibernate | работа с PostgreSQL |
| Liquibase | миграции БД |
| Spring Kafka | очередь проверок документов |
| Spring Data Redis | refresh-токены, brute-force/rate-limit инфраструктура |
| MinIO SDK | объектное хранилище файлов |
| Apache POI | чтение и изменение DOCX |
| Apache PDFBox | чтение PDF |
| iText 7 | генерация PDF-отчётов |
| MapStruct | маппинг DTO |
| JJWT | JWT access/refresh |
| SpringDoc OpenAPI | Swagger UI |
| Micrometer Prometheus | метрики |
| Bucket4j | rate limiting |
| JUnit 5 / Mockito / Testcontainers / Jacoco | тесты и покрытие |

## 6. Как показать фреймворки

Spring Boot: `NormaControlApplication.java`, затем любой `@RestController`.

Spring Security: `SecurityConfig.java`, `JwtAuthFilter.java`, `OAuth2AuthenticationSuccessHandler.java`, `@PreAuthorize`.

Spring Data JPA: `DocumentJpaEntity.java`, `DocumentJpaRepository.java`.

Liquibase: `db.changelog-master.xml` и файлы `V*.sql`.

Kafka: `DocumentCheckProducer.java` и `DocumentCheckConsumer.java`.

MinIO: `MinioStorageService.java`.

Swagger: открыть `http://localhost:8080/api/docs`.

Prometheus/Grafana: открыть `/actuator/prometheus` и `http://localhost:3000`.

Kubernetes: показать `k8s/deployment.yaml`, `k8s/hpa.yaml`, `k8s/ingress-tls.yaml`.

CI/CD: показать `.github/workflows/ci.yml`.

## 7. Основные endpoint'ы

| Endpoint | Назначение |
|---|---|
| `POST /api/v1/auth/register` | регистрация |
| `POST /api/v1/auth/login` | вход |
| `POST /api/v1/auth/logout` | выход |
| `GET /oauth2/authorization/github` | OAuth2 GitHub |
| `GET /oauth2/authorization/google` | OAuth2 Google |
| `GET /api/v1/users/me` | профиль |
| `PATCH /api/v1/users/me` | редактирование профиля |
| `GET /api/v1/users/me/export` | GDPR-экспорт данных |
| `DELETE /api/v1/users/me` | GDPR-анонимизация |
| `POST /api/v1/documents` | загрузить документ |
| `GET /api/v1/documents?status=CHECKED&type=docx` | список с фильтрами |
| `GET /api/v1/documents/{id}` | карточка документа |
| `GET /api/v1/documents/{id}/download` | скачать исходник |
| `POST /api/v1/documents/{id}/check` | запустить проверку |
| `GET /api/v1/documents/{id}/result` | результат проверки |
| `GET /api/v1/documents/{id}/report/download` | PDF-отчёт |
| `GET /api/v1/documents/{id}/report/html` | HTML-отчёт |
| `GET /api/v1/documents/{id}/report/json` | JSON-отчёт |
| `POST /api/v1/documents/{id}/fix` | автоисправление |
| `PUT /api/v1/documents/{id}/webhook` | webhook |
| `POST /api/v1/documents/compare` | сравнение версий |
| `GET /api/v1/rule-settings` | правила |
| `POST /api/v1/rule-settings/import` | импорт правил JSON |
| `GET /api/v1/rule-settings/export` | экспорт правил JSON |
| `GET /api/v1/stats/export.csv` | CSV-экспорт статистики |

## 8. Что говорить про спорные пункты

Если спросят про OAuth2: «Подключён Spring Security OAuth2 Client. Для реального входа нужно подставить `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`; callback уже обрабатывается и выдаёт JWT».

Если спросят про HTTPS: «В локальной среде HTTP для удобства, но есть `APP_FORCE_HTTPS=true` и Kubernetes TLS ingress. В продакшене включается TLS через ingress/reverse proxy».

Если спросят про pgcrypto: «Миграция включает расширение `pgcrypto`; это база для шифрования чувствительных полей в PostgreSQL. В учебной среде сохранена читаемость email для логина и демонстрации».

Если спросят про Kubernetes/CI/CD/Gatling: «Есть базовые манифесты, HPA, TLS ingress, GitHub Actions и Gatling-сценарий. Для боевого деплоя нужны секреты, Docker registry и кластер».
