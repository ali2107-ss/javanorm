# Документ для защиты проекта «НормаКонтроль»

## 1. Короткая речь

«НормаКонтроль» — это Java/Spring Boot система для автоматической проверки документов на соответствие ГОСТ 19.201-78. Пользователь загружает DOCX, PDF, TXT или MD файл, система сохраняет его в MinIO, ставит задачу проверки в Kafka, разбирает документ, применяет набор стратегий нормоконтроля, считает итоговый балл, показывает нарушения и формирует PDF-отчёт. Также реализованы антиплагиат, автоисправление DOCX, статистика, аудит, JWT-авторизация и Swagger-документация.

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
   - поиск/фильтр документов;
   - скачать исходник;
   - открыть проверку;
   - скачать PDF-отчёт;
   - нажать автоисправление;
   - показать статистику.

5. Показать API:
   ```text
   http://localhost:8080/api/docs
   ```

6. Показать мониторинг:
   ```text
   http://localhost:3000
   ```

## 3. Где что лежит в проекте

| Часть | Путь |
|---|---|
| Точка входа | `src/main/java/ru/normacontrol/NormaControlApplication.java` |
| REST-контроллеры | `src/main/java/ru/normacontrol/presentation/controller` |
| Use Case'ы | `src/main/java/ru/normacontrol/application/usecase` |
| Доменные сущности | `src/main/java/ru/normacontrol/domain/entity` |
| Правила проверки ГОСТ | `src/main/java/ru/normacontrol/domain/checker/strategy` |
| Движок правил | `src/main/java/ru/normacontrol/domain/service/GostRuleEngine.java` |
| JPA-сущности | `src/main/java/ru/normacontrol/infrastructure/persistence/entity` |
| Репозитории | `src/main/java/ru/normacontrol/infrastructure/persistence/repository` |
| Kafka | `src/main/java/ru/normacontrol/infrastructure/kafka` |
| MinIO | `src/main/java/ru/normacontrol/infrastructure/minio` |
| Redis | `src/main/java/ru/normacontrol/infrastructure/redis`, `security/BruteForceService.java` |
| JWT/Security | `src/main/java/ru/normacontrol/infrastructure/security` |
| PDF-отчёт | `src/main/java/ru/normacontrol/infrastructure/report/ReportGenerator.java` |
| Автоисправление | `src/main/java/ru/normacontrol/infrastructure/export/DocumentAutoFixService.java` |
| Антиплагиат | `src/main/java/ru/normacontrol/infrastructure/plagiarism` |
| WebSocket progress | `src/main/java/ru/normacontrol/infrastructure/websocket` |
| Статический UI | `src/main/resources/static` |
| Конфиг приложения | `src/main/resources/application.yml` |
| Миграции БД | `src/main/resources/db/changelog` |
| Docker | `Dockerfile`, `docker-compose.yml` |
| Kubernetes | `k8s` |
| CI/CD | `.github/workflows/ci.yml` |

## 4. База данных

Используется PostgreSQL. Схема создаётся Liquibase-миграциями.

Основные таблицы:

| Таблица | Назначение |
|---|---|
| `users` | пользователи |
| `roles`, `user_roles` | роли RBAC |
| `documents` | метаданные загруженных документов |
| `check_results` | результаты проверок |
| `violations` | найденные нарушения |
| `refresh_tokens` | refresh-токены |
| `audit_logs` | журнал аудита |
| `check_strategy_settings` | включение/отключение правил |
| `document_text_hashes` | данные антиплагиата |

Команда для показа таблиц:

```bash
docker compose exec postgres psql -U norma -d normacontrol -c "\dt"
```

Команда для показа документов по пользователям:

```bash
docker compose exec postgres psql -U norma -d normacontrol -c "select u.email, count(d.id) from users u left join documents d on d.owner_id = u.id and d.deleted = false group by u.email;"
```

## 5. Библиотеки и зачем они нужны

| Библиотека | Зачем |
|---|---|
| Spring Boot Web | REST API и статический UI |
| Spring Security | JWT-защита, RBAC |
| Spring Data JPA + Hibernate | работа с PostgreSQL |
| Liquibase | миграции схемы БД |
| Spring Kafka | очередь проверки документов |
| Spring Data Redis | brute-force защита, кэш AI-рекомендаций |
| MinIO SDK | объектное хранилище файлов |
| Apache POI | чтение и изменение DOCX |
| Apache PDFBox | чтение PDF |
| iText 7 | генерация PDF-отчётов |
| MapStruct | маппинг DTO |
| JJWT | создание и проверка JWT |
| SpringDoc OpenAPI | Swagger UI |
| Micrometer Prometheus | метрики |
| Lombok | меньше шаблонного Java-кода |
| JUnit 5 / Mockito / Testcontainers | тестирование |

## 6. Как показать фреймворки

Spring Boot:
Показать `NormaControlApplication.java`, `@SpringBootApplication`, затем любой `@RestController`.

Spring Security:
Показать `SecurityConfig.java`, `JwtAuthFilter.java`, `@PreAuthorize`.

Spring Data JPA:
Показать `DocumentJpaEntity.java`, `DocumentJpaRepository.java`.

Liquibase:
Показать `db.changelog-master.xml` и файлы `V*.sql`.

Kafka:
Показать `DocumentCheckProducer.java` и `DocumentCheckConsumer.java`.

MinIO:
Показать `MinioStorageService.java`.

Swagger:
Открыть `http://localhost:8080/api/docs`.

Prometheus/Grafana:
Открыть `/actuator/prometheus` и `http://localhost:3000`.

## 7. Основные endpoint'ы

| Endpoint | Назначение |
|---|---|
| `POST /api/v1/auth/login` | вход |
| `POST /api/v1/documents` | загрузить документ |
| `GET /api/v1/documents` | список документов |
| `GET /api/v1/documents/{id}/download` | скачать исходник |
| `POST /api/v1/documents/{id}/check` | запустить проверку |
| `GET /api/v1/documents/{id}/result` | результат проверки |
| `GET /api/v1/documents/{id}/report/download` | скачать PDF-отчёт |
| `POST /api/v1/documents/{id}/fix` | автоисправление |
| `POST /api/v1/documents/compare` | сравнить версии |
| `GET /api/v1/stats/top-violations` | статистика нарушений |
| `GET /api/v1/rule-settings` | набор правил |
| `PATCH /api/v1/rule-settings` | включить/выключить правила |

## 8. Что говорить про ограничения

Некоторые требования ТЗ являются продакшен-уровня и отмечены как развитие: OAuth2, webhook callback, HTTPS через reverse proxy, pgcrypto-шифрование, регулярные backup-job, полноценный Kubernetes-кластер и Gatling-нагрузочные тесты. Основной рабочий сценарий нормоконтроля реализован и демонстрируется локально через Docker Compose.
