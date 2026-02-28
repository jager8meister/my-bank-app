# My Bank App

Микросервисное банковское приложение на Spring Boot. Пользователь может редактировать данные аккаунта, пополнять и снимать деньги, переводить деньги другим пользователям.

## Сервисы

| Сервис | Порт | Описание |
|---|---|---|
| front-service | 8084 | Web UI (Thymeleaf). Единственная точка входа для браузера |
| gateway-service | 8080 | API Gateway. Принимает запросы от front-service, проверяет JWT, маршрутизирует в сервисы |
| accounts-service | 8081 | Хранит аккаунты и балансы. Отправляет уведомления |
| cash-service | 8082 | Пополнение и снятие наличных |
| transfer-service | 8085 | Переводы между счетами |
| notifications-service | 8086 | Принимает уведомления от accounts-service и логирует их |
| auth-service | 8083 | Хранит пользователей, инициализирует тестовые данные |
| config-server | 8888 | Централизованная конфигурация (Spring Cloud Config) |
| eureka-server | 8761 | Service Discovery (Spring Cloud Netflix Eureka) |
| keycloak | 9090 | OAuth2/OIDC сервер, realm: `bank-realm` |
| postgres | 5432 | PostgreSQL, единая БД с отдельными схемами на каждый сервис |

## Как всё работает

### Страница аккаунта

Браузер открывает `http://localhost:8084`. Если пользователь не авторизован — front-service перенаправляет на страницу входа Keycloak (Authorization Code Flow). После входа открывается страница с тремя блоками:
- **Данные аккаунта** — имя, дата рождения, текущий баланс, кнопка сохранить
- **Наличные** — ввести сумму и нажать «Положить» или «Снять»
- **Перевод** — выбрать получателя из списка, ввести сумму, нажать «Перевести»

Все действия на странице отправляются из front-service в gateway-service с Bearer JWT токеном пользователя. Gateway проверяет токен и передаёт запрос в нужный сервис:

```
Браузер
  └─▶ front-service :8084
        └─▶ gateway-service :8080
              ├─▶ accounts-service :8081  (/api/accounts/**)
              ├─▶ cash-service :8082      (/api/cash/**)
              └─▶ transfer-service :8085  (/api/transfer/**)
```

Межсервисные вызовы идут напрямую через Eureka, минуя gateway:

```
cash-service     ─▶ accounts-service   (пополнить / снять баланс)
transfer-service ─▶ accounts-service   (списать у отправителя, зачислить получателю)
accounts-service ─▶ notifications-service  (отправить уведомление об операции)
```

### Регистрация

Страница `http://localhost:8084/register` доступна без авторизации. front-service:

1. Проверяет входные данные (логин 3–20 символов, строчные буквы/цифры/`_`/`-`; имя не пустое до 100 символов; пароль 6–100 символов; возраст ≥ 18 лет; пароли совпадают)
2. Создаёт пользователя в Keycloak через Admin API и назначает роли
3. Создаёт запись аккаунта в accounts-service через `POST /api/accounts/register`
4. Если шаг 3 упал — откатывает создание пользователя в Keycloak

### Уведомления

accounts-service при каждой операции (пополнение, снятие, перевод) сохраняет событие в таблицу `outbox_events` в рамках той же транзакции. Фоновая задача каждые 5 секунд забирает необработанные события и отправляет в notifications-service, который их логирует. Получатель перевода видит уведомление при открытии страницы — в течение 5 минут после операции.

### auth-service

Содержит собственную таблицу пользователей. При первом старте создаёт тестовых пользователей `ivanov`, `petrov`, `sidorov`. В основном потоке аутентификации не участвует — Keycloak управляет пользователями самостоятельно.

## Стек

| | |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3, Spring WebFlux |
| Безопасность | Spring Security OAuth2, Keycloak, JWT |
| БД | PostgreSQL 16, Spring Data R2DBC |
| Инфраструктура | Spring Cloud Config, Eureka, Gateway |
| Тесты | JUnit 5, Mockito, Testcontainers, Spring Cloud Contract |
| Сборка | Maven (multi-module) |
| Деплой | Docker Compose |

## Запуск в Docker

**Требования:** Docker Desktop 24+

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

Дождаться пока все 11 контейнеров перейдут в статус `healthy`:

```bash
docker compose ps
```

Приложение: **http://localhost:8084**

### Тестовые пользователи

