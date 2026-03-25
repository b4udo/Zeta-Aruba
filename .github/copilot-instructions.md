# Zeta-Aruba — Copilot Workspace Instructions

## Project Overview

**Zeta-Aruba** is an Italian-market microservices platform for PEC (Posta Elettronica Certificata — certified email) management with an integrated AI engine. It consists of Java/Spring Boot microservices, a Python FastAPI AI engine, and infrastructure containers orchestrated via Docker Compose.

→ See [README.md](../README.md) and [docs/architecture.md](../docs/architecture.md) for full context.

---

## Architecture at a Glance

| Service | Port | Language | Role |
|---|---|---|---|
| `gateway` | 8080 | Java / Spring Cloud Gateway | API entry point, routing, JWT passthrough |
| `user-service` | 8081 | Java / Spring Boot 3.4 | User lifecycle, roles, Kafka event producer |
| `pec-service` | 8082 | Java / Spring Boot 3.4 | PEC mailboxes, documents (MinIO), Kafka consumer |
| `ai-engine` | 8000 | Python / FastAPI | Document indexing (ChromaDB), RAG chat (Ollama) |
| `keycloak` | 8180 | Keycloak 26.0 | OAuth2/OIDC IAM, realm: `zeta` |
| `postgres` | 5432 | PostgreSQL 16 | DBs: `zeta_users`, `zeta_pec` (init via `config/postgres/init-databases.sh`) |
| `kafka` | 9092 | Apache Kafka 3.9 KRaft | Async events (`user.service.activated`) |
| `minio` | 9000/9001 | MinIO | S3-compatible document storage |
| `chromadb` | 8100 | ChromaDB | Vector DB for semantic search |
| `ollama` | 11434 | Ollama | Local LLM host (Mistral 7B) |

**Communication:**
- **Sync (REST):** Client → Gateway → Services
- **Async (Kafka):** `user-service` publishes `user.service.activated` → `pec-service` provisions mailbox

---

## Build & Test Commands

```bash
# Build ALL services (from workspace root)
mvn clean install

# Build a single service (skip tests)
mvn -f user-service/pom.xml clean package -DskipTests
mvn -f pec-service/pom.xml clean package -DskipTests
mvn -f gateway/pom.xml clean package -DskipTests

# Run unit tests
mvn test

# Run integration tests (Testcontainers required)
mvn verify -Pit

# Build & start all Docker containers
docker compose up --build -d

# Force clean Docker rebuild
docker compose up --build --no-cache -d

# Pull Ollama LLM model (REQUIRED for ai-engine — 4 GB, one-time)
docker exec -it zeta-ollama ollama pull mistral:7b
```

> **Java version:** 21 | **Spring Boot:** 3.4.1 | **Spring Cloud:** 2024.0.0

---

## Java Service Conventions

### Package Structure
Bounded-context layout per service:
```
com.zeta.{service}.{domain}.{layer}
# Examples:
com.zeta.user.user.controller
com.zeta.pec.mailbox.service
```

### Mandatory Patterns
- **Constructor injection only** — all fields `private final`, use `@RequiredArgsConstructor` (Lombok)
- **DTOs with MapStruct** — `@Mapper(componentModel = "spring")` interfaces; no manual mapping code
- **Validation on DTOs** — Jakarta Validation (`@NotBlank`, `@Email`, `@Size`) + `@Valid` on controller params
- **JPA entities** — UUID PKs, `@CreatedDate`/`@LastModifiedDate` via `@EntityListeners(AuditingEntityListener.class)`
- **Exception handling** — `@ControllerAdvice` + `@ExceptionHandler`; custom exceptions (e.g. `UserNotFoundException`)
- **Structured logging** — `@Slf4j` (Lombok), Logstash JSON encoder; never use `System.out.println`
- **No setter injection**, no field injection (`@Autowired` on fields)

### Lombok Annotations in Use
`@Data`, `@Builder`, `@Value`, `@RequiredArgsConstructor`, `@Slf4j`

