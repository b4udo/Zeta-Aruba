# Piattaforma Zeta — Scelte Tecniche e Giustificazioni

> Documento di riferimento per esporre e difendere le decisioni architetturali e tecnologiche durante il colloquio tecnico.

---

## Premessa: Approccio Progettuale

Il progetto è stato costruito con un doppio livello:
1. **Architettura target di produzione** — descritta nei diagrammi e nella documentazione, pensata per Kubernetes on-premise con volumi reali (~2M caselle PEC, 5M+ msg/giorno, 50 GB/giorno di documenti).
2. **Implementazione dimostrativa** — il codice compilabile e avviabile in locale con `docker-compose up`, che prova la validità dell'architettura senza richiedere un cluster K8s o hardware dedicato.

Questa separazione è intenzionale: l'architettura guida le scelte (interfacce, contratti, event-driven), ma l'implementazione resta eseguibile su un portatile da sviluppatore.

---

## 1. Architettura a Microservizi

### Scelta
Architettura a microservizi con comunicazione REST sincrona + eventi asincroni, orchestrata da Docker Compose in locale e predisposta per Kubernetes in produzione.

### Giustificazione
- **Scalabilità orizzontale**: i singoli servizi (utenti, PEC, AI) possono scalare indipendentemente in base al carico.
- **Resilienza**: il failure di un servizio non compromette gli altri.
- **Deploy indipendente**: ogni microservizio ha il proprio ciclo di sviluppo e deploy.
- **On-premise**: nessuna dipendenza da cloud provider; tutto gira in container Docker.

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| Monolite | Non scala per i volumi richiesti; deploy rischioso |
| SOA con ESB | Accoppiamento tramite ESB; single point of failure |

---

## 2. Spring Boot come Framework Applicativo

### Scelta
Java 21 + Spring Boot 3.x per i microservizi backend.

### Giustificazione
- **Requisito esplicito** del documento di progetto.
- **Ecosistema maturo**: Spring Security, Spring Data JPA, Spring Boot Actuator coprono autenticazione, persistenza e health check.
- **Virtual Threads (Java 21)**: migliorano il throughput per workload I/O-bound (chiamate REST verso API Aruba) senza la complessità della programmazione reattiva.
- **Organizzazione per feature/dominio**: package strutturati per bounded context (`com.zeta.user`, `com.zeta.pec`) anziché per layer tecnico.

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| Quarkus | Ecosistema meno maturo; il requisito specifica Spring Boot |
| Micronaut | Meno adottato in contesto enterprise |

---

## 3. Autenticazione e Autorizzazione — OAuth2 + JWT

### Scelta
- **Keycloak** come Identity Provider self-hosted (container in docker-compose).
- **OAuth2 Authorization Code Flow** per il portale web.
- **JWT** come formato del token di accesso.
- **Spring Security OAuth2 Resource Server** in ogni microservizio per la validazione del token.

### Giustificazione
- **Keycloak è open-source e on-premise**: nessuna dipendenza da IdP cloud (Auth0, Okta). Supporta OAuth2, OpenID Connect.
- **OAuth2 compliance**: il documento specifica integrazione con sistemi Aruba via API REST con autenticazione OAuth2.
- **JWT stateless**: i microservizi validano il token localmente senza chiamare l'IdP ad ogni richiesta.
- **RBAC**: Keycloak supporta realm e ruoli per gestire utenti e permessi (ADMIN, USER, OPERATOR).

### Sicurezza token API Aruba
In produzione le chiavi di accesso per i servizi Aruba sarebbero conservate in un secret manager (es. HashiCorp Vault). Nella demo locale sono iniettate come variabili d'ambiente nel docker-compose — la struttura del codice resta identica grazie all'uso di `@Value` / `@ConfigurationProperties`.

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| Session-based auth | Non scala in architettura a microservizi stateless |
| API Key semplice | Non standard OAuth2; meno sicuro per un portale web |

---

## 4. API Gateway — Spring Cloud Gateway

