# Piattaforma Zeta — Guida a `ai-engine`

## Cos'è `ai-engine`

`ai-engine` è il microservizio Python della piattaforma che implementa una pipeline **RAG** (*Retrieval-Augmented Generation*).

In termini semplici:

- prende documenti caricati dall'utente
- estrae il testo
- trasforma il testo in rappresentazioni numeriche (*embeddings*)
- salva questi vettori in un archivio ricercabile
- quando arriva una domanda, recupera i pezzi di documento più pertinenti
- usa quei contenuti come contesto per generare una risposta con il modello LLM locale

Questo permette di avere una chat che risponde **sui documenti caricati**, invece di rispondere “a caso” come un modello generico.

## Perché esiste nella piattaforma

Nella demo Zeta l'`ai-engine` serve a mostrare la parte di valore aggiunto della piattaforma:

- consultazione intelligente di documenti
- base per knowledge retrieval su contenuti PEC e documentali
- dimostrazione di una componente AI **on-premise**, senza dipendenze cloud esterne

## Stack utilizzato

Il modulo usa questi componenti:

- **FastAPI**: espone le API HTTP del servizio
- **PyPDF2** e **python-docx**: estrazione testo da PDF e DOCX
- **sentence-transformers**: generazione embeddings
- **ChromaDB**: vector store per salvare e cercare i chunk
- **Ollama**: runtime locale del modello LLM
- **phi3:mini**: modello usato per la generazione della risposta

## Come funziona, in pratica

### 1. Indicizzazione documento

Quando chiami:

```text
POST /api/v1/ai/index
```

il servizio esegue questi step:

1. riceve `document_id`, `user_id` e il file caricato
2. legge il file
3. estrae il testo dal documento
4. divide il testo in chunk più piccoli
5. genera un embedding per ogni chunk
6. salva chunk, metadata ed embeddings nel vector store

### 2. Chat sui documenti

Quando chiami:

```text
POST /api/v1/ai/chat
```

il servizio esegue questi step:

1. riceve `user_id` e `query`
2. converte la query in embedding
3. cerca in ChromaDB i chunk più vicini semanticamente
4. costruisce un prompt con il contesto recuperato
5. invia il prompt a Ollama
6. restituisce la risposta generata, insieme ai chunk di contesto usati

## Struttura interna del modulo

### `src/main.py`

È il punto di ingresso FastAPI. Inizializza il servizio e registra i componenti condivisi nell'application state.

### `src/config.py`

Gestisce la configurazione runtime:

- host e porta
- Chroma host/port
- URL di Ollama
- modello da usare
- `top_k`
- timeout richieste
- parametri di chunking

### `src/api/routes.py`

Contiene gli endpoint pubblici:

- `GET /api/v1/ai/health`
- `POST /api/v1/ai/index`
- `POST /api/v1/ai/chat`
- `DELETE /api/v1/ai/index/{document_id}`

### `src/api/schemas.py`

Definisce i modelli request/response della API.

### `src/indexing/document_processor.py`

Fa due cose fondamentali:

1. estrazione testo dai documenti
2. chunking del testo in blocchi più piccoli

Il chunking serve perché i modelli non lavorano bene su documenti interi troppo grandi: si ottengono risultati migliori usando segmenti più piccoli e mirati.

### `src/indexing/embedding_service.py`

Genera gli embeddings dei chunk e delle query.

Questi embeddings sono vettori numerici che permettono di confrontare la similarità semantica tra domanda e contenuto.

### `src/indexing/vector_store.py`

Gestisce il salvataggio e la ricerca dei chunk indicizzati.

Responsabilità principali:

- creare o usare una collection per utente
- aggiungere chunk indicizzati
- cercare i chunk più rilevanti
- eliminare un documento già indicizzato

### `src/chat/llm_client.py`

È il client HTTP verso Ollama.

Responsabilità:

- inviare il prompt al modello
- gestire timeout
- ritentare in caso di errore transient
- normalizzare la risposta

### `src/chat/rag_service.py`

È il cuore del flusso RAG.

Coordina:

- embedding della query
- ricerca chunk rilevanti
- costruzione del prompt
- invocazione del modello
- fallback se Ollama non è disponibile

## Come dialogano i componenti esterni

### ChromaDB

Serve per archiviare i vettori.

- input: chunk + embeddings + metadata
- output: chunk semanticamente vicini a una query

### Ollama

Serve per la generazione della risposta finale.

Riceve un prompt strutturato, ad esempio:

- contesto recuperato dai documenti
- domanda dell'utente
- vincolo di rispondere solo in base ai documenti

### Gateway

Il `gateway` espone l'entrypoint unificato anche per il motore AI:

- `/api/v1/ai/index`
- `/api/v1/ai/chat`
- `/api/v1/ai/health`

Quindi in demo puoi mostrare tutto passando sempre da `localhost:8080`.

## Fallback e resilienza già presenti

Nel modulo ci sono alcune protezioni utili per demo e sviluppo:

- se il modello di embeddings non è subito disponibile, esiste un fallback deterministico
- se ChromaDB non è raggiungibile, il servizio usa una struttura in-memory di fallback
- se Ollama è temporaneamente indisponibile, il servizio prova alcuni retry
- se Ollama resta non disponibile, il servizio restituisce comunque una risposta contestuale di fallback

Questo è molto utile in demo, perché evita errori “catastrofici” quando un container è in warming-up.

## Limiti attuali della demo

La versione corrente è dimostrativa, quindi ha alcuni limiti accettabili:

- supporta solo `PDF` e `DOCX`
- non implementa autenticazione propria
- non ha persistenza distribuita per il vector store
- non gestisce workflow massivi di ingestione documentale
- non implementa scheduler o consumer Kafka dedicati per indicizzazione automatica

## Come usarlo nella demo

### Indicizzare un documento

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/ai/index" `
  -F "document_id=doc-001" `
  -F "user_id=user-demo" `
  -F "file=@C:\path\documento.docx"
```

### Fare una domanda sul documento

```powershell
$payload = @{
  user_id = 'user-demo'
  query = 'Quali informazioni contiene questo documento?'
} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/ai/chat" `
  -ContentType "application/json" `
  -Body $payload
```

### Controllare lo stato del servizio

```powershell
curl http://localhost:8000/api/v1/ai/health
```

## Come raccontarlo al cliente

Una spiegazione semplice ma efficace può essere questa:

> Il modulo `ai-engine` trasforma i documenti in conoscenza interrogabile. Quando carichiamo un file, il sistema lo spezza in parti, le indicizza semanticamente e poi usa un modello locale per rispondere alle domande basandosi solo sul contenuto effettivamente presente nei documenti.

## Evoluzione futura naturale

Se il progetto evolvesse verso produzione, i prossimi passi naturali sarebbero:

- usare un embedding model multilingua più potente
- sostituire il vector store embedded con un cluster dedicato
- introdurre una pipeline asincrona via Kafka per ingestione documentale
- aggiungere osservabilità e metriche
- collegare il motore AI al dominio PEC/documentale in modo automatico
