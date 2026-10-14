# MyGamingJournal API v2 — Design del refactoring backend

- **Data:** 2026-10-14
- **Stato:** in revisione
- **Repository:** `mygamingjournal-api` (nuova repo, importata da `DGSimone99/mygamingjournal-backend`, che resta intatta)
- **Progetto collegato:** `mygamingjournal-web` (frontend React + TypeScript, spec separata, dipende dal contratto API definito qui)

## 1. Obiettivo e contesto

MyGamingJournal è un pezzo di portfolio: un diario videoludico con catalogo giochi, libreria personale, progressi achievement, recensioni, follow, matchmaking e sistema di livelli.

La v1 del backend (Spring Boot 3.4, Java 21, PostgreSQL, JWT) copiava il catalogo RAWG nel proprio database (`Game`, `Achievement`, `RelatedGame`, `DeveloperMember`, nome/descrizione/immagine degli achievement duplicati per utente). Questo è un uso dei dati RAWG da evitare.

La v2 ha tre obiettivi:

1. **Nessun dato del catalogo RAWG persistito.** Il database contiene solo dati degli utenti; i giochi e gli achievement sono riferiti tramite i loro ID RAWG.
2. **Proxy RAWG nel backend** con cache in memoria a scadenza breve: la chiave API resta sul server e il frontend non chiama mai RAWG direttamente.
3. **Codice ripulito e presentabile:** struttura per feature, DTO come record, errori uniformi, migrazioni Flyway, test automatici, CI.

**Criteri di successo**

- Tutte le funzionalità della v1 sono disponibili tramite la nuova API (vedi §4).
- Nessuna tabella contiene dati descrittivi di giochi o achievement (nomi, descrizioni, immagini, rating RAWG).
- L'app si avvia e funziona (auth, profilo, libreria) anche senza chiave RAWG configurata.
- `./mvnw verify` passa in locale e in CI, con Docker come unico prerequisito oltre al JDK 21.
- Swagger UI documenta ogni endpoint in sviluppo; da lì il frontend genera i tipi TypeScript.

**Fuori ambito per questa versione**

- Refresh token / cookie httpOnly (resta un possibile miglioramento futuro).
- Migrazione dei dati della v1: si parte da un database vuoto.
- Nuove funzionalità (liste personalizzate, feed attività, import da Steam, notifiche).

## 2. Funzionalità mantenute

Tutte quelle della v1:

| Funzionalità | Descrizione |
|---|---|
| Account | Registrazione, login, cambio password, eliminazione account |
| Profilo | Nome visualizzato, bio, lingue, account di gioco (Steam, PSN, Xbox, Nintendo, Epic, Riot, Discord), avatar su Cloudinary |
| Libreria | Per ogni gioco: stato (`WISHLIST`, `PLAYING`, `COMPLETED`, `DROPPED`, `BACKLOG`), voto personale, ore giocate, note |
| Achievement | Segnare come sbloccati gli achievement RAWG di un gioco in libreria |
| Recensioni | Una recensione (testo + voto) per utente per gioco, visibile a tutti |
| Social | Follow unidirezionale, ricerca utenti, profili pubblici con statistiche |
| Matchmaking | Disponibilità a giocare per gioco, con piattaforme, lingue e data limite; ricerca dei giocatori disponibili |
| Livelli | Esperienza e livello calcolati dall'attività (`LevelingSystem` invariato) |

Il voto personale (sulla entry di libreria) e il voto della recensione restano **separati**, come nella v1.

## 3. Modello dati

Lo schema viene creato da zero con Flyway (`V1__init.sql`). Hibernate gira con `ddl-auto=validate`. Gli ID interni sono `bigint` generati; gli ID RAWG sono `bigint`. Gli istanti temporali sono `timestamptz` (`Instant` in Java).

### Entità rimosse

`Game`, `Achievement`, `RelatedGame`, `DeveloperMember`, `GameRepository`, `AchievementRepository`, l'intero package `rawg/` della v1 (mapper verso entità e salvataggio), `GameBootstrap`, `CloudinaryController` (`/api/images/uploadme`, doppione dell'upload avatar).

### Tabelle