### Scelta
Spring Cloud Gateway come unico punto di ingresso.

### Giustificazione
- **Routing centralizzato**: instrada le richieste verso il microservizio corretto in base al path.
- **Cross-cutting concerns**: CORS, logging, propagazione JWT gestiti a livello di gateway.
- **Integrato con Spring**: configurazione YAML nativa, stessa stack dei microservizi.
- **Non-blocking**: basato su Reactor/Netty, gestisce alto throughput.

### Note per la demo locale
Il Gateway gira come container Docker e fa proxy verso `user-service:8081` e `pec-service:8082` tramite la rete Docker interna. In produzione, il routing sarebbe basato su Service DNS di Kubernetes.

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| Kong | Introduce tecnologia extra (Lua/Go) fuori dallo stack Java |
| Chiamata diretta ai servizi | Perde i vantaggi del routing centralizzato e del cross-cutting |

---

## 5. Persistenza dei Dati

### Scelta
- **PostgreSQL** come database relazionale primario.
- **MinIO** come object storage S3-compatible per i documenti binari.

### Giustificazione

#### PostgreSQL
- **Open-source, on-premise-friendly**: nessuna licenza, ampiamente supportato.
- **JSON/JSONB**: supporto nativo per dati semi-strutturati (metadata documenti).
- **ACID compliance**: fondamentale per dati utente e transazioni di provisioning.
- **Leggero in Docker**: un singolo container `postgres:16-alpine` è sufficiente per la demo.

#### MinIO
- **S3-compatible on-premise**: i documenti (~50 GB/giorno in produzione) richiedono un object storage dedicato.
- **Singolo container per la demo**: `minio/minio` gira con un comando, ma in produzione si scala a multi-nodo con erasure coding.

### Scelta di NON includere Redis nella demo
Redis sarebbe necessario in produzione per caching e rate limiting. Nella demo locale è omesso per ridurre complessità — il rate limiting non serve e il caching non è critico per la dimostrazione funzionale. La sua integrazione in produzione è solo una dipendenza Maven e una configurazione in `application.yml`.

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| MySQL | PostgreSQL offre funzionalità JSON native superiori |
| MongoDB | Overkill; PostgreSQL JSONB copre le esigenze semi-strutturate |
| File system per documenti | Non scala; nessuna API standard |

---

## 6. Comunicazione tra Microservizi

### Scelta
- **Sincrona**: REST (OpenAPI 3.0) per operazioni request/response.
- **Asincrona**: Apache Kafka per eventi e workflow asincroni.

### Giustificazione

#### REST sincrono
- Standard de facto; interoperabilità con API Aruba (già REST).
- Ideale per operazioni CRUD e query immediate.

#### Apache Kafka
- **Volume**: 5+ milioni messaggi/giorno in produzione richiedono un broker ad alto throughput.
- **Durabilità**: i messaggi persistono su disco; nessuna perdita anche con consumer down.
- **Decoupling**: il servizio PEC pubblica "messaggio ricevuto"; il servizio AI sottoscrive indipendentemente per l'indicizzazione.
- **Replay**: possibilità di riprocessare eventi (es. re-indicizzazione semantica).

### Note per la demo locale
Kafka gira come singolo broker Bitnami (KRaft mode, senza Zookeeper) — un singolo container leggero. In produzione si userebbe un cluster a 3-5 broker (o Strimzi operator su K8s).

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| RabbitMQ | Kafka eccelle per event streaming ad alto volume e replay |
| Comunicazione solo sincrona | Eliminerebbe il decoupling necessario per la pipeline AI |

---

## 7. Indicizzazione Semantica e AI Engine (On-Premise)

### Scelta
- **Architettura RAG** (Retrieval-Augmented Generation) interamente on-premise.
- **LLM locale**: modello open-source (Mistral 7B) servito tramite **Ollama**.
- **Embedding model**: `all-MiniLM-L6-v2` (o equivalente multilingua) via sentence-transformers.
- **Vector store**: **ChromaDB** (embedded, singolo container, zero dipendenze extra).
- **Orchestratore RAG**: Python con **LangChain**.
- **Document processing**: estrazione testo semplificata con PyPDF2/python-docx + chunking custom.

