# Piattaforma Zeta — Runbook Demo

Questo file contiene i **comandi esatti** per preparare e lanciare la demo, insieme a una spiegazione essenziale dei servizi infrastrutturali e delle API da mostrare al cliente.

## Obiettivo della demo

Durante la demo puoi far vedere questi tre flussi principali:

1. **Gestione utenti** tramite `user-service`
2. **Attivazione e invio PEC** tramite `pec-service`
3. **Indicizzazione documenti e chat intelligente** tramite `ai-engine`

Tutto passa dal `gateway`, che espone l'entrypoint unico su `http://localhost:8080`.

## Prerequisiti

- Docker Desktop avviato
- PowerShell
- Almeno 8 GB di RAM disponibili
- Connessione internet per scaricare il modello Ollama (4 GB)

## Avvio completo della demo

### 1. Posizionati nella cartella del progetto

```powershell
Set-Location "c:\Users\dodob\OneDrive\Desktop\Zeta-Aruba"
```

### 2. Avvia tutti i container

```powershell
# Opzione A: Avvio detached (consigliato per demo)
docker compose up -d

# Opzione B: Avvio con build da zero (se hai modificato il codice)
docker compose up --build -d

# Opzione C: Avvio in foreground (utile per debug, mostra log in tempo reale)
docker compose up --build
```

### 3. Verifica lo stato dei container

```powershell
docker compose ps
```

Aspetta che tutti i container siano `Up` o `healthy` prima di procedere. In particolare:
- `postgres`, `kafka`, `keycloak`, `minio` devono essere `(healthy)`
- `user-service`, `pec-service`, `gateway` dipendono dall'infrastruttura, aspetta che siano `Up`

### 4. Download del modello Ollama richiesto dalla demo (una sola volta)

In un **secondo terminale** (il primo è ancora occupato da Docker se usi foreground, oppure libero se usi `-d`):

```powershell
docker exec -it zeta-ollama ollama pull mistral:7b
```

Questo comando scarica il modello Mistral 7B (~4 GB) la prima volta. **Non ripeterlo** a meno che non lo elimini manualmente dal container.

## Avvio della sola infrastruttura (senza microservizi Java)

Se vuoi preparare prima solo i servizi di base (utile per test database o Kafka):

```powershell
docker compose up -d postgres kafka keycloak minio chromadb ollama ai-engine
```

## Verifica rapida dello stato

```powershell
docker compose ps
```

## Endpoint di salute utili da mostrare

```powershell
# Gateway
curl http://localhost:8080/actuator/health

# AI Engine
curl http://localhost:8000/api/v1/ai/health
```

## Come ottenere un token JWT per la demo

Puoi usare Keycloak da browser, oppure richiederlo via API.

### Richiesta token via PowerShell

```powershell
$body = @{
  client_id = 'zeta-portal'
  username = 'admin@zeta.local'
  password = 'admin123'
  grant_type = 'password'
}
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8180/realms/zeta/protocol/openid-connect/token" `
  -ContentType "application/x-www-form-urlencoded" `
  -Body $body
```

Il valore da riutilizzare nelle chiamate successive è `access_token`.

## Credenziali demo

### Keycloak admin
- URL: `http://localhost:8180`
- username: `admin`
- password: `admin`
- Realm: `zeta`

### Utenti demo pre-caricati
- `admin@zeta.local` / `admin123`
- `user@zeta.local` / `user123`

### MinIO Console
- URL: `http://localhost:9001`
- access key: `minioadmin`
- secret key: `minioadmin123`

### PostgreSQL
- Host: `localhost`
- Port: `5432`
- User: `zeta`
- Password: `zeta_secret`
- Databases: `zeta_users`, `zeta_pec`

### Kafka
- Bootstrap server: `localhost:9092`

## API essenziali da mostrare al cliente

### 1. Creazione utente

**Endpoint:** `POST /api/v1/users`

