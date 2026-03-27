# Piattaforma Zeta — Piano di Implementazione (Semplificato)

> Piano operativo per l'implementazione della Piattaforma Zeta.
> Tutto gira in locale con `docker-compose up`. Niente cluster Kubernetes reale.
> Le scelte tecniche e le giustificazioni sono in `piattaforma-zeta-scelte-tecniche.md`.

---

## Struttura Complessiva del Progetto

```
piattaforma-zeta/
├── docker-compose.yml
├── README.md
├── docs/
│   └── architecture.md              # Diagrammi Mermaid e doc architetturale
├── gateway/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/zeta/gateway/
├── user-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/zeta/user/
├── pec-service/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/zeta/pec/
├── ai-engine/
│   ├── requirements.txt
│   ├── Dockerfile
│   └── src/
└── config/
    └── keycloak/
        └── zeta-realm.json
```

> **Non ci sono directory `k8s/`** — il progetto è pensato per docker-compose. Un esempio di manifest K8s è incluso nella documentazione architetturale.

---

## FASE 0 — Setup Progetto e Infrastruttura Docker

### Obiettivo
Creare la struttura multi-modulo Maven, i Dockerfile e docker-compose con tutti i servizi.

### Step 0.1 — Parent POM Maven
- Creare `piattaforma-zeta/pom.xml` (parent POM) con:
  - `spring-boot-starter-parent` 3.x come parent
  - Java 21 come `java.version`
  - Moduli: `gateway`, `user-service`, `pec-service`
  - Dependency management per: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`, `spring-kafka`, `postgresql`, `lombok`, `mapstruct`, `logstash-logback-encoder`, `resilience4j-spring-boot3`
- Ogni sotto-modulo eredita dal parent.

### Step 0.2 — Dockerfile per ogni servizio Java
- Template unico, replicato per `user-service`, `pec-service`, `gateway`:
  - **Stage 1** (`builder`): `maven:3.9-eclipse-temurin-21` → copia pom + src → `mvn package -DskipTests`
  - **Stage 2** (`runtime`): `eclipse-temurin:21-jre-alpine` → copia JAR → `USER 1001` → `ENTRYPOINT`
  - `EXPOSE` la porta specifica del servizio (8080 gateway, 8081 user, 8082 pec)

### Step 0.3 — docker-compose.yml
Servizi da includere:

| Servizio | Immagine | Porta esposta | Note |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | 5432 | Volume persistente. Env: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`. Init script per creare DB separati (zeta_users, zeta_pec) |
| `keycloak` | `quay.io/keycloak/keycloak:latest` | 8180 | `start-dev` mode. Import del realm `zeta` da `config/keycloak/zeta-realm.json` |
| `kafka` | `bitnami/kafka:latest` | 9092 | **KRaft mode** (senza Zookeeper). Singolo broker per la demo |
| `minio` | `minio/minio` | 9000 (API), 9001 (console) | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`. Comando: `server /data --console-address :9001` |
| `gateway` | build `./gateway` | 8080 | `depends_on`: keycloak |
| `user-service` | build `./user-service` | 8081 | `depends_on`: postgres, keycloak, kafka |
| `pec-service` | build `./pec-service` | 8082 | `depends_on`: postgres, keycloak, kafka, minio |

- Rete: `zeta-network` (bridge)
- Volumi: `postgres-data`, `minio-data`, `kafka-data`
- Variabili d'ambiente per ogni servizio definite direttamente nel docker-compose (no .env file complesso)

> **Non inclusi nella demo**: Redis, ELK stack, Prometheus, Grafana, Jaeger. Sono descritti nel documento delle scelte tecniche come componenti di produzione.

### Step 0.4 — Configurazione Keycloak
- Creare `config/keycloak/zeta-realm.json` con:
  - Realm: `zeta`
  - Client `zeta-portal`: public, redirect URI `http://localhost:8080/*`
  - Client `zeta-service-account`: confidential, service account enabled (per inter-servizio)
  - Ruoli realm: `ADMIN`, `USER`, `OPERATOR`
  - Utente di test: `admin@zeta.local` / `admin123` con ruolo ADMIN
  - Utente di test: `user@zeta.local` / `user123` con ruolo USER

