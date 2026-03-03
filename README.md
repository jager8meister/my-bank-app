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
| auth-service | 8083 | Аутентификация и регистрация пользователей через Keycloak Admin API |
| keycloak | — | OAuth2/OIDC сервер, realm: `bank-realm`. Порт не публикуется — доступен только внутри Docker-сети |
| postgres | 5432 | PostgreSQL, единая БД с отдельными схемами на каждый сервис |

## Как всё работает

### Страница аккаунта

Браузер открывает `http://localhost:8084`. Если пользователь не авторизован — front-service показывает собственную страницу входа (`/login`). Пользователь вводит логин и пароль: front-service передаёт их в auth-service, который вызывает Keycloak через ROPC (`grant_type=password`), получает JWT и сохраняет его в сессии. Keycloak недоступен из браузера напрямую. После входа открывается страница с тремя блоками:
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

Межсервисные вызовы идут напрямую по имени сервиса (Docker/Kubernetes DNS), минуя gateway (с JWT по client_credentials):

```
cash-service     ─▶ accounts-service      (пополнить / снять баланс)
transfer-service ─▶ accounts-service      (списать у отправителя, зачислить получателю)
accounts-service ─▶ notifications-service (отправить уведомление об операции)
auth-service     ─▶ accounts-service      (создать запись при регистрации)
auth-service     ─▶ keycloak Admin API    (создать пользователя в Keycloak)
```

### Регистрация

Страница `http://localhost:8084/register` доступна без авторизации. front-service отправляет данные в gateway → auth-service, который:

1. Проверяет входные данные (логин 3–20 символов, строчные буквы/цифры/`_`/`-`; имя не пустое до 100 символов; пароль 6–100 символов; возраст ≥ 18 лет; пароли совпадают)
2. Создаёт пользователя в Keycloak через Admin API и назначает роли
3. Создаёт запись аккаунта в accounts-service через `POST /api/accounts/register`
4. Если шаг 3 упал — откатывает создание пользователя в Keycloak

### Уведомления

accounts-service при каждой операции (пополнение, снятие, перевод) сохраняет событие в таблицу `outbox_events` в рамках той же транзакции. Фоновая задача каждые 5 секунд забирает необработанные события и отправляет в notifications-service, который их логирует. Получатель перевода видит уведомление при открытии страницы — в течение 5 минут после операции.

### auth-service

Центральный сервис аутентификации. Выдаёт JWT токены по логину/паролю через ROPC (`POST /api/auth/token`) и обновляет их (`POST /api/auth/refresh`), делегируя вызовы к Keycloak. При регистрации создаёт пользователя в Keycloak Admin API. Содержит собственную таблицу пользователей, при первом старте заполняет её тестовыми записями (`ivanov`, `petrov`, `sidorov`). Тестовые пользователи Keycloak импортируются из `keycloak/realm-export.json` при первом запуске.

## Стек

| | |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3, Spring WebFlux |
| Безопасность | Spring Security OAuth2, Keycloak, JWT |
| БД | PostgreSQL 16, Spring Data R2DBC |
| Инфраструктура | Kubernetes, Helm, Spring Cloud Gateway |
| Тесты | JUnit 5, Mockito, Testcontainers, Spring Cloud Contract |
| Сборка | Maven (multi-module) |
| Деплой | Docker Compose, Kubernetes (Helm) |

## Запуск в Docker

**Требования:** Docker Desktop 24+

```bash
cp .env.example .env
docker compose build
docker compose up -d
```

Дождаться пока все 9 контейнеров перейдут в статус `healthy`:

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

## Запуск в Kubernetes

**Требования:** Kubernetes (Rancher Desktop, Minikube или Docker Desktop с включённым K8s), Helm 3.x

### 1. Собрать Docker-образы

```bash
cp .env.example .env
docker compose build
```

### 2. Загрузить образы в кластер

Для **Rancher Desktop** (containerd):
```bash
# Образы собраны через docker compose build — импортировать в nerdctl:
docker save bank-accounts-service:latest | nerdctl --namespace k8s.io load
docker save bank-auth-service:latest | nerdctl --namespace k8s.io load
docker save bank-cash-service:latest | nerdctl --namespace k8s.io load
docker save bank-transfer-service:latest | nerdctl --namespace k8s.io load
docker save bank-notifications-service:latest | nerdctl --namespace k8s.io load
docker save bank-gateway-service:latest | nerdctl --namespace k8s.io load
docker save bank-front-service:latest | nerdctl --namespace k8s.io load
```

Для **Minikube**:
```bash
eval $(minikube docker-env)
docker compose build
```

### 3. Установить чарт

```bash
cd helm/bank-app
helm dependency update
helm install bank-app . --namespace bank --create-namespace
```

### 4. Дождаться готовности

```bash
kubectl wait --namespace bank --for=condition=ready pod --all --timeout=300s
kubectl get pods -n bank
```

### 5. Запустить Helm-тесты

```bash
helm test bank-app --namespace bank
```

Приложение: **http://localhost:30084**

### Переустановка / обновление

```bash
helm upgrade bank-app helm/bank-app --namespace bank
```

### Удаление

```bash
helm uninstall bank-app --namespace bank
```

---

## Запуск локально из исходников

**Требования:** JDK 21, Maven 3.8+, Docker (для инфраструктуры)

### 1. Запустить инфраструктуру

```bash
docker compose up -d postgres keycloak
```

Дождаться `healthy`:

```bash
docker compose ps postgres keycloak
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
2. `docker compose up -d postgres keycloak`
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

Keycloak не публикует порт наружу. Токен выдаёт auth-service через `POST /api/auth/token`.
Для API-тестирования временно добавьте в `docker-compose.yml` секцию `auth-service`:

```yaml
ports:
  - "8083:8083"
```

Затем получите токен:

```bash
TOKEN=$(curl -s -X POST http://localhost:8083/api/auth/token \
  -H 'Content-Type: application/json' \
  -d '{"login":"ivanov","password":"password"}' \
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
├── keycloak/               # Realm export + init.sh
├── helm/                   # Helm-чарты (umbrella + 9 сабчартов)
├── database/               # Скрипты инициализации БД
├── docker-compose.yml
├── .env.example
└── pom.xml
```