| Логин | Пароль |
|---|---|
| ivanov | password |
| petrov | password |
| sidorov | password |

## Запуск локально из исходников

**Требования:** JDK 21, Maven 3.8+, Docker (для инфраструктуры)

### 1. Запустить инфраструктуру

```bash
docker compose up -d postgres keycloak config-server eureka-server
```

Дождаться `healthy`:

```bash
docker compose ps postgres keycloak config-server eureka-server
```

### 2. Собрать проект

```bash
mvn clean package -DskipTests
```

### 3. Запустить сервисы

Запускать в отдельных терминалах в следующем порядке:

```bash
java -jar gateway-service/target/gateway-service-*.jar
java -jar auth-service/target/auth-service-*.jar
java -jar accounts-service/target/accounts-service-*.jar
java -jar cash-service/target/cash-service-*.jar
java -jar transfer-service/target/transfer-service-*.jar
java -jar notifications-service/target/notifications-service-*.jar
java -jar front-service/target/front-service-*.jar
```

Приложение: **http://localhost:8084**

### Запуск в IntelliJ IDEA

1. `File → Open` — выбрать корневой `pom.xml`, открыть как проект
2. `docker compose up -d postgres keycloak config-server eureka-server`
3. Запустить каждый `*Application.java` через Run в порядке из шага 3
4. Открыть **http://localhost:8084**

## Тесты

```bash
# Все сервисы
mvn test

# Один сервис
mvn test -pl accounts-service
```

Каждый сервис покрыт:
- **Юнит-тестами** — сервисный слой с Mockito
- **Интеграционными тестами** — `@SpringBootTest` + Testcontainers
- **Контрактными тестами** — Spring Cloud Contract (Groovy DSL)

## Конфигурация

Параметры задаются через `.env` в корне проекта. Пример — `.env.example`:

```env
# PostgreSQL
POSTGRES_DB=bankdb
POSTGRES_USER=bankuser
POSTGRES_PASSWORD=bankpass
POSTGRES_PORT=5432
DB_PASSWORD=bankpass

# Keycloak
KEYCLOAK_PORT=9090
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=admin
KEYCLOAK_REALM=bank-realm
KEYCLOAK_HOSTNAME=localhost
KEYCLOAK_EXTERNAL_URL=http://localhost:9090
KEYCLOAK_INTERNAL_URL=http://keycloak:9090

# OAuth2 секреты (должны совпадать с keycloak/realm-export.json)
MICROSERVICES_CLIENT_SECRET=microservices-secret-key-12345
FRONT_CLIENT_SECRET=front-client-secret-key-67890
GATEWAY_CLIENT_SECRET=gateway-client-secret-key-11111

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

Все запросы через Gateway `:8080` с Bearer JWT токеном.

```bash
# Получить токен
TOKEN=$(curl -s -X POST "http://localhost:9090/realms/bank-realm/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=front-client&client_secret=front-client-secret-key-67890&username=ivanov&password=password" \
  | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
```

### Аккаунты

```
GET /api/accounts/{login}
  → данные аккаунта: имя, дата рождения, баланс, список других пользователей

PUT /api/accounts/{login}
  Body: {"name": "Иванов Иван", "birthdate": "1990-06-15"}
  → обновить имя и дату рождения

POST /api/accounts/register
  Body: {"login": "newuser", "name": "Имя", "birthdate": "1995-01-01"}
  → создать аккаунт (вызывается front-service при регистрации, без токена)
```

### Наличные

```
POST /api/cash/{login}
  Body: {"value": 1000, "action": "PUT"}   → пополнение
        {"value": 500,  "action": "GET"}   → снятие
```

### Переводы

```
POST /api/transfer
  Body: {"senderLogin": "ivanov", "recipientLogin": "petrov", "amount": 300}
```

## Структура проекта

```
my-bank-app/
├── accounts-service/       # Аккаунты и балансы
├── auth-service/           # Хранение пользователей, инициализация тестовых данных
├── cash-service/           # Пополнение и снятие
├── transfer-service/       # Переводы между счетами
├── notifications-service/  # Логирование уведомлений
├── gateway-service/        # API Gateway
├── front-service/          # Web UI
├── config-server/          # Централизованная конфигурация
├── eureka-server/          # Service Discovery
├── keycloak/               # Realm export
├── database/               # Скрипты инициализации БД
├── docker-compose.yml
├── .env.example
└── pom.xml
```