---

## FASE 1 — Microservizio Gestione Utenti (`user-service`)

### Obiettivo
Microservizio CRUD utenti con autenticazione JWT, provisioning dei servizi Aruba e logging strutturato.

### Step 1.1 — Struttura package
```
com.zeta.user/
├── UserServiceApplication.java
├── config/
│   └── SecurityConfig.java
├── user/
│   ├── UserController.java
│   ├── UserService.java
│   ├── UserRepository.java
│   ├── User.java                    # Entity JPA
│   └── dto/
│       ├── CreateUserRequest.java
│       ├── UpdateUserRequest.java
│       └── UserResponse.java
├── provisioning/
│   ├── ProvisioningService.java
│   └── ProvisioningKafkaProducer.java
└── common/
    ├── GlobalExceptionHandler.java
    └── ApiErrorResponse.java
```

### Step 1.2 — Entity `User`
- JPA entity mappata sulla tabella `users` nel DB `zeta_users`:
  - `id` (UUID, `@GeneratedValue`)
  - `email` (String, unique, `@NotBlank`)
  - `firstName`, `lastName` (String)
  - `organization` (String — nome azienda/tenant)
  - `role` (Enum: `ADMIN`, `USER`, `OPERATOR`)
  - `activatedServices` (`@ElementCollection` con Set<String> — es. "PEC", "FIRMA", "CONSERVAZIONE")
  - `semanticIndexingEnabled` (boolean)
  - `createdAt`, `updatedAt` (LocalDateTime, `@CreatedDate`, `@LastModifiedDate`)

### Step 1.3 — DTO e Validazione
- `CreateUserRequest`: `email` (`@Email`, `@NotBlank`), `firstName` (`@NotBlank`, `@Size(max=100)`), `lastName`, `organization`, `role`
- `UpdateUserRequest`: stessi campi ma tutti opzionali (partial update)
- `UserResponse`: tutti i campi leggibili + `activatedServices` + `semanticIndexingEnabled`
- Mapping Entity ↔ DTO con **MapStruct** (`UserMapper` interface con `@Mapper`)

### Step 1.4 — Repository
- `UserRepository extends JpaRepository<User, UUID>`:
  - `Optional<User> findByEmail(String email)`
  - `boolean existsByEmail(String email)`
  - `Page<User> findByOrganization(String organization, Pageable pageable)`

### Step 1.5 — Service Layer
- `UserService` (`@Service`, constructor injection, campi `private final`):
  - `UserResponse createUser(CreateUserRequest request)` — verifica email univoca, salva in DB
  - `UserResponse getUserById(UUID id)` — lancia `UserNotFoundException` se non esiste
  - `Page<UserResponse> getUsers(Pageable pageable)`
  - `UserResponse updateUser(UUID id, UpdateUserRequest request)`
  - `void deleteUser(UUID id)` — hard delete (per semplicità nella demo)
  - `UserResponse activateService(UUID userId, String serviceName)` — aggiunge il servizio al Set, salva, pubblica evento Kafka su topic `user.service.activated`
- `ProvisioningKafkaProducer`: pubblica su Kafka eventi di tipo `{"userId": "...", "service": "PEC", "action": "ACTIVATED"}`

### Step 1.6 — Controller REST
- `UserController` (`@RestController`, `@RequestMapping("/api/v1/users")`):
  - `POST /` → `createUser(@Valid @RequestBody CreateUserRequest)` → `201 Created`
  - `GET /{id}` → `getUserById(UUID)` → `200 OK`
  - `GET /` → `getUsers(Pageable)` → `200 OK` (paginato)
  - `PUT /{id}` → `updateUser(UUID, @Valid @RequestBody UpdateUserRequest)` → `200 OK`
  - `DELETE /{id}` → `deleteUser(UUID)` → `204 No Content`
  - `POST /{id}/services/{serviceName}` → `activateService(UUID, String)` → `200 OK`
- Protezione ruolo: `@PreAuthorize("hasRole('ADMIN')")` su POST e DELETE