| Tabella | Colonne | Vincoli e note |
|---|---|---|
| `users` | `id`, `username`, `email`, `password_hash`, `display_name`, `avatar_url`, `bio`, `experience`, `level`, `created_at` | `username` ed `email` unici. I flag `accountNonExpired`/`Locked`/… della v1 spariscono (sempre `true` nell'implementazione di `UserDetails`). |
| `user_roles` | `user_id`, `role` | `role` ∈ {`USER`, `ADMIN`} |
| `user_languages` | `user_id`, `language_code` | Codici ISO 639-1 |
| `user_gaming_accounts` | `user_id` (PK), `steam`, `psn`, `xbox`, `nintendo`, `epic`, `riot`, `discord` | 1:1 con `users`; sostituisce i 7 campi sparsi su `User` |
| `user_follows` | `follower_id`, `followed_id`, `created_at` | PK composta; `follower_id <> followed_id`. Sostituisce `user_friends` |
| `game_entries` | `id`, `user_id`, `rawg_game_id`, `status`, `personal_rating`, `hours_played`, `notes`, `added_at`, `updated_at` | `UNIQUE(user_id, rawg_game_id)`; `personal_rating` 0–10 con un decimale, nullable; `hours_played` ≥ 0 |
| `achievement_progress` | `entry_id`, `rawg_achievement_id`, `unlocked_at` | PK `(entry_id, rawg_achievement_id)`. Una riga esiste solo se l'achievement è sbloccato |
| `play_availability` | `entry_id` (PK), `platforms text[]`, `languages text[]`, `available_until` | La presenza della riga = utente disponibile per quel gioco. `available_until` nullable = senza scadenza |
| `reviews` | `id`, `user_id`, `rawg_game_id`, `text`, `score`, `created_at`, `updated_at` | `UNIQUE(user_id, rawg_game_id)`; `score` 1–10 intero |

Le cancellazioni sono in cascata: eliminare un utente rimuove entry, progressi, disponibilità, recensioni e follow; eliminare una entry rimuove i suoi progressi e la disponibilità.

### Logica derivata

- **Statistiche utente** (giochi totali, completati, in wishlist, ore totali, achievement sbloccati): query aggregate nei repository, mai caricando tutte le entry in memoria.
- **Esperienza e livello:** `ProgressionService` ricalcola `experience` e `level` con `LevelingSystem` dopo ogni operazione che cambia entry o achievement, nella stessa transazione. I valori restano salvati su `users` per una lettura veloce.
- **Voto medio della community** per gioco: media di `reviews.score`, separata dal rating RAWG.
- **Disponibilità scadute:** le query del matchmaking escludono le righe con `available_until < oggi`. Non serve un job di pulizia.

**Limite noto:** senza nomi dei giochi nel database, la libreria non può essere ordinata o cercata per titolo lato DB. Il backend filtra e ordina per stato, voto, ore e data di aggiunta; ordinamento e ricerca per titolo li fa il client sulla pagina ricevuta.

## 4. API

### Convenzioni

- Prefisso `/api`. JSON in camelCase.
- Risposte con DTO propri (record Java), mai il JSON di RAWG inoltrato così com'è.
- Paginazione uniforme: query `page` (da 1) e `pageSize` (default 20, max 40); risposta `{ items, page, pageSize, totalItems }`.
- Errori come `ProblemDetail` (RFC 9457): `400` validazione (con l'elenco dei campi non validi), `401`, `403`, `404`, `409` conflitto, `502` errore RAWG, `503` RAWG non configurato o non raggiungibile.
- Le risorse legate a un gioco usano l'ID RAWG nel path.
- 🔒 = richiede `Authorization: Bearer <jwt>`. Senza 🔒 = pubblico; dove ha senso, la risposta si arricchisce se l'utente è autenticato.

### Endpoint

**Auth**

| Metodo | Path | Note |
|---|---|---|
| POST | `/auth/register` | `{ username, email, password }` → `201 { accessToken, user }`; `409` se username o email esistono già |
| POST | `/auth/login` | `{ usernameOrEmail, password }` → `{ accessToken, user }`; `401` se le credenziali sono errate |

**Catalogo (proxy RAWG)**

| Metodo | Path | Note |
|---|---|---|
| GET | `/games` | Query: `search`, `genre`, `developer`, `platform`, `ordering`, `dates`, `page`, `pageSize` → pagina di `GameSummary` |
| GET | `/games/{rawgId}` | `GameDetails`: descrizione, date, metacritic, ESRB, generi, piattaforme, requisiti, developer, publisher, tag, screenshot, link Reddit, più statistiche della community (voto medio recensioni, numero di utenti con il gioco in libreria) |
| GET | `/games/{rawgId}/achievements` | Lista completa degli achievement RAWG (nome, descrizione, immagine, percentuale) |
| GET | `/games/{rawgId}/related` | `{ series, additions, parents }` come liste di `GameSummary` |
| GET | `/genres` | Per i filtri del catalogo |
| GET | `/platforms` | Piattaforme "parent" per filtri e icone |

Le viste del catalogo della v1 (top, new, coming, genre, developer) diventano combinazioni di `ordering` e `dates` impostate dal frontend.

**Community**

| Metodo | Path | Note |
|---|---|---|
| GET | `/games/{rawgId}/reviews` | Paginato, più recenti per prime |
| PUT | 🔒 `/games/{rawgId}/reviews/me` | Crea o aggiorna la propria recensione `{ text, score }` |
| DELETE | 🔒 `/games/{rawgId}/reviews/me` | `204` |
| GET | 🔒 `/games/{rawgId}/available-players` | Query: `platform`, `language`, `page`. Esclude l'utente corrente e le disponibilità scadute |

**Profilo corrente**

| Metodo | Path | Note |
|---|---|---|
| GET | 🔒 `/me` | Profilo completo, account di gioco, statistiche, livello |
| PATCH | 🔒 `/me` | Aggiornamento parziale di `displayName`, `bio`, `languages`, `gamingAccounts` (sostituisce i 4 PUT della v1) |
| PUT | 🔒 `/me/password` | `{ currentPassword, newPassword }`; `400` se la password attuale è errata |
| PUT | 🔒 `/me/avatar` | Multipart; carica su Cloudinary e restituisce il nuovo `avatarUrl` |
| DELETE | 🔒 `/me` | Elimina l'account; `204` |

**Libreria**

| Metodo | Path | Note |
|---|---|---|
| GET | 🔒 `/me/library` | Query: `status`, `sort` (`addedAt`, `updatedAt`, `rating`, `hours`), `page`. Ogni entry include `game: GameSummary` (o `null` se RAWG non risponde per quel gioco) e il conteggio degli achievement sbloccati |
| GET | 🔒 `/me/library/index` | Mappa compatta `{ [rawgId]: status }` per i badge nel catalogo |
| PUT | 🔒 `/me/library/{rawgId}` | Upsert `{ status, personalRating?, hoursPlayed?, notes? }`. Alla creazione verifica che il gioco esista su RAWG (`404` altrimenti) |
| DELETE | 🔒 `/me/library/{rawgId}` | `204` |
| GET | 🔒 `/me/library/{rawgId}/achievements` | Achievement RAWG uniti ai progressi: ogni elemento ha `unlocked` e `unlockedAt` |
| PUT | 🔒 `/me/library/{rawgId}/achievements/{achievementId}` | Segna come sbloccato (idempotente) |
| DELETE | 🔒 `/me/library/{rawgId}/achievements/{achievementId}` | Segna come non sbloccato (idempotente) |
| PUT | 🔒 `/me/library/{rawgId}/availability` | `{ platforms, languages, availableUntil? }` |
| DELETE | 🔒 `/me/library/{rawgId}/availability` | `204` |

Le operazioni su achievement e disponibilità restituiscono `404` se il gioco non è nella libreria dell'utente.

**Social e utenti**

| Metodo | Path | Note |
|---|---|---|
| GET | 🔒 `/me/following` | Paginato |
| PUT | 🔒 `/me/following/{userId}` | Idempotente; `400` se si prova a seguire sé stessi |
| DELETE | 🔒 `/me/following/{userId}` | Idempotente |
| GET | `/users` | Query: `search` (username o nome visualizzato), `page` |
| GET | `/users/{userId}` | Profilo pubblico e statistiche; se autenticato include `followedByMe` |
| GET | `/users/{userId}/library` | Come `/me/library`, in sola lettura |
| GET | `/users/{userId}/reviews` | Paginato |

## 5. Proxy RAWG

- **Client:** interfaccia `@HttpExchange` su `RestClient`, base URL `https://api.rawg.io/api`, chiave aggiunta come query param `key` da un interceptor. Timeout di connessione 3 s e di lettura 10 s.
- **Chiave:** proprietà `rawg.api-key: ${RAWG_API_KEY:}`. Nel repo c'è solo `.env.example` con un placeholder; `.env` è nel `.gitignore`. Se la chiave è vuota, l'app parte e gli endpoint del catalogo rispondono `503` con il messaggio "RAWG API key not configured". Per cambiare chiave basta modificare l'ambiente e riavviare.
- **Mapping:** `RawgMapper` converte le risposte RAWG nei DTO propri (`GameSummary`, `GameDetails`, `AchievementDto`, …). I DTO RAWG grezzi restano interni al package `catalog`.
- **Cache Caffeine** (solo in memoria, nessuna persistenza):

  | Cache | TTL | Max elementi |
  |---|---|---|
  | Liste e ricerche `/games` | 10 min | 500 |
  | Dettagli gioco | 12 h | 2000 |
  | Achievement di un gioco | 12 h | 500 |
  | Giochi correlati | 12 h | 500 |
  | Generi, piattaforme | 24 h | 10 |

- **Paginazione achievement:** il client legge tutte le pagine RAWG (fino a un limite di sicurezza di 20 pagine) e mette in cache la lista completa.
- **Arricchimento libreria:** per una pagina di entry, i dettagli dei giochi vengono recuperati in parallelo con i virtual thread (Java 21), passando sempre dalla cache. Un errore su un singolo gioco produce `game: null` per quella entry e non fa fallire la pagina.
- **Errori:** `404` di RAWG → `404`; `401`/`403` di RAWG (chiave non valida) → `503` con un messaggio chiaro; timeout e `5xx` → `502`; `429` → `503`. Gli errori vengono registrati nei log senza mai includere la chiave.
- **Attribuzione:** la richiede RAWG ed è responsabilità del frontend ("Data by RAWG" con link nelle pagine con dati di gioco, oltre alla pagina Credits).

## 6. Sicurezza

- JWT HS256 (jjwt 0.12), secret da `JWT_SECRET` (almeno 256 bit), scadenza 7 giorni, claim `sub` = id utente.
- Password con BCrypt. Validazione: almeno 8 caratteri.
- Sessione stateless e CSRF disabilitato (API con solo bearer token).
- CORS da `CORS_ALLOWED_ORIGINS` (lista separata da virgole).
- Upload avatar: solo immagini (`image/jpeg`, `image/png`, `image/webp`), massimo 5 MB.
- Endpoint pubblici: `/auth/**`, `GET /games/**` (tranne `available-players`), `GET /genres`, `GET /platforms`, `GET /users/**`, Swagger in `dev`. Tutto il resto richiede autenticazione.

## 7. Struttura del codice

Package base `it.mygamingjournal` (minuscolo, come da convenzione Java), organizzato per feature:

```
it.mygamingjournal
├── auth          JWT, filtro, SecurityConfig, AuthController/Service
├── user          User, profilo, account di gioco, /me, /users
├── social        follow
├── library       GameEntry, AchievementProgress, PlayAvailability, /me/library
├── review        recensioni
├── matchmaking   ricerca dei giocatori disponibili
├── catalog       RawgClient, RawgMapper, cache, CatalogService, controller /games /genres /platforms
├── progression   LevelingSystem, ProgressionService
├── media         Cloudinary (upload avatar)
└── common        ProblemDetail advice, PageResponse, configurazione
```

- DTO come `record`; Lombok solo sulle entità.
- Mapper scritti a mano, piccoli e testati.
- Validazione Jakarta su tutti i body e i parametri.
- Un solo `@RestControllerAdvice` (sostituisce `ExceptionHandlerClass` e `CustomErrorController`).
- Niente codice commentato o morto.
- Dipendenze aggiornate all'ultima versione stabile di Spring Boot. La scelta tra la linea 3.5.x e la 4.x si fa nel piano di implementazione, verificando la compatibilità di springdoc, jjwt e Cloudinary.

## 8. Configurazione e ambiente

- `application.yml` con i profili `dev`, `test` e `prod`.
- Variabili d'ambiente: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `RAWG_API_KEY`, `CLOUDINARY_URL`, `CORS_ALLOWED_ORIGINS`. Tutte documentate in `.env.example`.
- **Sviluppo:** `compose.yaml` con PostgreSQL; `spring-boot-docker-compose` lo avvia automaticamente con l'app. Swagger UI attivo su `/swagger-ui.html`, specifica OpenAPI su `/v3/api-docs`.
- **Prerequisiti:** JDK 21 e Docker. Maven non serve (`mvnw`).
- **Produzione:** `Dockerfile` multi-stage. Target indicativi: Postgres su Neon, backend su un hosting container gratuito o a basso costo. Il deploy concreto è l'ultimo passo del piano ed è opzionale.

## 9. Test

| Livello | Cosa | Strumenti |
|---|---|---|
| Unit | `LevelingSystem`, `ProgressionService`, `RawgMapper`, mapper DTO | JUnit 5, AssertJ, Mockito |
| Slice | Controller: validazione, sicurezza (401/403), formato `ProblemDetail` | `@WebMvcTest` |
| Client RAWG | Mapping, paginazione achievement, cache (seconda chiamata senza HTTP), chiave mancante → 503, RAWG non raggiungibile → 502 | `MockRestServiceServer` |
| Integrazione | Registrazione → login → aggiunta a libreria → sblocco achievement → livello aggiornato; recensioni (upsert e unicità); follow; matchmaking (filtri, esclusione scadute); eliminazione account in cascata | `@SpringBootTest` + Testcontainers PostgreSQL, RAWG simulato |

Nessun test chiama il vero RAWG.

**CI:** una GitHub Action esegue `./mvnw verify` a ogni push e pull request (Docker è disponibile sui runner Ubuntu), con il badge nel README.

## 10. README

Il README della v2 descrive la nuova architettura (nessun dato RAWG persistito, proxy con cache), i prerequisiti, l'avvio in locale in tre passi (copiare `.env.example`, inserire la chiave, `./mvnw spring-boot:run`), il link a Swagger, i comandi per i test e l'attribuzione a RAWG.