**Comando:**
```powershell
$jwt = '<ACCESS_TOKEN>'
$payload = @{
  email = 'mario.rossi@zeta.local'
  firstName = 'Mario'
  lastName = 'Rossi'
  organization = 'Zeta'
  role = 'USER'
} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/users" `
  -Headers @{ Authorization = "Bearer $jwt" } `
  -ContentType "application/json" `
  -Body $payload
```

### 2. Recupero utente per ID

**Endpoint:** `GET /api/v1/users/{id}`

### 3. Lista utenti

**Endpoint:** `GET /api/v1/users`

### 4. Attivazione servizio PEC

**Endpoint:** `POST /api/v1/users/{id}/services/PEC`

**Comando:**
```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/users/<USER_ID>/services/PEC" `
  -Headers @{ Authorization = "Bearer $jwt" }
```

Trigger l'evento Kafka che attiva il provisioning automatico della mailbox.

### 5. Recupero mailbox PEC dell'utente

**Endpoint:** `GET /api/v1/mailboxes/user/{userId}`

### 6. Invio messaggio PEC

**Endpoint:** `POST /api/v1/mailboxes/{mailboxId}/messages`

**Comando:**
```powershell
$messagePayload = @{
  recipientAddress = 'destinatario@pec.local'
  subject = 'Test PEC'
  body = 'Messaggio di prova dalla demo Zeta'
} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/mailboxes/<MAILBOX_ID>/messages" `
  -Headers @{ Authorization = "Bearer $jwt" } `
  -ContentType "application/json" `
  -Body $messagePayload
```

### 7. Lista messaggi PEC

**Endpoint:** `GET /api/v1/mailboxes/{mailboxId}/messages`

### 8. Indicizzazione documento nel motore AI

**Endpoint:** `POST /api/v1/ai/index`

**Comando:**
```powershell
curl.exe -X POST "http://localhost:8080/api/v1/ai/index" `
  -F "document_id=doc-001" `
  -F "user_id=<USER_ID>" `
  -F "file=@C:\path\al\documento.docx"
```

### 9. Chat RAG

**Endpoint:** `POST /api/v1/ai/chat`

**Comando:**
```powershell
$chatPayload = @{
  user_id = '<USER_ID>'
  query = 'Riassumi il contenuto del documento indicizzato'
} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/ai/chat" `
  -ContentType "application/json" `
  -Body $chatPayload
```

## Flusso demo consigliato, passo per passo

```text
1. Avvio infrastruttura (docker compose up -d)
2. Attesa che tutti i container siano healthy (docker compose ps)
3. Scaricamento modello Ollama (docker exec -it zeta-ollama ollama pull mistral:7b)
4. Recupero JWT da Keycloak
5. Creazione utente
6. Attivazione servizio PEC
7. Verifica creazione mailbox automatica
8. Invio messaggio PEC
9. Lettura messaggi PEC
10. Indicizzazione documento AI
11. Domanda al motore RAG
```

## Troubleshooting

### Un container non parte

```powershell
# Visualizza i log del container
docker compose logs <service-name>

# Segui gli log in tempo reale
docker compose logs -f <service-name>
```

### Postgresql non ha i database

Rimuovi il volume e ricrea:
```powershell
docker compose down
docker volume rm zeta-aruba_postgres-data
docker compose up -d postgres
```

### Keycloak rimane in "starting"

Il healthcheck usa la porta 9000 (non 8180). Aspetta ~60 secondi al primo avvio.

## Stop della demo

### Fermare tutti i servizi

```powershell
docker compose down
```

### Fermare tutto e rimuovere anche i dati

```powershell
docker compose down -v
```

## Ricompilare il codice Java (opzionale)

Se hai modificato il codice Java, riconstruisci le immagini:

```powershell
# Build di tutti i servizi
mvn clean install

# Poi ricrea i container con le nuove immagini
docker compose up --build -d
```