### Step 1.7 — Security Config
- `SecurityConfig` (`@Configuration`, `@EnableMethodSecurity`):
  - `SecurityFilterChain` che configura OAuth2 Resource Server con JWT
  - `jwt.issuer-uri` puntato a Keycloak: `http://keycloak:8180/realms/zeta`
  - Converter custom per estrarre i ruoli dal claim `realm_access.roles` di Keycloak e mapparli come `ROLE_ADMIN`, `ROLE_USER`, ecc.
  - Endpoint `/actuator/health` pubblico (no auth)
  - Tutti gli altri endpoint richiedono autenticazione

### Step 1.8 — Global Exception Handler
- `GlobalExceptionHandler` (`@ControllerAdvice`):
  - `UserNotFoundException` → `404`
  - `UserAlreadyExistsException` (email duplicata) → `409 Conflict`
  - `MethodArgumentNotValidException` → `400 Bad Request` con dettaglio dei campi invalidi
  - Eccezione generica → `500`
- Response body standard: `ApiErrorResponse` con `timestamp`, `status`, `error`, `message`, `path`

### Step 1.9 — Logging Strutturato
- Dipendenza: `logstash-logback-encoder`
- File `logback-spring.xml` con appender `LogstashEncoder` che produce JSON con campi: `@timestamp`, `level`, `logger_name`, `message`, `service_name`, `trace_id`
- Ogni log usa SLF4J con messaggi parametrizzati: `log.info("User created: {}", user.getId())`

### Step 1.10 — application.yml
```yaml
# Configurazioni chiave da definire:
server.port: 8081
spring.datasource.url: jdbc:postgresql://postgres:5432/zeta_users
spring.jpa.hibernate.ddl-auto: update   # 'update' per semplicità nella demo
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak:8180/realms/zeta
spring.kafka.bootstrap-servers: kafka:9092
management.endpoints.web.exposure.include: health,info
```

### Step 1.11 — Test

#### Unit Test
- `UserServiceTest` (JUnit 5 + Mockito):
  - Testare `createUser` — verifica che salva in DB e restituisce DTO corretto
  - Testare `createUser` con email duplicata — verifica che lancia eccezione
  - Testare `activateService` — verifica che pubblica evento Kafka
- `UserControllerTest` (`@WebMvcTest`):
  - Testare status code su endpoint validi/invalidi
  - Testare validazione DTO (email mancante → 400)
  - Mock di `UserService`

#### Integration Test
- `UserIntegrationTest` (`@SpringBootTest` + Testcontainers con PostgreSQL):
  - Flusso completo: creazione → lettura → update → cancellazione
  - Verifica persistenza reale su PostgreSQL

---

## FASE 2 — Microservizio PEC (`pec-service`)

### Obiettivo
Microservizio per la gestione delle caselle PEC con integrazione API Aruba (mock/stub), archiviazione documenti su MinIO e comunicazione asincrona via Kafka.

### Step 2.1 — Struttura package
```
com.zeta.pec/
├── PecServiceApplication.java
├── config/
│   ├── SecurityConfig.java
│   └── MinioConfig.java
├── mailbox/
│   ├── MailboxController.java
│   ├── MailboxService.java
│   ├── MailboxRepository.java
│   ├── Mailbox.java
│   └── dto/
│       ├── MailboxResponse.java
│       └── ActivateMailboxRequest.java
├── message/
│   ├── MessageController.java
│   ├── MessageService.java
│   ├── MessageRepository.java
│   ├── PecMessage.java
│   └── dto/
│       ├── SendMessageRequest.java
│       └── MessageResponse.java
├── integration/
│   ├── ArubaPecClient.java          # Interface
│   └── ArubaPecStub.java            # Mock implementation (sempre attivo)
├── storage/
│   └── DocumentStorageService.java   # Upload/download su MinIO
├── event/
│   ├── PecEventProducer.java
│   └── PecEventConsumer.java
└── common/
    ├── GlobalExceptionHandler.java
    └── ApiErrorResponse.java
```

### Step 2.2 — Entity `Mailbox`
- `id` (UUID), `userId` (UUID — relazione logica verso user-service), `pecAddress` (String, unique), `status` (Enum: ACTIVE, SUSPENDED, CLOSED), `createdAt`, `updatedAt`

