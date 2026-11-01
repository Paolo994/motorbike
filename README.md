# MotoRide

Social network e pianificatore di road trip per motociclisti.

**Stack:** Java 21 · Spring Boot 4.1 · Spring Security 7 · Thymeleaf + Bootstrap 5 · Leaflet · PostgreSQL 17 · Flyway · Mailpit (dev)

## Documentazione tecnica

- [Architettura](docs/architettura.md): componenti, moduli, tempo reale, sicurezza, modello dei dati, test
- [Infrastruttura](docs/infrastruttura.md): container, rete locale, configurazione, job, servizi esterni, produzione
- [Flussi](docs/flussi.md): registrazione, amicizie, chat, foto, agente notizie, tappe e costi, permessi

## Avvio con Docker (tutto in container)

Requisiti: solo Docker.

```powershell
docker compose --profile app up -d --build    # app + PostgreSQL + Mailpit
docker compose logs -f app                    # log dell'applicazione
docker compose --profile app down             # ferma tutto (i dati restano nel volume pgdata)
```

Dopo una modifica al codice ripetere il primo comando per ricostruire l'immagine.

Per avere un utente admin, prima del primo avvio copia `.env.example` in `.env` (escluso da git)
e imposta `ADMIN_PASSWORD`. Senza password l'admin non viene creato.

