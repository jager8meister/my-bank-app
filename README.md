# My Bank App

Микросервисное банковское приложение на Spring Boot. Пользователь может редактировать данные аккаунта, пополнять и снимать деньги, переводить деньги другим пользователям.

## Сервисы

| Сервис | Порт | Описание |
|---|---|---|
| front-service | 8084 | Web UI (Thymeleaf). Единственная точка входа для браузера |
| gateway-service | 8080 | API Gateway. Принимает запросы от front-service, проверяет JWT, маршрутизирует в сервисы |
| accounts-service | 8081 | Хранит аккаунты и балансы. Публикует события `ACCOUNT_UPDATED` в Kafka |
| cash-service | 8082 | Пополнение и снятие наличных. Публикует события `CASH_DEPOSIT` / `CASH_WITHDRAWAL` в Kafka |
| transfer-service | 8085 | Переводы между счетами. Публикует события `TRANSFER_SENT` / `TRANSFER_RECEIVED` в Kafka |
| notifications-service | 8086 | Подписан на Kafka-топик `notifications`, логирует все события |
| auth-service | 8083 | Аутентификация и регистрация пользователей через Keycloak Admin API |
| keycloak | 9090 | OAuth2/OIDC сервер, realm: `bank-realm`. Доступен из браузера (LoadBalancer) |
| postgres | 5432 | PostgreSQL, единая БД с отдельными схемами на каждый сервис |
| kafka | 9092 | Apache Kafka (KRaft, без Zookeeper). Топик `notifications`, 1 партиция |

## Как всё работает

### Страница аккаунта

Браузер открывает `http://localhost:8084`. Если пользователь не авторизован — Spring Security автоматически перенаправляет его на страницу входа Keycloak (`http://localhost:9090`) по **Authorization Code Flow**. После успешной аутентификации Keycloak перенаправляет браузер обратно на front-service с кодом авторизации; front-service обменивает его на токены и сохраняет в OAuth2-сессии. После входа открывается страница с тремя блоками:
- **Данные аккаунта** — имя, дата рождения, текущий баланс, кнопка сохранить
- **Наличные** — ввести сумму и нажать «Положить» или «Снять»
- **Перевод** — выбрать получателя из списка, ввести сумму, нажать «Перевести»

Все действия на странице отправляются из front-service в gateway-service с Bearer JWT токеном пользователя (полученным от Keycloak по Authorization Code Flow). Gateway проверяет токен и пробрасывает его в нужный сервис через `tokenRelay()`:

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
auth-service     ─▶ accounts-service      (создать запись при регистрации)
auth-service     ─▶ keycloak Admin API    (создать пользователя в Keycloak)
```

Уведомления передаются через Apache Kafka (топик `notifications`):

```
cash-service          ─▶ Kafka [notifications]  CASH_DEPOSIT / CASH_WITHDRAWAL
transfer-service      ─▶ Kafka [notifications]  TRANSFER_SENT + TRANSFER_RECEIVED
accounts-service      ─▶ Kafka [notifications]  ACCOUNT_UPDATED (через outbox relay)
notifications-service ◀─ Kafka [notifications]  @KafkaListener, group: notifications-group
```

### Регистрация

Страница `http://localhost:8084/register` доступна без авторизации. front-service отправляет данные в gateway → auth-service, который:

1. Проверяет входные данные (логин 3–20 символов, строчные буквы/цифры/`_`/`-`; имя не пустое до 100 символов; пароль 6–100 символов; возраст ≥ 18 лет; пароли совпадают)
2. Создаёт пользователя в Keycloak через Admin API и назначает роли
3. Создаёт запись аккаунта в accounts-service через `POST /api/accounts/register`
4. Если шаг 3 упал — откатывает создание пользователя в Keycloak

### Уведомления

Три сервиса публикуют события в Kafka-топик `notifications` (JSON, ключ — логин пользователя):

| Сервис | Тип события | Когда |
|---|---|---|
| cash-service | `CASH_DEPOSIT` | После пополнения счёта |
| cash-service | `CASH_WITHDRAWAL` | После снятия наличных |
| transfer-service | `TRANSFER_SENT` | Отправителю после перевода |
| transfer-service | `TRANSFER_RECEIVED` | Получателю после перевода |
| accounts-service | `ACCOUNT_UPDATED` | После изменения профиля (через outbox relay) |

