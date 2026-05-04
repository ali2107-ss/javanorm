# Руководство системного программиста

## Назначение

Документ описывает запуск, конфигурацию, обслуживание и проверку системы «НормаКонтроль».

## Состав системы

| Компонент | Назначение |
|---|---|
| `normacontrol-api` | Spring Boot приложение |
| `postgres` | основная база данных |
| `redis` | refresh-токены, security/cache инфраструктура |
| `kafka`, `zookeeper` | очередь асинхронной проверки |
| `minio` | хранение документов и отчётов |
| `prometheus` | сбор метрик |
| `grafana` | dashboards |
| `postgres-backup` | backup PostgreSQL каждые 6 часов |

## Запуск

```bash
docker compose up -d
```

Проверка:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
```

Логи:

```bash
docker compose logs -f normacontrol-api
```

## Конфигурация

Основные параметры передаются через env:

```text
SPRING_PROFILES_ACTIVE=prod
POSTGRES_DB=normacontrol
POSTGRES_USER=norma
POSTGRES_PASSWORD=norma
JWT_SECRET=...
APP_FORCE_HTTPS=false
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
```

Конфигурация приложения находится в `src/main/resources/application.yml`.

## База данных

Миграции запускаются автоматически через Liquibase:

```text
src/main/resources/db/changelog/db.changelog-master.xml
src/main/resources/db/changelog/V*.sql
```

Показать таблицы:

```bash
docker compose exec postgres psql -U norma -d normacontrol -c "\dt"
```

## Backup

Сервис `postgres-backup` в `docker-compose.yml` делает резервную копию каждые 6 часов и хранит копии 30 дней.

Папка/volume:

```text
postgres_backups
```

## Monitoring

Метрики:

```text
http://localhost:8080/actuator/prometheus
```

Grafana:

```text
http://localhost:3000
```

## Kubernetes

Манифесты:

```text
k8s/namespace.yaml
k8s/deployment.yaml
k8s/service.yaml
k8s/hpa.yaml
k8s/ingress-tls.yaml
k8s/secrets.example.yaml
```

Пример применения:

```bash
kubectl apply -f k8s/
```

Перед реальным деплоем нужно создать секреты и указать Docker registry.

## CI/CD

GitHub Actions workflow находится в:

```text
.github/workflows/ci.yml
```

Он предназначен для сборки, тестов и проверки проекта при push/pull request.

## Нагрузочный тест

Gatling-сценарий:

```text
load-tests/gatling/NormaControlSimulation.scala
```

Сценарий проверяет health, login, список документов и Swagger.