### Step 2.3 — Entity `PecMessage`
- `id` (UUID), `mailboxId` (UUID, FK verso Mailbox), `senderAddress` (String), `recipientAddress` (String), `subject` (String), `body` (Text/Lob), `direction` (Enum: INBOUND, OUTBOUND), `status` (Enum: SENT, DELIVERED, FAILED, RECEIVED), `documentKey` (String, nullable — object key su MinIO), `receivedAt` (LocalDateTime), `createdAt`

### Step 2.4 — Aruba PEC API Client (Stub)
- **Interface** `ArubaPecClient`:
  - `List<ArubaPecMessageDto> fetchMessages(String mailboxAddress, int page, int size)`
  - `ArubaPecMessageDto sendMessage(ArubaPecSendRequest request)`
  - `ArubaPecMailboxStatus getMailboxStatus(String mailboxAddress)`
- **Stub** `ArubaPecStub` (`@Service` — sempre attivo per la demo):
  - Restituisce dati fake realistici (indirizzi PEC verosimili, oggetti tipo "Fattura n. 2024/001")
  - Simula latenza minima (`Thread.sleep(50)`)
  - Per `sendMessage`: restituisce status `SENT` con messageId generato
- L'interfaccia è progettata in modo che in produzione si sostituisca lo stub con un'implementazione reale usando `RestClient` + OAuth2 Client Credentials + Resilience4j `@CircuitBreaker`. Nella demo si usa solo lo stub.

### Step 2.5 — DocumentStorageService (MinIO)
- Usa il **MinIO Java SDK** (`io.minio:minio`):
  - `String uploadDocument(String bucket, String objectKey, InputStream data, String contentType)` — carica un file su MinIO
  - `InputStream downloadDocument(String bucket, String objectKey)` — scarica un file
  - `void deleteDocument(String bucket, String objectKey)`
- Bucket di default: `pec-documents`
- Alla partenza del servizio, verifica che il bucket esista o lo crea (`makeBucket`)

### Step 2.6 — Service Layer
- `MailboxService`:
  - `MailboxResponse activateMailbox(UUID userId, ActivateMailboxRequest request)` — crea la mailbox in DB
  - `MailboxResponse getMailbox(UUID mailboxId)`
  - `List<MailboxResponse> getUserMailboxes(UUID userId)`
- `MessageService`:
  - `MessageResponse sendMessage(UUID mailboxId, SendMessageRequest request)` — chiama `ArubaPecClient.sendMessage()`, salva in DB, pubblica evento Kafka `pec.message.sent`
  - `Page<MessageResponse> getMessages(UUID mailboxId, Pageable pageable)`
  - `MessageResponse getMessage(UUID messageId)`
  - `void syncMessages(UUID mailboxId)` — chiama `ArubaPecClient.fetchMessages()`, salva nuovi messaggi in DB, per ogni messaggio con allegato → carica su MinIO
- `PecEventConsumer` (Kafka `@KafkaListener`):
  - Ascolta topic `user.service.activated`
  - Quando il servizio attivato è "PEC", crea automaticamente una mailbox per l'utente

### Step 2.7 — Controller REST
- `MailboxController` (`@RequestMapping("/api/v1/mailboxes")`):
  - `POST /` → activateMailbox → `201`
  - `GET /{id}` → getMailbox → `200`
  - `GET /user/{userId}` → getUserMailboxes → `200`
- `MessageController` (`@RequestMapping("/api/v1/mailboxes/{mailboxId}/messages")`):
  - `POST /` → sendMessage → `201`
  - `GET /` → getMessages (paginato) → `200`
  - `GET /{messageId}` → getMessage → `200`
  - `POST /sync` → syncMessages → `200` (trigger manuale sincronizzazione da API Aruba)

### Step 2.8 — Security, Logging, Config
- `SecurityConfig`: identica a user-service (OAuth2 Resource Server + JWT Keycloak)
- `logback-spring.xml`: identico, cambia solo `service_name: pec-service`
- `application.yml`:
  ```yaml
  server.port: 8082
  spring.datasource.url: jdbc:postgresql://postgres:5432/zeta_pec
  spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak:8180/realms/zeta
  spring.kafka.bootstrap-servers: kafka:9092
  minio.endpoint: http://minio:9000
  minio.access-key: ${MINIO_ROOT_USER}
  minio.secret-key: ${MINIO_ROOT_PASSWORD}
  ```

