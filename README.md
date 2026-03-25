# Piattaforma Zeta

Piattaforma dimostrativa a microservizi per la gestione utenti, provisioning PEC e retrieval-augmented generation locale. L'implementazione è pensata per girare interamente in locale con Docker Compose, mantenendo però una struttura pronta per essere discussa ed evoluta in ottica Kubernetes/on-premise.

## Panoramica del progetto

La soluzione è composta da:

- `gateway`: entrypoint unico con Spring Cloud Gateway, routing, CORS e logging delle richieste.
- `user-service`: CRUD utenti, ruoli, attivazione servizi e pubblicazione eventi Kafka.
- `pec-service`: gestione mailbox PEC, invio/sync messaggi, stub Aruba e storage documenti su MinIO.
- `ai-engine`: servizio FastAPI per indicizzazione documenti e chat RAG locale con ChromaDB + Ollama.
- `postgres`, `kafka`, `keycloak`, `minio`: componenti infrastrutturali per persistenza, messaging, IAM e object storage.

Le scelte architetturali e i compromessi della demo sono spiegati in:

- `../piattaforma-zeta-scelte-tecniche.md`
- `docs/architecture.md`
- `docs/DEMO_RUNBOOK_CLIENT.md` — Guida semplificata per demo cliente
- `docs/DEMO_RUNBOOK.md` — Runbook tecnico completo con debug
- `docs/AI_ENGINE_GUIDE.md`

## Stack tecnologico

| Area | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security, Spring Data JPA |
| Gateway | Spring Cloud Gateway |
| Messaging | Apache Kafka |
| Identity | Keycloak |
| Database | PostgreSQL 16 |
| Object Storage | MinIO |
| AI/RAG | FastAPI, ChromaDB, sentence-transformers, Ollama |
| Orchestrazione locale | Docker Compose |

## Prerequisiti

- Docker Desktop o Docker Engine + Compose
- Almeno 8 GB RAM liberi consigliati
- Java 21 e Maven solo se vuoi lanciare i moduli Java fuori da Docker
- Python 3.11 solo se vuoi lanciare `ai-engine` localmente senza container

## Quick start

1. Avvia l'intera piattaforma:

```bash
docker-compose up --build
```

2. In un secondo terminale, scarica il modello Ollama richiesto dalla demo:

```bash
docker exec -it zeta-ollama ollama pull mistral:7b
```

3. Verifica i servizi principali:

- Gateway: `http://localhost:8080/actuator/health`
- Keycloak: `http://localhost:8180`
- MinIO Console: `http://localhost:9001`
- AI Engine: `http://localhost:8000/api/v1/ai/health`

## Credenziali demo

### Keycloak admin

- username: `admin`
- password: `admin`

### Utenti realm importati

- `admin@zeta.local` / `admin123`
- `user@zeta.local` / `user123`

### MinIO

- access key: `minioadmin`
- secret key: `minioadmin123`

## Come provarlo

### 1. Ottieni un JWT da Keycloak

Puoi usare l'interfaccia web di Keycloak oppure il password grant per la demo. Esempio payload:

- realm: `zeta`
- client: `zeta-portal`
- username: `admin@zeta.local`
- password: `admin123`

### 2. Crea un utente

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "mario.rossi@zeta.local",
    "firstName": "Mario",
    "lastName": "Rossi",
    "organization": "Zeta",
    "role": "USER"
  }'
```

### 3. Attiva il servizio PEC per l'utente

```bash
curl -X POST http://localhost:8080/api/v1/users/<USER_ID>/services/PEC \
  -H "Authorization: Bearer <JWT>"
```

Il `pec-service` ascolta l'evento Kafka `user.service.activated` e crea automaticamente una mailbox demo per quell'utente.

### 4. Recupera le mailbox dell'utente

```bash
curl http://localhost:8080/api/v1/mailboxes/user/<USER_ID> \
  -H "Authorization: Bearer <JWT>"
```

### 5. Invia un messaggio PEC

```bash
curl -X POST http://localhost:8080/api/v1/mailboxes/<MAILBOX_ID>/messages \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "recipientAddress": "destinatario@pec.local",
    "subject": "Test PEC",
    "body": "Messaggio di prova dalla demo Zeta"
  }'
```

### 6. Leggi i messaggi PEC

```bash
curl http://localhost:8080/api/v1/mailboxes/<MAILBOX_ID>/messages \
  -H "Authorization: Bearer <JWT>"
```

### 7. Indicizza un documento nel motore AI

```bash
curl -X POST http://localhost:8080/api/v1/ai/index \
  -F "document_id=doc-001" \
  -F "user_id=<USER_ID>" \
  -F "file=@./sample.docx"
```

### 8. Interroga la chat RAG

```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "<USER_ID>",
    "query": "Riassumi il contenuto del documento indicizzato"
  }'
```

## Struttura del repository

| Path | Scopo |
|---|---|
| `pom.xml` | Parent Maven multi-modulo |
| `gateway/` | API gateway e routing |
| `user-service/` | Gestione utenti e provisioning eventi |
| `pec-service/` | Mailbox PEC, messaggi e storage documenti |
| `ai-engine/` | Indicizzazione documenti e chat RAG |
| `config/keycloak/` | Realm Keycloak importabile |
| `config/postgres/` | Script di init PostgreSQL |
| `docs/architecture.md` | Diagrammi e spiegazione architetturale |

## Note importanti

- La demo è **semplificata** rispetto allo scenario di produzione descritto in `piattaforma-zeta-scelte-tecniche.md`.
- Il gateway inoltra le richieste ai servizi downstream; la validazione dei JWT avviene nei microservizi Java.
- Se Ollama non è ancora pronto o il modello non è stato scaricato, l'`ai-engine` restituisce comunque un fallback contestuale invece di fallire brutalmente.
- Il modulo AI supporta file `PDF` e `DOCX`.

## Sviluppo locale senza Docker (opzionale)

### Java

Esegui i moduli Spring Boot dal root del progetto con Maven, dopo aver avviato PostgreSQL, Kafka, Keycloak e MinIO.

### Python

Dentro `ai-engine/`:

```bash
pip install -r requirements.txt
uvicorn src.main:app --reload --host 0.0.0.0 --port 8000
```

## Cosa mostra bene in colloquio

- decomposizione per bounded context (`user`, `pec`, `ai`)
- uso combinato di REST sincrono ed eventi Kafka
- security centralizzata con Keycloak + JWT
- separazione chiara tra demo locale e architettura target di produzione
- estendibilità della pipeline AI verso deployment GPU / vector DB distribuiti
