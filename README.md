# My Bank App

## Архитектура

```
Browser / curl
     │
     ▼
front-service :8084  (UI, OAuth2 login через front-client)
     │
     ▼
gateway-service :8080  (API Gateway, JWT validation, oauth2Login)
     ├──▶ accounts-service :8081  (счета, балансы, R2DBC + PostgreSQL)
     ├──▶ cash-service :8082      (пополнение / снятие наличных)
     ├──▶ transfer-service :8085  (переводы между счетами)
     └──▶ notifications-service :8086 (уведомления об операциях)

auth-service :8083       (хранение пользователей, R2DBC + PostgreSQL)
config-server :8888      (Spring Cloud Config — общие properties)
eureka-server :8761      (Spring Cloud Eureka — service discovery)
keycloak :9090           (OAuth2 / OIDC сервер, realm: bank-realm)
postgres :5432           (единая БД, отдельные схемы на каждый сервис)
```

## Стек технологий

| Категория | Технологии |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3, Spring WebFlux (реактивный) |
| Безопасность | Spring Security OAuth2, Keycloak 23, JWT |
| БД | PostgreSQL 16, R2DBC (реактивный драйвер) |
| Инфраструктура | Spring Cloud (Config, Eureka, Gateway) |
| Отказоустойчивость | Resilience4j (Circuit Breaker, Retry, TimeLimiter) |
| Сборка | Maven (multi-module) |
| Деплой | Docker Compose |

## Быстрый старт

### Требования
- Docker и Docker Compose
- Файл `.env` в корне проекта (см. раздел Конфигурация)

### Запуск

```bash
docker compose build
docker compose up -d
```

Дождаться пока все контейнеры перейдут в статус `healthy`:

```bash
docker compose ps
```

Приложение доступно на **http://localhost:8084**

### Тестовые пользователи

| Логин | Пароль |
|---|---|
| ivanov | password |
| petrov | password |
| sidorov | password |

## Конфигурация

Все параметры задаются через `.env` в корне проекта:

```env
# База данных
POSTGRES_DB=bankdb
POSTGRES_USER=bankuser
POSTGRES_PASSWORD=bankpass
POSTGRES_PORT=5432
DB_PASSWORD=bankpass

# Keycloak
KEYCLOAK_PORT=9090
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_EXTERNAL_URL=http://localhost:9090
KEYCLOAK_INTERNAL_URL=http://keycloak:9090
KEYCLOAK_REALM=bank-realm

# OAuth2 клиенты
MICROSERVICES_CLIENT_SECRET=...
FRONT_CLIENT_SECRET=...
GATEWAY_CLIENT_SECRET=...

# Порты сервисов
CONFIG_SERVER_PORT=8888
EUREKA_SERVER_PORT=8761
GATEWAY_PORT=8080
ACCOUNTS_SERVICE_PORT=8081
CASH_SERVICE_PORT=8082
AUTH_SERVICE_PORT=8083
FRONT_SERVICE_PORT=8084
TRANSFER_SERVICE_PORT=8085
NOTIFICATIONS_SERVICE_PORT=8086
```

## API

Все запросы через Gateway `:8080`. Требуется Bearer-токен:

```bash
# Получить токен
TOKEN=$(curl -s -X POST "http://localhost:9090/realms/bank-realm/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=front-client&client_secret=<secret>&username=ivanov&password=password" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
```

### Счета

```bash
# Получить информацию о счёте
GET /api/accounts/{login}

# Обновить имя и дату рождения
PUT /api/accounts/{login}
{"name": "Иванов Иван", "birthdate": "1990-06-15"}
```

### Наличные

```bash
# Пополнение или снятие
POST /api/cash/{login}
{"value": 1000, "action": "PUT"}   # пополнение
{"value": 500,  "action": "GET"}   # снятие
```

### Переводы

```bash
POST /api/transfer
{"senderLogin": "ivanov", "recipientLogin": "petrov", "amount": 300}
```

## Разработка

### Запуск тестов

```bash
# Все сервисы
mvn test

# Конкретный сервис
mvn test -pl accounts-service
```

### Пересборка сервиса

```bash
docker compose build accounts-service
docker compose up -d accounts-service
```

### Логи

```bash
docker compose logs -f accounts-service
docker compose logs --since=10m gateway-service | grep ERROR
```

## Структура проекта

```
my-bank-app/
├── accounts-service/       # Управление счетами и балансами
├── auth-service/           # Пользователи и аутентификация
├── cash-service/           # Операции с наличными
├── transfer-service/       # Переводы между счетами
├── notifications-service/  # Уведомления об операциях
├── gateway-service/        # API Gateway
├── front-service/          # Web UI (Thymeleaf)
├── config-server/          # Централизованная конфигурация
├── eureka-server/          # Service Discovery
├── keycloak/               # Realm export и темы
├── database/               # Скрипты инициализации БД
├── docker-compose.yml
└── .env
```