### Step 2.9 — Test
- **Unit Test**: `MessageServiceTest` — mock di `ArubaPecClient`, `MessageRepository`, `PecEventProducer`. Verifica che `sendMessage()` chiami il client, salvi in DB, pubblichi evento.
- **Unit Test**: `MailboxServiceTest` — verifica creazione e lookup mailbox.
- **Integration Test**: `PecIntegrationTest` (`@SpringBootTest` + Testcontainers con PostgreSQL):
  - Flusso: crea mailbox → invia messaggio → verifica persistenza.

---

## FASE 3 — API Gateway (`gateway`)

### Obiettivo
Configurare Spring Cloud Gateway come entry point. Configurazione semplice di routing + JWT relay.

### Step 3.1 — Struttura (minimale)
```
com.zeta.gateway/
├── GatewayApplication.java
├── config/
│   └── SecurityConfig.java
└── filter/
    └── LoggingFilter.java
```

### Step 3.2 — Routing (in application.yml)
Configurare le route declarativamente in `application.yml`:
- `/api/v1/users/**` → `http://user-service:8081`
- `/api/v1/mailboxes/**` → `http://pec-service:8082`

Nessuna logica Java custom per il routing — tutto in YAML.

### Step 3.3 — Security
- Configurare il Gateway come **OAuth2 Resource Server** (o Token Relay se si usa OAuth2 Login).
- Nella versione semplificata: il Gateway si limita a **propagare** il header `Authorization: Bearer <JWT>` verso i microservizi downstream. Non fa validazione aggiuntiva — la validazione avviene in ogni microservizio.
- CORS: abilitare `http://localhost:*` per test da Postman/browser.

### Step 3.4 — Logging Filter
- Un `GlobalFilter` che logga: metodo HTTP, path, status code, durata ms.
- Log in formato JSON (come gli altri servizi).

### Step 3.5 — application.yml
```yaml
server.port: 8080
spring.cloud.gateway.routes:
  - id: user-service
    uri: http://user-service:8081
    predicates:
      - Path=/api/v1/users/**
  - id: pec-service
    uri: http://pec-service:8082
    predicates:
      - Path=/api/v1/mailboxes/**
```

---

## FASE 4 — AI Engine (Leggero, Dimostrativo)

### Obiettivo
Implementare un servizio Python leggero che dimostra la pipeline RAG: indicizzare documenti e rispondere a domande via chat. Il focus del colloquio è architetturale — l'implementazione deve funzionare ma non essere production-grade.

### Step 4.1 — Struttura
```
ai-engine/
├── Dockerfile
├── requirements.txt
├── src/
│   ├── main.py                      # FastAPI app
│   ├── config.py                    # Settings
│   ├── api/
│   │   ├── routes.py
│   │   └── schemas.py
│   ├── indexing/
│   │   ├── document_processor.py    # Estrazione testo + chunking
│   │   ├── embedding_service.py     # Generazione embeddings
│   │   └── vector_store.py          # ChromaDB
│   └── chat/
│       ├── rag_service.py           # Orchestrazione RAG
│       └── llm_client.py            # Client verso Ollama
└── tests/
    └── test_document_processor.py
```

### Step 4.2 — Dipendenze (`requirements.txt`)
```
fastapi
uvicorn
chromadb
langchain
langchain-community
sentence-transformers
httpx
pydantic-settings
pypdf2
python-docx
```
> **Nota**: non servono Tika, Milvus, vLLM. Il tutto è leggero.

### Step 4.3 — Pipeline di Indicizzazione
- `document_processor.py`:
  - Accetta un file (PDF o DOCX).
  - Estrae il testo con **PyPDF2** (PDF) o **python-docx** (DOCX).
  - Divide il testo in chunk da ~500 caratteri con overlap di ~100 caratteri (chunking semplice con split su paragrafi + sliding window).
  - Restituisce lista di chunk con metadata (`document_id`, `chunk_index`).
- `embedding_service.py`:
  - Carica localmente `all-MiniLM-L6-v2` (modello leggero, ~80 MB, gira su CPU).
  - Metodo `embed(texts: list[str]) → list[list[float]]`.
