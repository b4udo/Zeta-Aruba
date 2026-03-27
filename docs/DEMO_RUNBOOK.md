# Piattaforma Zeta — Runbook Demo Cliente

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
- Java 21 disponibile solo se vuoi rifare il build locale Maven

## Build locale del progetto (PowerShell)

Esegui questi comandi dalla root del progetto:

```powershell
Set-Location "c:\Users\edoardo.baudino\Downloads\PRJ\piattaforma-zeta"
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\corretto-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn clean install
```

## Avvio completo della demo

### Avvio di tutti i servizi

```powershell
Set-Location "c:\Users\edoardo.baudino\Downloads\PRJ\piattaforma-zeta"
docker compose up --build -d
```

### Download del modello Ollama richiesto dalla demo

```powershell
docker exec -it zeta-ollama ollama pull phi3:mini
```

### Verifica rapida dello stato

```powershell
docker compose ps
```

## Come aprire PostgreSQL, Kafka e Keycloak

### Avviare solo PostgreSQL, Kafka e Keycloak

Se vuoi preparare prima solo l'infrastruttura base:

```powershell
Set-Location "c:\Users\edoardo.baudino\Downloads\PRJ\piattaforma-zeta"
docker compose up -d postgres kafka keycloak
```

### Aprire i log dei tre servizi

```powershell
docker compose logs -f postgres
```

```powershell
docker compose logs -f kafka
```

```powershell
docker compose logs -f keycloak
```

### Come usare PostgreSQL nella demo

- Host: `localhost`
- Porta: `5432`
- User: `zeta`
- Password: `zeta_secret`
- Database creati dallo script init:
  - `zeta_users`
  - `zeta_pec`

Lo script `config/postgres/init-databases.sh` crea i database all'avvio del container.

### Come usare Kafka nella demo

- Bootstrap server: `localhost:9092`
- Ruolo nella demo:
  - `user-service` pubblica eventi come `user.service.activated`
  - `pec-service` consuma questi eventi per creare automaticamente mailbox PEC

Per vedere i log di Kafka durante l'attivazione di un servizio:

```powershell
docker compose logs -f kafka
```

### Come usare Keycloak nella demo

- URL: `http://localhost:8180`
- Admin user: `admin`
- Admin password: `admin`
- Realm importato automaticamente: `zeta`

Utenti demo già disponibili:

- `admin@zeta.local` / `admin123`
- `user@zeta.local` / `user123`

Keycloak serve per autenticare le richieste e rilasciare il JWT da usare contro il gateway e i microservizi.

## Endpoint di salute utili da mostrare

```powershell
curl http://localhost:8080/actuator/health
```

```powershell
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

## API essenziali da mostrare al cliente

### 1. Creazione utente

**Endpoint**

```text
POST /api/v1/users
```

**Cosa fa**

Crea un utente applicativo sulla piattaforma, con email, nome, organizzazione e ruolo.

**Quando usarlo**

È il primo passo del flusso demo: senza utente non puoi attivare i servizi successivi.

**Comando**

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

**Endpoint**

```text
GET /api/v1/users/{id}
```

**Cosa fa**

Restituisce i dettagli dell'utente creato, utile per confermare che la persistenza su PostgreSQL funzioni.

### 3. Lista utenti

**Endpoint**

```text
GET /api/v1/users
```

**Cosa fa**

Restituisce la lista paginata degli utenti presenti.

### 4. Aggiornamento utente

**Endpoint**

```text
PUT /api/v1/users/{id}
```

**Cosa fa**

Aggiorna i campi dell'utente già creato.

### 5. Attivazione servizio PEC

**Endpoint**

```text
POST /api/v1/users/{id}/services/PEC
```

**Cosa fa**

Aggiunge il servizio PEC ai servizi attivi dell'utente e pubblica un evento Kafka.

**Perché è importante in demo**

È il ponte tra `user-service` e `pec-service`: mostra il disaccoppiamento via eventi.

**Comando**

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/users/<USER_ID>/services/PEC" `
  -Headers @{ Authorization = "Bearer $jwt" }
```

### 6. Recupero mailbox PEC dell'utente

**Endpoint**

```text
GET /api/v1/mailboxes/user/{userId}
```

**Cosa fa**

Restituisce le mailbox associate a quell'utente.

**Perché è importante in demo**

Conferma che il consumer Kafka ha ricevuto l'evento e ha creato la mailbox.

### 7. Creazione mailbox manuale

**Endpoint**

```text
POST /api/v1/mailboxes
```

**Cosa fa**

Crea una mailbox PEC esplicita per un utente, senza passare dal provisioning automatico.

### 8. Invio messaggio PEC

**Endpoint**

```text
POST /api/v1/mailboxes/{mailboxId}/messages
```

**Cosa fa**

Invia un messaggio tramite lo stub Aruba, salva il messaggio e lo rende disponibile nel sistema.

**Comando**

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

### 9. Lista messaggi PEC

**Endpoint**

```text
GET /api/v1/mailboxes/{mailboxId}/messages
```

**Cosa fa**

Legge i messaggi della mailbox in formato paginato.

### 10. Sincronizzazione messaggi PEC

**Endpoint**

```text
POST /api/v1/mailboxes/{mailboxId}/messages/sync
```

**Cosa fa**

Simula il recupero messaggi dal provider Aruba PEC tramite lo stub interno.

### 11. Indicizzazione documento nel motore AI

**Endpoint**

```text
POST /api/v1/ai/index
```

**Cosa fa**

Carica un PDF o DOCX, estrae il testo, lo spezza in chunk e lo indicizza nel vector store.

**Comando**

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/ai/index" `
  -F "document_id=doc-001" `
  -F "user_id=<USER_ID>" `
  -F "file=@C:\path\al\documento.docx"
```

### 12. Chat RAG

**Endpoint**

```text
POST /api/v1/ai/chat
```

**Cosa fa**

Riceve una domanda, cerca i chunk più rilevanti per l'utente, costruisce il prompt e chiede la risposta a Ollama.

**Comando**

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
1. Avvio infrastruttura
2. Login su Keycloak / recupero JWT
3. Creazione utente
4. Attivazione servizio PEC
5. Verifica creazione mailbox automatica
6. Invio messaggio PEC
7. Lettura messaggi PEC
8. Indicizzazione documento AI
9. Domanda al motore RAG
```

## Stop della demo

### Fermare tutti i servizi

```powershell
docker compose down
```

### Fermare tutto e rimuovere anche i volumi

```powershell
docker compose down -v
```