L'app è raggiungibile anche dagli altri dispositivi della stessa rete Wi-Fi su `http://<IP-del-PC>:8080`:
vedi [Accesso dagli altri dispositivi](docs/infrastruttura.md#accesso-dagli-altri-dispositivi-della-rete).

## Avvio in locale per lo sviluppo (dall'IDE o da Maven)

Requisiti: JDK 21, Maven 3.9+, Docker in esecuzione. Se il container `app` è attivo, fermalo prima
(`docker compose stop app`) per liberare la porta 8080.

```powershell
$env:ADMIN_PASSWORD = "una-password"   # opzionale: crea l'utente "admin" al primo avvio
mvn spring-boot:run
```

All'avvio Spring Boot lancia automaticamente `compose.yaml` (PostgreSQL + Mailpit).
Al primo avvio partono anche l'import dei prezzi carburante (~1 minuto) e, dopo 20 secondi, il primo giro dell'agente notizie.

| Servizio | URL |
|----------|-----|
| App | http://localhost:8080 |
| Mailpit (email inviate in dev) | http://localhost:8025 |
| PostgreSQL | `localhost:5433`, db/utente/password `motorbike` |
| Area admin | http://localhost:8080/admin (solo utenti ADMIN) |

## Test

```powershell
mvn test
```

I test di integrazione usano un PostgreSQL reale tramite Testcontainers (serve Docker) ed estendono `IntegrationTest`,
che sostituisce con mock i servizi esterni (email, feed RSS, routing). Gli scheduler sono disattivati nei test
(`src/test/resources/config/application.yml`).

## Moduli

```
com.motorbike
├── auth          registrazione, verifica email, login (username o email), seed admin
├── user          entità utente, profilo, dati moto (consumo, serbatoio)
├── photo         foto profilo: upload, ri-codifica sicura, moderazione admin
├── notification  notifiche in-app con push in tempo reale
├── friend        ricerca utenti per username, richieste di amicizia, lista amici
├── chat          conversazioni 1 a 1 tra amici, ricerca, messaggi non letti, push via WebSocket/STOMP
├── news          agente schedulato sui feed RSS, classificazione per topic, iscrizioni, email riepilogative
├── trip          road trip: tappe, fermate, alloggi, partecipanti, costi
├── geo           percorsi (Valhalla, profilo moto), ricerca luoghi (Nominatim), polyline
├── fuel          import open data MIMIT e prezzi della benzina vicino a un punto
├── mail          invio email HTML da template Thymeleaf
├── config        sicurezza, WebSocket, proprietà applicative
└── web           home, area admin, attributi comuni (badge navbar)
```

### Foto profilo
Il file caricato non viene mai salvato così com'è: viene decodificato, raddrizzato secondo l'EXIF, ritagliato
quadrato e ri-codificato in JPEG 400×400 (niente metadati EXIF/GPS, niente contenuti malevoli). Resta visibile
solo al proprietario e agli admin finché un admin non la approva; l'utente riceve una notifica con l'esito.

### Chat
- I messaggi si **inviano via HTTP POST** (CSRF, validazione e autorizzazione di Spring MVC); senza JavaScript il form funziona comunque.
- Il **WebSocket** (`/ws`, STOMP) serve solo al **push** server → browser sulle code personali
  `/user/queue/messages` e `/user/queue/notifications`. Ogni altra iscrizione o invio è negato (`WebSocketConfig`).

### Notizie (agente)
- Ogni 30 minuti (`NEWS_FETCH_INTERVAL`) legge i feed RSS/Atom configurati (11 fonti italiane e inglesi, gestibili da `/admin/news`).
- Ogni articolo va in **un solo topic**: punteggio per parole chiave (3 punti nel titolo, 1 nel riassunto), topic
  predefinito della fonte come spareggio/ripiego. Le parole chiave si modificano dall'admin.
  `ArticleClassifier` è un'interfaccia: in Fase 2 si può aggiungere una classificazione con LLM.
- Si salvano solo titolo, riassunto breve, immagine e link: l'articolo si legge sul sito della fonte.
- Notifiche: per ogni giro, al massimo **una notifica per topic** e **una sola email riepilogativa** per utente.
  Il primo import di una fonte e gli articoli più vecchi di 48 ore non generano notifiche.

### Road trip
- Percorsi calcolati con **Valhalla** (profilo `motorcycle`): evita autostrade/pedaggi su richiesta e restituisce
  le indicazioni svolta per svolta in italiano. Se il servizio non risponde: stima in linea d'aria, segnalata in pagina.
- Costi **deterministici**: km ÷ consumo della moto di chi guarda × prezzo medio reale della benzina self (MIMIT)
  entro 15 km da partenza e arrivo; autonomia = serbatoio × consumo × 0,85 per stimare i rifornimenti.
- Permessi: organizzatore (tutto), editor (tappe), sola lettura. Si possono invitare solo gli amici.

## Configurazione (variabili d'ambiente)

| Variabile | Default | Descrizione |
|-----------|---------|-------------|
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | db locale di compose | Connessione PostgreSQL |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USER` / `MAIL_PASSWORD` | `localhost:1025` | Server SMTP |
| `MAIL_FROM` | `MotoRide <noreply@motoride.local>` | Mittente email |
| `APP_BASE_URL` | `http://localhost:8080` | Usato nei link delle email |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin` / — / vuota | Se la password è valorizzata, crea l'admin |
| `APP_USER_AGENT` | `MotoRideBot/0.1 (...)` | User-Agent verso i servizi esterni (richiesto dalle loro policy) |
| `NEWS_FETCH_ENABLED` / `NEWS_FETCH_INTERVAL` | `true` / `PT30M` | Agente notizie |
| `FUEL_IMPORT_ENABLED` | `true` | Import giornaliero prezzi MIMIT (ore 9:30) |
| `VALHALLA_URL` | istanza pubblica FOSSGIS | Servizio di routing |
| `NOMINATIM_URL` | istanza pubblica OSM | Ricerca luoghi |

## Prima di andare in produzione

- **Valhalla, Nominatim e tile OSM**: le istanze pubbliche sono per uso moderato. In produzione servono istanze
  proprie o un provider commerciale (stesse API, basta cambiare l'URL).
- **Più istanze dell'app**: broker STOMP esterno (es. RabbitMQ) al posto di quello in memoria e lock distribuito
  (es. ShedLock) per gli scheduler di notizie e carburante.
- **Email**: aggiungere un link di disiscrizione con un clic (oggi si gestisce dalla pagina Notizie).

## Roadmap

- [x] Autenticazione e profilo utente
- [x] Ricerca utenti e amicizie
- [x] Chat in tempo reale con ricerca amici
- [x] Foto profilo con approvazione admin
- [x] Notizie: agente RSS, topic, notifiche ed email riepilogative
- [x] Road trip: tappe, fermate, alloggi, mappa, indicazioni, costi carburante reali
- [ ] Chat di gruppo per i partecipanti di un road trip (schema già pronto)
- [ ] Fase 2: modulo AI opzionale (classificazione notizie, assistente road trip)
