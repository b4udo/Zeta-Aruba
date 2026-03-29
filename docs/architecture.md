# Piattaforma Zeta — Architettura

## Obiettivo

La piattaforma dimostra un'architettura a microservizi on-premise orientata a servizi Aruba, autenticazione centralizzata e pipeline AI locale basata su RAG. In locale tutto gira con `docker-compose`, mentre i componenti restano predisposti per un'evoluzione verso Kubernetes.

## Architettura complessiva

```mermaid
flowchart LR
    Browser[Browser / Client API] --> Gateway[Spring Cloud Gateway]
    Gateway --> UserService[user-service]
    Gateway --> PecService[pec-service]
    Gateway --> AiEngine[ai-engine]

    Keycloak[Keycloak] --> Gateway
    Keycloak --> UserService
    Keycloak --> PecService

    UserService --> UsersDb[(PostgreSQL zeta_users)]
    UserService --> Kafka[(Kafka)]

    PecService --> PecDb[(PostgreSQL zeta_pec)]
    PecService --> Kafka
    PecService --> MinIO[(MinIO)]
    PecService --> ArubaStub[Aruba PEC Stub]

    AiEngine --> Chroma[(ChromaDB)]
    AiEngine --> Ollama[(Ollama / phi3:mini)]
```

## Sequenza — autenticazione

```mermaid
sequenceDiagram
    actor User
    participant Gateway
    participant Keycloak
    participant UserService

    User->>Keycloak: Login / token request
    Keycloak-->>User: JWT access token
    User->>Gateway: API request + Authorization Bearer
    Gateway->>UserService: Forward request + same JWT
    UserService->>Keycloak: Resolve issuer/JWKS metadata
    Keycloak-->>UserService: Public keys
    UserService-->>Gateway: Authorized response
    Gateway-->>User: API response
```

## Sequenza — invio PEC

```mermaid
sequenceDiagram
    actor User
    participant Gateway
    participant PecService
    participant ArubaStub as Aruba PEC Stub
    participant MinIO
    participant Postgres as PostgreSQL
    participant Kafka

    User->>Gateway: POST /api/v1/mailboxes/{id}/messages
    Gateway->>PecService: Forward request
    PecService->>ArubaStub: sendMessage(...)
    ArubaStub-->>PecService: SENT + messageId
    PecService->>MinIO: upload allegati/documenti
    PecService->>Postgres: salva messaggio PEC
    PecService->>Kafka: publish pec.message.sent
    PecService-->>Gateway: MessageResponse
    Gateway-->>User: 201 Created
```

## Sequenza — chat RAG

```mermaid
sequenceDiagram
    actor User
    participant Gateway
    participant AiEngine
    participant Embedder as Embedding Service
    participant Chroma as ChromaDB
    participant Ollama

    User->>Gateway: POST /api/v1/ai/chat
    Gateway->>AiEngine: Forward request
    AiEngine->>Embedder: embed(query)
    Embedder-->>AiEngine: query vector
    AiEngine->>Chroma: top-k similarity search by user_id
    Chroma-->>AiEngine: relevant chunks
    AiEngine->>Ollama: prompt with retrieved context
    Ollama-->>AiEngine: grounded answer
    AiEngine-->>Gateway: ChatResponse
    Gateway-->>User: final answer
```

## ERD

```mermaid
erDiagram
    USERS {
        uuid id PK
        string email UK
        string first_name
        string last_name
        string organization
        string role
        boolean semantic_indexing_enabled
        datetime created_at
        datetime updated_at
    }

    USER_ACTIVATED_SERVICES {
        uuid user_id FK
        string service_name
    }

    MAILBOXES {
        uuid id PK
        uuid user_id
        string pec_address UK
        string status
        datetime created_at
        datetime updated_at
    }

    PEC_MESSAGES {
        uuid id PK
        uuid mailbox_id FK
        string sender_address
        string recipient_address
        string subject
        text body
        string direction
        string status
        string document_key
        datetime received_at
        datetime created_at
    }

    USERS ||--o{ USER_ACTIVATED_SERVICES : activates
    USERS ||--o{ MAILBOXES : owns
    MAILBOXES ||--o{ PEC_MESSAGES : contains
```

## Deployment Kubernetes di esempio

> Solo a scopo documentale: in locale il progetto usa `docker-compose`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: user-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: user-service
  template:
    metadata:
      labels:
        app: user-service
    spec:
      containers:
        - name: user-service
          image: ghcr.io/example/piattaforma-zeta/user-service:latest
          ports:
            - containerPort: 8081
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: kubernetes
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 20
            periodSeconds: 20
          resources:
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1024Mi"
---
apiVersion: v1
kind: Service
metadata:
  name: user-service
spec:
  selector:
    app: user-service
  ports:
    - name: http
      port: 80
      targetPort: 8081
  type: ClusterIP
```

## Note architetturali

- **Autenticazione**: Keycloak emette JWT validati dai microservizi Spring Security.
- **Messaging**: Kafka gestisce eventi asincroni come `user.service.activated` e `pec.message.sent`.
- **Storage documentale**: MinIO funge da object storage S3-compatible per contenuti PEC.
- **AI locale**: il motore RAG usa embeddings locali, ChromaDB per il retrieval e Ollama per l'inferenza LLM.
- **Produzione vs demo**: la demo privilegia semplicità di esecuzione; la documentazione resta compatibile con un deployment Kubernetes/Helm più evoluto.