### Kafka
- **Producer:** `KafkaTemplate<String, Object>` with JSON serializer in `user-service`
- **Consumer:** `@KafkaListener(topics = "user.service.activated", groupId = "pec-service")` in `pec-service`
- `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` is set — topics are created automatically

---

## Python (ai-engine) Conventions

### Module Structure
```
ai-engine/src/
  config.py            # Pydantic settings (env-driven)
  main.py              # FastAPI app + lifespan
  api/routes.py        # REST endpoints
  api/schemas.py       # Pydantic request/response models
  chat/llm_client.py   # Ollama async HTTP client (retry w/ backoff)
  chat/rag_service.py  # RAG orchestration
  indexing/            # PDF/DOCX parsing, embedding, ChromaDB storage
```

### Key Settings (configurable via env)
| Variable | Default | Notes |
|---|---|---|
| `OLLAMA_MODEL` | `mistral:7b` | LLM model name |
| `AI_TOP_K` | `5` | RAG retrieval results |
| `AI_REQUEST_TIMEOUT_SECONDS` | `60` | Ollama call timeout |
| `CHROMA_HOST` / `CHROMA_PORT` | `chromadb` / `8000` | ChromaDB connection |

### Patterns
- Always use `async def` for endpoint handlers and Ollama calls
- ChromaDB client falls back to in-memory store if unreachable — log a warning, don't crash
- Ollama client: 3-attempt exponential backoff on 429/5xx and connection errors
- Embeddings: `all-MiniLM-L6-v2` via `sentence-transformers`; chunk size 500 chars, 100 overlap

---

## Infrastructure Pitfalls & Notes

### Keycloak
- Requires `KC_HEALTH_ENABLED: "true"` for the `/health/ready` endpoint used in the Docker healthcheck
- Realm `zeta` is imported at startup from `config/keycloak/zeta-realm.json`
- Other services connect via internal hostname: `http://keycloak:8180/realms/zeta`
- Admin credentials (dev only): `admin` / `admin`

### PostgreSQL
- Init script `config/postgres/init-databases.sh` creates both `zeta_users` and `zeta_pec` databases on first run
- Spring Boot services use `SPRING_PROFILES_ACTIVE: docker` which activates the Docker datasource URL

### Ollama / AI Engine
- The `mistral:7b` model (~4 GB) must be pulled manually after first start: `docker exec -it zeta-ollama ollama pull mistral:7b`
- ai-engine starts before Ollama is ready to serve the model — it uses retry/backoff, but first chat requests may time out

### Multi-stage Docker Builds
All Java services use a two-stage Dockerfile:
1. `maven:3.9-eclipse-temurin-21` — build stage (copies root pom + service pom + src)
2. `eclipse-temurin:21-jre-alpine` — runtime stage, non-root user `appuser`

---

## Testing Strategy

| Layer | Tool |
|---|---|
| Unit | JUnit 5 + Mockito |
| Security | `spring-security-test` (`@WithMockUser`) |
| Integration | Testcontainers (PostgreSQL, Kafka) |
| Python | pytest in `ai-engine/tests/` |

Run Java integration tests with profile `-Pit`. Tests expect Docker to be available for Testcontainers.

---

## Key Files Reference

| File | Purpose |
|---|---|
| [docker-compose.yml](../docker-compose.yml) | Full infrastructure definition |
| [config/postgres/init-databases.sh](../config/postgres/init-databases.sh) | DB init script |
| [config/keycloak/zeta-realm.json](../config/keycloak/zeta-realm.json) | Keycloak realm configuration |
| [ai-engine/src/config.py](../ai-engine/src/config.py) | AI engine settings |
| [pom.xml](../pom.xml) | Root Maven BOM (versions for all modules) |
| [docs/architecture.md](../docs/architecture.md) | Detailed architecture doc |
| [AI_ENGINE_GUIDE.md](../AI_ENGINE_GUIDE.md) | AI engine developer guide |
| [DEMO_RUNBOOK.md](../DEMO_RUNBOOK.md) | Demo environment runbook |