### Giustificazione

#### RAG vs. fine-tuning
- **RAG** permette di interrogare i documenti senza ri-addestrare il modello. I documenti vengono indicizzati come embedding vettoriali e recuperati al momento della query.
- **Fine-tuning** richiederebbe GPU dedicate per ogni aggiornamento e non garantisce la freschezza dei dati.

#### Ollama per il LLM
- **Semplicità**: un singolo container Docker che serve Mistral 7B con API OpenAI-compatibili. Nessun setup GPU complesso per la demo.
- **On-premise**: nessun cloud provider o servizio AI di terze parti (vincolo fondamentale).
- **In produzione**: sostituibile con **vLLM** su GPU NVIDIA A100 per throughput superiore (PagedAttention, quantizzazione GPTQ/AWQ).

#### ChromaDB vs. Milvus
- **ChromaDB**: singolo container, zero dipendenze (no etcd, no MinIO dedicato), ideale per la demo locale.
- **Milvus**: più scalabile ma richiede 3+ container (etcd, minio, milvus). Indicato per la produzione.
- Il codice usa un'interfaccia astratta: in produzione si sostituisce il client ChromaDB con Milvus senza cambiare la logica RAG.

#### Pipeline di indicizzazione
1. Documento caricato → evento Kafka `document.uploaded`
2. Consumer Python estrae testo e lo divide in chunk (500 token, overlap 100)
3. Ogni chunk → embedding vettoriale
4. Embedding + metadata salvati in ChromaDB (collection per utente)
5. Query utente (chat) → embedding query → ricerca top-k → contesto iniettato nel prompt LLM → risposta

### Evoluzione verso produzione
| Componente demo | Componente produzione |
|---|---|
| Ollama (CPU) | vLLM su GPU NVIDIA (A100/H100) |
| ChromaDB | Milvus cluster (3+ nodi) |
| PyPDF2 / python-docx | Apache Tika (più formati supportati) |
| all-MiniLM-L6-v2 | multilingual-e5-large (migliore qualità) |

### Alternative scartate
| Alternativa | Motivo dello scarto |
|---|---|
| OpenAI API | Vietato: vincolo on-premise |
| Elasticsearch kNN | Meno performante di un vector DB per alta dimensionalità |
| FAISS standalone | Non ha persistenza nativa |

---

## 8. Logging Strutturato (ELK-Compatible)

### Scelta
Logging JSON via **Logback + logstash-logback-encoder**, compatibile con stack ELK.

### Giustificazione
- **ELK è requisito** del documento di progetto.
- **JSON structured logging**: ogni log entry include `timestamp`, `level`, `serviceName`, `traceId`, `message`.
- **Compatibile con Kubernetes**: in produzione i log JSON vengono raccolti da Fluentd/Filebeat e inviati a Elasticsearch.
- **Nella demo locale**: i log JSON vengono stampati su stdout e sono leggibili con `docker-compose logs`.

### Note per la demo
Il docker-compose locale **non** include uno stack ELK completo (pesante ~4 GB RAM). Il formato dei log è già ELK-ready — in produzione basta aggiungere un Logstash/Filebeat sidecar.

### Monitoraggio in produzione (non implementato nella demo)
- **Prometheus + Grafana**: Spring Boot Actuator espone metriche in formato Prometheus (`/actuator/prometheus`). L'endpoint è già abilitato nel codice.
- **OpenTelemetry + Jaeger**: tracing distribuito. Micrometer Tracing in Spring Boot 3.x lo supporta; nella demo il traceId è propagato via header.

---

## 9. Containerizzazione

### Scelta
- **Docker** per il packaging (multi-stage build).
- **docker-compose** per orchestrazione locale.