notifications-service подписан на топик (group: `notifications-group`, `auto.offset.reset=earliest`) и логирует каждое событие:

```
INFO  NotificationListener : Notification [CASH_DEPOSIT] for ivanov: Положено 500 руб
INFO  NotificationListener : Notification [TRANSFER_SENT] for ivanov: Вы перевели 300 руб пользователю petrov
```

Доставка at-least-once: при перезапуске consumer продолжает с последнего committed offset и воспроизводит все неподтверждённые сообщения.

### auth-service

Сервис аутентификации. При регистрации создаёт пользователя в Keycloak Admin API и запись в accounts-service. Также предоставляет вспомогательные endpoints: `POST /api/auth/token` (ROPC, для внутреннего использования) и `POST /api/auth/refresh`.

### Схема аутентификации и авторизации

| Тип взаимодействия | Flow | Клиент |
|---|---|---|
| Браузер → front-service | Authorization Code Flow | `front-client` |
| front-service → gateway-service | Bearer JWT (forwarding) | — |
| gateway-service → backend | tokenRelay() | — |
| cash/transfer → accounts-service | Client Credentials Flow | `microservices-client`, scope `microservice-scope` |
| auth-service → accounts-service | без токена (`/register` — публичный) | — |
| auth-service → Keycloak Admin API | Admin API (client credentials) | `admin` |

Keycloak доступен из браузера на `http://localhost:9090` (порт проброшен наружу через LoadBalancer). Пользователи могут обращаться только к данным своего аккаунта: все backend-сервисы проверяют `preferred_username` в JWT-токене.

## Стек

| | |
|---|---|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3, Spring WebFlux |
| Безопасность | Spring Security OAuth2, Keycloak, JWT |
| БД | PostgreSQL 16, Spring Data R2DBC |
| Очереди | Apache Kafka 3.9.0 (KRaft, без Zookeeper) |
| Инфраструктура | Kubernetes, Helm, Spring Cloud Gateway |
| Тесты | JUnit 5, Mockito, Testcontainers, Spring Cloud Contract, EmbeddedKafka |
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

Ожидаемое состояние: 10 подов в `Running` (включая `bank-app-kafka-0` StatefulSet) + 1 `Completed` (keycloak-init Job).

### 4. Запустить Helm-тесты

```bash
helm test bank-app --namespace bank
```

Тесты проверяют доступность actuator/health каждого сервиса и соединение с PostgreSQL. Все 10 тестов должны пройти со статусом `Succeeded`.

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
# Все сервисы (юнит-тесты + контрактные)
mvn test

# Один сервис
mvn test -pl accounts-service
```

Каждый сервис покрыт:
- **Юнит-тестами** — сервисный слой с Mockito
- **Интеграционными тестами** — `@SpringBootTest` + `@EmbeddedKafka` (Kafka) / Testcontainers (PostgreSQL)
- **Контрактными тестами** — Spring Cloud Contract (Groovy DSL): REST и Kafka messaging

> **Примечание:** `*IntegrationTest.java` исключены из стандартного запуска `mvn test` (см. конфигурацию Maven Surefire в родительском `pom.xml`). Запуск интеграционных тестов:
>
> ```bash
> # Kafka-интеграционные тесты (все сервисы)
> mvn test -pl cash-service,transfer-service,accounts-service,notifications-service \
>   -Dtest="*IntegrationTest,*ListenerTest" -DfailIfNoTests=false
>
> # Только cash-service Kafka-тест
> mvn test -pl cash-service -Dtest="CashKafkaIntegrationTest"
>
> # Только transfer-service Kafka-тест
> mvn test -pl transfer-service -Dtest="TransferKafkaIntegrationTest"
>
> # notifications-service listener-тест (EmbeddedKafka)
> mvn test -pl notifications-service -Dtest="NotificationListenerTest"
> ```

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
├── helm/                   # Helm-чарты (umbrella + 10 сабчартов)
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
│           ├── postgres/
│           ├── kafka/
│           └── common/           # library chart — shared deployment template
├── database/               # Скрипты инициализации БД
├── Makefile                # Команды сборки и деплоя
├── .env.example
└── pom.xml
```
