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
| keycloak | — | OAuth2/OIDC сервер, realm: `bank-realm`. Доступен только внутри кластера |
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

Межсервисные вызовы идут напрямую по имени сервиса (Kubernetes DNS), минуя gateway (с JWT по client_credentials):

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

Центральный сервис аутентификации. Выдаёт JWT токены по логину/паролю через ROPC (`POST /api/auth/token`) и обновляет их (`POST /api/auth/refresh`), делегируя вызовы к Keycloak. При регистрации создаёт пользователя в Keycloak Admin API.

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
| Деплой | Kubernetes (Helm) |

## Тестовые пользователи

| Логин | Пароль |
|---|---|
| ivanov | password |
| petrov | password |
| sidorov | password |

## Запуск в Kubernetes

**Требования:** Docker Desktop с включённым Kubernetes, Helm 3.12+

### 1. Собрать Docker-образы

```bash
make build
```

Или по одному сервису:

```bash
make build-accounts-service
make build-auth-service
# и т.д.
```

### 2. Развернуть кластер

```bash
make deploy
```

Helm установит все сервисы в namespace `bank`, дождётся готовности всех подов (timeout 10m) и проверит rollout status.

### 3. Проверить статус

```bash
make status
# или
kubectl get pods -n bank
```

Ожидаемое состояние: 9 подов в `Running` + 1 `Completed` (keycloak-init Job).

### 4. Запустить Helm-тесты

```bash
helm test bank-app --namespace bank
```

Тесты проверяют доступность actuator/health каждого сервиса и соединение с PostgreSQL. Все 9 тестов должны пройти со статусом `Succeeded`.

Приложение: **http://localhost:8084**

### Переустановка с нуля

```bash
make rebuild
```

Выполняет: `helm uninstall` → удаление PVC → `make build` → `make deploy`.

### Перезапуск подов без переустановки

```bash
make restart
```

### Логи сервиса

```bash
make logs-front-service
make logs-auth-service
# и т.д.
```

### Удаление кластера

```bash
make undeploy   # удалить helm release + PVC
make clean      # удалить всё включая namespace
```

---

## Запуск локально из исходников

**Требования:** JDK 21, Maven 3.8+, запущенный Kubernetes кластер (для postgres и keycloak)

### 1. Запустить инфраструктуру в Kubernetes

```bash
make deploy
```

### 2. Пробросить порты postgres и keycloak

```bash
kubectl port-forward -n bank svc/postgres 5432:5432 &
kubectl port-forward -n bank svc/keycloak 9090:9090 &
```

### 3. Собрать проект

```bash
mvn clean package -DskipTests
```

### 4. Запустить сервисы

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

## API

Все запросы через Gateway `:8080` с Bearer JWT токеном.

Получить токен через auth-service:

```bash
# Пробросить порт auth-service
kubectl port-forward -n bank svc/auth-service 8083:8083

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
├── auth-service/           # Аутентификация, выдача JWT токенов
├── cash-service/           # Пополнение и снятие
├── transfer-service/       # Переводы между счетами
├── notifications-service/  # Логирование уведомлений
├── gateway-service/        # API Gateway
├── front-service/          # Web UI
├── keycloak/               # Realm export + init.sh
├── helm/                   # Helm-чарты (umbrella + 9 сабчартов)
│   └── bank-app/
│       ├── Chart.yaml
│       ├── values.yaml
│       └── charts/
│           ├── accounts-service/
│           ├── auth-service/
│           ├── cash-service/
│           ├── transfer-service/
│           ├── notifications-service/
│           ├── gateway-service/
│           ├── front-service/
│           ├── keycloak/
│           └── postgres/
├── database/               # Скрипты инициализации БД
├── Makefile                # Команды сборки и деплоя
├── .env.example
└── pom.xml
```