- `vector_store.py`:
  - Usa **ChromaDB** (embedded, singolo container).
  - Collection per utente: `user_{userId}`.
  - Metodi: `add_chunks(user_id, chunks, embeddings, metadatas)`, `search(user_id, query_embedding, top_k)`, `delete_document(user_id, document_id)`.

### Step 4.4 — Servizio Chat RAG
- `rag_service.py`:
  1. Riceve `(user_id, query)`.
  2. Genera l'embedding della query via `embedding_service`.
  3. Cerca i top-5 chunk più simili in ChromaDB, filtrati per `user_id`.
  4. Costruisce il prompt: `"Rispondi basandoti solo sui seguenti documenti:\n{chunks}\n\nDomanda: {query}"`.
  5. Invia ad **Ollama** via `llm_client`.
  6. Restituisce la risposta.
- `llm_client.py`:
  - Chiama `http://ollama:11434/api/chat` con modello `phi3:mini`.
  - Gestisce timeout e errori base.

### Step 4.5 — API REST (FastAPI)
- `POST /api/v1/ai/index` — body: `{document_id, user_id, file}` (multipart). Indicizza il documento.
- `POST /api/v1/ai/chat` — body: `{user_id, query}`. Restituisce risposta RAG.
- `DELETE /api/v1/ai/index/{document_id}?user_id=...` — elimina embedding del documento.
- `GET /api/v1/ai/health` — health check.

### Step 4.6 — Dockerfile
- Base image: `python:3.11-slim`
- `pip install -r requirements.txt`
- `CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]`

### Step 4.7 — docker-compose additions
Aggiungere al docker-compose:
- **chromadb**: immagine `chromadb/chroma:latest`, porta 8100
- **ollama**: immagine `ollama/ollama`, porta 11434. Nota: il modello va scaricato la prima volta con `ollama pull phi3:mini` (documentare nel README)
- **ai-engine**: build da `./ai-engine`, porta 8000, dipende da chromadb e ollama

> **Nota**: Ollama su CPU è lento (~10-30 sec per risposta). È sufficiente per una demo. Nel README si documenta che in produzione si userebbe vLLM su GPU.

### Step 4.8 — Test
- `test_document_processor.py`: verifica che il chunking produca chunk della dimensione attesa e mantenga i metadata.

---

## FASE 5 — Documentazione Architetturale

### Obiettivo
Creare `docs/architecture.md` con diagrammi Mermaid per il colloquio tecnico.

### Step 5.1 — Diagramma Architettura Complessiva
- Flowchart Mermaid che mostra:
  - Browser → Gateway → [User Service, PEC Service, AI Engine]
  - User Service → PostgreSQL, Kafka
  - PEC Service → PostgreSQL, Kafka, MinIO, Aruba PEC API (mock)
  - AI Engine → ChromaDB, Ollama
  - Kafka come canale tra i servizi

### Step 5.2 — Diagramma Sequenza — Autenticazione
- Sequence diagram:
  1. User → Gateway (richiesta API)
  2. Gateway → Keycloak (login OAuth2, ottenimento JWT)
  3. Gateway → Microservizio (forwarding JWT in header)
  4. Microservizio → Keycloak JWKS endpoint (validazione JWT)

### Step 5.3 — Diagramma Sequenza — Invio PEC
- Sequence diagram:
  1. User → Gateway → PEC Service (`POST /messages`)
  2. PEC Service → Aruba PEC API Stub (invia messaggio)
  3. PEC Service → MinIO (upload allegati)
  4. PEC Service → PostgreSQL (salva messaggio)
  5. PEC Service → Kafka (pubblica `pec.message.sent`)

### Step 5.4 — Diagramma Sequenza — Chat RAG
- Sequence diagram:
  1. User → Gateway → AI Engine (`POST /chat`)
  2. AI Engine → Embedding Model (genera embedding query)
  3. AI Engine → ChromaDB (ricerca top-k, filtro per userId)
  4. AI Engine → Ollama/LLM (prompt con contesto)
  5. AI Engine → User (risposta generata)

