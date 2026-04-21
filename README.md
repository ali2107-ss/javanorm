# ПРОГРАММАНормаКонтроль

Система автоматической проверки документов на соответствие **ГОСТ 19.201-78** «Техническое задание. Требования к содержанию и оформлению».

## Архитектура

Проект построен по принципу **Clean Architecture**:

```
src/main/java/ru/normacontrol/
├── domain/                      # Доменный слой (ядро)
│   ├── entity/                  # Сущности: User, Document, CheckResult, Violation, Role
│   ├── enums/                   # Перечисления: RoleName, DocumentStatus, ViolationSeverity
│   ├── repository/              # Порты репозиториев (интерфейсы)
│   └── service/                 # GostRuleEngine — движок проверки ГОСТ
│
├── application/                 # Слой приложения
│   ├── dto/                     # Request/Response DTO
│   ├── mapper/                  # MapStruct маппинг
│   └── usecase/                 # Use Cases: Auth, Document, CheckDocument, UserManagement
│
├── infrastructure/              # Инфраструктурный слой
│   ├── persistence/             # JPA-сущности, Spring Data репозитории, адаптеры
│   ├── kafka/                   # Kafka Producer/Consumer для очереди проверок
│   ├── minio/                   # MinIO файловое хранилище
│   ├── redis/                   # Redis конфигурация (кэширование)
│   ├── security/                # JWT, OAuth2, Spring Security
│   └── parser/                  # Apache POI (DOCX) + PDFBox (PDF)
│
└── presentation/                # Слой представления
    ├── controller/              # REST API контроллеры
    ├── advice/                  # Глобальный обработчик ошибок
    └── config/                  # OpenAPI/Swagger конфигурация
```

## Стек технологий

| Компонент           | Технология                        |
|---------------------|-----------------------------------|
| Язык                | Java 17+                          |
| Фреймворк           | Spring Boot 3.2.5                 |
| БД                  | PostgreSQL 16 + Liquibase         |
| Кэш                | Redis 7                           |
| Очередь             | Apache Kafka 3.7 (KRaft)          |
| Хранилище файлов    | MinIO (S3-совместимое)            |
| Безопасность        | Spring Security + JWT + OAuth2    |
| Документация API    | SpringDoc OpenAPI 3.0 (Swagger)   |
| Парсинг DOCX        | Apache POI 5.2.5                  |
| Парсинг PDF         | Apache PDFBox 3.0.2               |
| Маппинг             | MapStruct 1.5.5                   |
| Утилиты             | Lombok                            |
| Сборка              | Gradle 8.7                        |

## Быстрый старт

### 1. Запуск инфраструктуры

```bash
docker-compose up -d
```

### 2. Сборка и запуск приложения

```bash
./gradlew bootRun
```

### 3. Swagger UI

Открыть в браузере: **http://localhost:8081/api/docs**

## API Endpoints

### Аутентификация (`/api/auth`)
| Метод | Путь              | Описание                      |
|-------|--------------------|-------------------------------|
| POST  | `/auth/register`   | Регистрация                   |
| POST  | `/auth/login`      | Вход                          |
| POST  | `/auth/refresh`    | Обновление токена             |

### Документы (`/api/documents`)
| Метод  | Путь                     | Описание                      |
|--------|--------------------------|-------------------------------|
| POST   | `/documents`             | Загрузить документ            |
| GET    | `/documents`             | Список своих документов       |
| GET    | `/documents/{id}`        | Получить документ             |
| DELETE | `/documents/{id}`        | Удалить документ              |

### Результаты проверки (`/api/check-results`)
| Метод | Путь                                  | Описание                      |
|-------|----------------------------------------|-------------------------------|
| GET   | `/check-results/document/{id}`         | Последний результат проверки  |
| GET   | `/check-results/document/{id}/history` | История проверок              |
| GET   | `/check-results/{id}`                  | Результат по ID               |

### Администрирование (`/api/admin`)
| Метод | Путь                          | Описание                      |
|-------|-------------------------------|-------------------------------|
| PATCH | `/admin/users/{id}/toggle`    | Блокировка/разблокировка      |

## ГОСТ 19.201-78 — Проверяемые правила

- ✅ Обязательные разделы (Введение, Основания, Назначение, Требования, и т.д.)
- ✅ Подразделы раздела «Требования к программе» (функциональные, надёжность, и др.)
- ✅ Оформление: шрифт (12–14 пт), поля страницы, межстрочный интервал
- ✅ Наличие титульного листа
- ✅ Лист утверждения / согласования
- ✅ Нумерация страниц

## RBAC Роли

| Роль          | Описание                              |
|---------------|---------------------------------------|
| ROLE_USER     | Загрузка документов, просмотр своих   |
| ROLE_REVIEWER | + просмотр чужих, история проверок    |
| ROLE_ADMIN    | + управление пользователями           |