### Giustificazione
- **Docker è requisito** esplicito del documento.
- **Multi-stage build**: Stage 1 compila con Maven, Stage 2 esegue con JRE slim → immagini ~150 MB anziché ~500 MB.
- **docker-compose**: soddisfa il nice-to-have di esecuzione/test locale con un singolo comando.
- **Non-root user**: i container girano con `USER 1001` per sicurezza base.

### Predisposizione Kubernetes (non implementata)
Il codice è **K8s-ready** by design:
- Ogni servizio espone `/actuator/health/liveness` e `/actuator/health/readiness` (probe standard K8s).
- Le configurazioni sono esternalizzate in `application.yml` con override via variabili d'ambiente (compatibile con ConfigMap/Secret K8s).
- I Dockerfile producono immagini immutabili, pronte per un registry.
- La documentazione include un esempio di manifest K8s (Deployment + Service) per illustrare la predisposizione.

---

## 10. Resilienza

### Scelta
**Resilience4j** per circuit breaker e retry sulle chiamate verso le API Aruba.

### Giustificazione
- Successore di Hystrix, integrato nativamente con Spring Boot 3.x.
- Protegge dalle cascading failures quando le API Aruba (PEC, firma) sono lente o down.
- Nella demo il mock/stub risponde sempre correttamente, ma il circuit breaker è configurato e attivo sull'implementazione "reale" del client.

---

## 11. Dimensionamento Produzione (Note per il Colloquio)

> Il documento specifica che il candidato viene valutato sugli aspetti architetturali/applicativi, ma gradisce osservazioni sul dimensionamento.

### Stime indicative

| Componente | Sizing suggerito (produzione) |
|---|---|
| Cluster K8s | 6-10 worker node (16 core, 64 GB RAM) |
| PostgreSQL | Primary + replica, 16 core, 64 GB RAM, SSD NVMe |
| Kafka | 3-5 broker (8 core, 32 GB RAM), retention 7 giorni |
| MinIO | 4+ nodi, ~20 TB storage iniziale |
| Milvus | 3 nodi, 32 GB RAM |
| GPU per LLM | 2+ NVIDIA A100 80GB per vLLM serving |
| ELK | 3 nodi Elasticsearch (16 core, 64 GB RAM) |

### Considerazioni
- **Storage**: 50 GB/giorno × 365 = ~18 TB/anno. Con erasure coding MinIO → ~27 TB/anno.
- **Kafka throughput**: 5M msg/giorno ≈ 58 msg/sec medio, con picchi 5-10x → cluster da 3 broker sufficiente.
- **LLM inference**: Mistral 7B quantizzato a 4-bit → ~4 GB VRAM → 50+ richieste concorrenti su A100.

---

## Riepilogo Stack Tecnologico

| Layer | Demo locale | Produzione on-premise |
|---|---|---|
| Language/Framework | Java 21, Spring Boot 3.x | idem |
| API Gateway | Spring Cloud Gateway (container) | idem (in K8s pod) |
| Identity Provider | Keycloak (container) | Keycloak (HA, 2+ repliche) |
| Auth Protocol | OAuth2 + JWT | idem |
| Database | PostgreSQL 16 (singola istanza) | PostgreSQL HA (Patroni) |
| Object Storage | MinIO (singolo nodo) | MinIO multi-nodo con erasure coding |
| Message Broker | Kafka singolo broker (KRaft) | Kafka cluster 3-5 broker (Strimzi) |
| AI/LLM | Ollama + Mistral 7B (CPU) | vLLM + Mistral/LLaMA su GPU |
| Vector DB | ChromaDB (singolo container) | Milvus cluster |
| RAG Orchestrator | Python + LangChain | idem |
| Logging | JSON su stdout (ELK-ready) | ELK Stack completo |
| Monitoring | Actuator endpoints | Prometheus + Grafana |
| Tracing | traceId in header | OpenTelemetry + Jaeger |
| Container Runtime | Docker + docker-compose | Docker + Kubernetes + Helm |
| Secret Management | Variabili d'ambiente | HashiCorp Vault |
