# Диаграммы проекта

## 1. Use Case

```mermaid
flowchart LR
  User[Пользователь] --> Register[Регистрация]
  User --> Login[Вход JWT/OAuth2]
  User --> Upload[Загрузка документа]
  User --> Check[Запуск проверки]
  User --> View[Просмотр результата]
  User --> Download[Скачивание отчёта]
  User --> Fix[Автоисправление DOCX]
  Reviewer[Рецензент] --> Rules[Управление правилами]
  Admin[Администратор] --> Stats[Статистика]
  Admin --> Audit[Аудит]
```

## 2. Доменная модель

```mermaid
classDiagram
  class User {
    UUID id
    String email
    String fullName
  }
  class Role {
    RoleName name
  }
  class Document {
    UUID id
    String originalFilename
    DocumentStatus status
  }
  class CheckResult {
    int complianceScore
    boolean passed
  }
  class Violation {
    String ruleCode
    ViolationSeverity severity
  }
  User "1" --> "*" Document
  User "*" --> "*" Role
  Document "1" --> "*" CheckResult
  CheckResult "1" --> "*" Violation
```

## 3. ER Diagram

```mermaid
erDiagram
  users ||--o{ documents : owns
  users }o--o{ roles : has
  documents ||--o{ check_results : checked_by
  check_results ||--o{ violations : contains
  documents ||--o{ document_webhooks : notifies
  users ||--o{ refresh_tokens : owns
  users ||--o{ audit_logs : writes
```

## 4. Sequence: запуск проверки

```mermaid
sequenceDiagram
  participant U as User
  participant API as DocumentController
  participant UC as CheckDocumentUseCase
  participant K as Kafka
  participant C as DocumentCheckConsumer
  participant E as GostRuleEngine
  participant DB as PostgreSQL
  U->>API: POST /documents/{id}/check
  API->>UC: initiateCheck()
  UC->>DB: status=PENDING
  UC->>K: check.requested
  K->>C: consume
  C->>DB: status=CHECKING
  C->>E: run strategies
  E-->>C: violations, score
  C->>DB: save result, status=CHECKED
```

## 5. C4 Level 2 Components

```mermaid
flowchart TB
  Browser --> Controllers[Presentation Controllers]
  Controllers --> UseCases[Application Use Cases]
  UseCases --> Domain[Domain Rules Engine]
  UseCases --> Repositories[Repository Ports]
  Repositories --> Jpa[JPA Adapters]
  Jpa --> Postgres[(PostgreSQL)]
  UseCases --> Kafka[Kafka Producer]
  Kafka --> Consumer[Kafka Consumer]
  Consumer --> Domain
  UseCases --> Storage[MinIO Storage]
```

## 6. Состояния документа

```mermaid
stateDiagram-v2
  [*] --> UPLOADED
  UPLOADED --> PENDING
  PENDING --> CHECKING
  CHECKING --> CHECKED
  CHECKING --> FAILED
  CHECKED --> DELETED
  FAILED --> PENDING
```

## 7. CI/CD Pipeline

```mermaid
flowchart LR
  Push[Push / Pull Request] --> Checkout[Checkout]
  Checkout --> Build[Gradle build]
  Build --> Test[JUnit / Mockito / Testcontainers]
  Test --> Coverage[Jacoco]
  Coverage --> Docker[Docker image build]
  Docker --> Ready[Artifact ready]
```

## 8. Deployment

```mermaid
flowchart TB
  Internet --> Ingress[TLS Ingress]
  Ingress --> Service[Kubernetes Service]
  Service --> Pod1[normacontrol-api pod]
  Service --> Pod2[normacontrol-api pod]
  HPA[HorizontalPodAutoscaler] --> Pod1
  HPA --> Pod2
  Pod1 --> Postgres[(PostgreSQL)]
  Pod1 --> Redis[(Redis)]
  Pod1 --> Kafka[(Kafka)]
  Pod1 --> MinIO[(MinIO)]
  Prometheus --> Pod1
  Grafana --> Prometheus
```