### Step 5.5 — ERD Database
- ER Diagram Mermaid:
  - `users` (id, email, firstName, lastName, organization, role, activatedServices, semanticIndexingEnabled)
  - `mailboxes` (id, userId, pecAddress, status)
  - `pec_messages` (id, mailboxId, sender, recipient, subject, body, direction, status, documentKey)

### Step 5.6 — Esempio Manifest K8s (solo documentazione)
- Inserire nella doc un **singolo esempio** di Deployment + Service K8s per `user-service`, per dimostrare la predisposizione:
  - Deployment con 2 repliche, liveness/readiness probe su `/actuator/health`, resource limits
  - Service ClusterIP
- Nota: "In produzione, ogni microservizio avrebbe un manifest analogo, gestito con Helm chart."

---

## FASE 6 — Test End-to-End e README

### Step 6.1 — Verifica docker-compose
- Eseguire `docker-compose up --build` e verificare che:
  - Tutti i container partano senza errori
  - `curl http://localhost:8080/api/v1/users` risponda (401 senza token = corretto)
  - Keycloak sia raggiungibile su `http://localhost:8180`

### Step 6.2 — Scenario di test manuale (da documentare nel README)
1. Ottenere un JWT da Keycloak (password grant o via browser su localhost:8180)
2. `POST /api/v1/users` — creare un utente
3. `POST /api/v1/users/{id}/services/PEC` — attivare servizio PEC
4. Verificare che la mailbox PEC sia stata creata automaticamente via Kafka consumer
5. `POST /api/v1/mailboxes/{id}/messages` — inviare un messaggio PEC
6. `GET /api/v1/mailboxes/{id}/messages` — leggere i messaggi
7. (Opzionale) `POST /api/v1/ai/index` — indicizzare un documento
8. (Opzionale) `POST /api/v1/ai/chat` — fare una domanda sul documento

### Step 6.3 — README.md
- **Descrizione**: cos'è la Piattaforma Zeta, architettura, stack tecnologico
- **Prerequisiti**: Docker, docker-compose, Java 21 (solo per sviluppo IDE), 8+ GB RAM disponibili
- **Quick start**: `docker-compose up --build`
- **Endpoint API** con esempi curl
- **Struttura dei microservizi** (tabella riassuntiva)
- **Link** ai documenti: `piattaforma-zeta-scelte-tecniche-v2.md`, `docs/architecture.md`
- **Note**: cosa è semplificato nella demo vs. produzione

---

## Riepilogo File per la Sessione di Implementazione

| File / Directory | Scopo |
|---|---|
| `pom.xml` (root) | Parent POM Maven multi-modulo |
| `docker-compose.yml` | Orchestrazione locale completa |
| `config/keycloak/zeta-realm.json` | Configurazione realm Keycloak |
| `user-service/.../UserService.java` | Business logic utenti |
| `user-service/.../SecurityConfig.java` | OAuth2 JWT validation |
| `user-service/.../ProvisioningKafkaProducer.java` | Pubblicazione eventi Kafka |
| `pec-service/.../ArubaPecClient.java` | Interface API Aruba PEC |
| `pec-service/.../ArubaPecStub.java` | Mock dell'API Aruba |
| `pec-service/.../MessageService.java` | Business logic messaggi PEC |
| `pec-service/.../DocumentStorageService.java` | Upload/download MinIO |
| `pec-service/.../PecEventConsumer.java` | Consumer Kafka per provisioning auto |
| `gateway/.../application.yml` | Route Spring Cloud Gateway |
| `ai-engine/src/indexing/document_processor.py` | Chunking documenti |
| `ai-engine/src/chat/rag_service.py` | Orchestratore RAG con ChromaDB + Ollama |
| `docs/architecture.md` | Documentazione e diagrammi Mermaid |
| `README.md` | Guida all'avvio e al testing |

---

## Ordine di Implementazione

```
FASE 0 (Setup) → FASE 1 (User Service) → FASE 2 (PEC Service) → FASE 3 (Gateway) → FASE 4 (AI Engine) → FASE 5 (Docs) → FASE 6 (Test + README)
```

> **Parallelismo**: La Fase 4 (Python) può essere sviluppata in parallelo alle Fasi 1-3 (Java).
> La Fase 5 (Docs) può essere scritta in qualsiasi momento.
