# Flussi principali

Come collaborano i componenti nelle operazioni più importanti. Per la struttura generale vedi
[architettura.md](architettura.md).

## 1. Registrazione e verifica dell'email

L'account resta disattivato finché l'utente non apre il link ricevuto via email. L'email parte solo dopo
il commit della transazione e in un thread separato, quindi la registrazione non attende il server SMTP.

```mermaid
sequenceDiagram
    actor U as Utente
    participant AC as AuthController
    participant RS as RegistrationService
    participant DB as PostgreSQL
    participant L as VerificationMailListener
    participant M as MailService / SMTP

    U->>AC: POST /register (username, email, password)
    AC->>AC: validazione del form
    AC->>RS: register(form)
    RS->>DB: username ed email liberi?
    RS->>DB: INSERT utente (enabled = false, password BCrypt)
    RS->>DB: INSERT token casuale (scade dopo 24 h)
    RS-->>AC: utente creato
    AC-->>U: redirect a /register/done
    Note over RS,L: dopo il commit, in modo asincrono
    L->>M: email con il link /verify?token=...
    U->>AC: GET /verify?token=...
    AC->>RS: verify(token)
    RS->>DB: token valido e non scaduto?
    RS->>DB: enabled = true, token eliminato
    AC-->>U: "Account attivato"
```

## 2. Amicizia

```mermaid
stateDiagram-v2
    [*] --> Nessuna
    Nessuna --> InAttesa: A invia la richiesta
    InAttesa --> Amici: B accetta
    InAttesa --> Amici: anche B invia una richiesta ad A
    InAttesa --> Nessuna: B rifiuta o A annulla
    Amici --> Nessuna: uno dei due rimuove l'amicizia
```

Rifiuto, annullamento e rimozione cancellano la riga, così in seguito si può inviare una nuova richiesta.
Un indice univoco sulla coppia di utenti impedisce richieste doppie, anche contemporanee.

## 3. Messaggio in chat

Il messaggio si invia con una normale richiesta HTTP. Il WebSocket serve solo a consegnarlo agli altri
browser, dopo che il database l'ha salvato definitivamente.

```mermaid
sequenceDiagram
    actor A as Mittente (browser)
    participant CC as ChatController
    participant CS as ChatService
    participant DB as PostgreSQL
    participant PL as ChatPushListener
    participant BR as Broker STOMP
    actor B as Destinatario (browser)

    A->>CC: POST /chat/{id}/messages (testo + token CSRF)
    CC->>CS: send(utente, conversazione, testo)
    CS->>DB: l'utente partecipa alla conversazione?
    CS->>DB: siete ancora amici?
    CS->>DB: INSERT messaggio, aggiorna ultimo letto del mittente
    CS-->>CC: messaggio salvato
    CC-->>A: 201 Created con il messaggio in JSON
    Note over CS,PL: evento consegnato dopo il commit
    PL->>BR: invia a /user/queue/messages di ogni partecipante
    BR-->>B: messaggio in tempo reale
    BR-->>A: stesso messaggio (scartato perché già mostrato)
    B->>CC: POST /chat/{id}/read
    Note over B: se B è su un'altra pagina<br/>si aggiorna solo il contatore in navbar
```

## 4. Foto profilo

```mermaid
stateDiagram-v2
    [*] --> InAttesa: upload, immagine validata e ri-codificata
    InAttesa --> Approvata: admin approva
    InAttesa --> Rifiutata: admin rifiuta, con motivo facoltativo
    InAttesa --> [*]: nuovo upload (sostituisce quella in attesa)
    Rifiutata --> [*]: nuovo upload
    Approvata --> [*]: sostituita da una nuova approvata o rimossa dall'utente
```

- Durante l'attesa la foto è visibile solo al proprietario e agli admin; quella approvata in precedenza resta in uso.
- Approvazione e rifiuto generano una notifica all'utente, consegnata in tempo reale.
- Il file originale non viene mai salvato: si conserva solo il JPEG 400×400 prodotto dal server.

## 5. Agente notizie

```mermaid
flowchart TD
    START(["Scheduler: ogni 30 minuti<br/>oppure 'Leggi ora' dall'admin"]) --> LOCK{"Un'altra esecuzione<br/>è in corso?"}
    LOCK -- sì --> STOP(["Salta questo giro"])
    LOCK -- no --> SRC["Per ogni fonte attiva"]
    SRC --> FETCH["Scarica il feed<br/>timeout 20 s, max 5 MB"]
    FETCH -->|errore| ERR["Fonte marcata in errore<br/>le altre proseguono"]
    FETCH -->|ok| PARSE["Interpreta RSS/Atom<br/>HTML ridotto a testo<br/>URL normalizzato"]
    PARSE --> CLASS["Classifica ogni articolo<br/>3 punti parola chiave nel titolo<br/>1 punto nel riassunto<br/>spareggio: topic della fonte"]
    CLASS --> SAVE["INSERT se l'URL è nuovo"]
    SAVE --> FIRST{"Primo import<br/>della fonte?"}
    FIRST -- sì --> NEXT["Nessuna notifica"]
    FIRST -- no --> RECENT["Da notificare:<br/>articoli nuovi<br/>delle ultime 48 ore"]
    ERR --> MORE
    NEXT --> MORE{"Altre fonti?"}
    RECENT --> MORE
    MORE -- sì --> SRC
    MORE -- no --> GROUP["Raggruppa per topic<br/>e per utente iscritto"]
    GROUP --> NOTIF["Una notifica in-app<br/>per topic"]
    GROUP --> MAIL["Una sola email riepilogativa<br/>solo per i topic con email attiva"]
```

## 6. Pianificazione di una tappa

Il calcolo del percorso è una chiamata esterna che può durare decine di secondi, quindi avviene **fuori**
dalla transazione: il database resta impegnato solo per i controlli e il salvataggio.

```mermaid
sequenceDiagram
    actor U as Utente (browser)
    participant GC as GeoController
    participant NOM as Nominatim
    participant TC as TripController
    participant TS as TripService
    participant DB as PostgreSQL
    participant RS as RoutingService
    participant VAL as Valhalla

    U->>GC: GET /api/geocode?q=Siena
    GC->>NOM: ricerca (max 1 richiesta al secondo, con cache)
    NOM-->>U: luoghi con coordinate
    Note over U: le fermate si possono anche<br/>scegliere cliccando sulla mappa
    U->>TC: POST /trips/{id}/stages
    TC->>TS: saveStage(form)
    TS->>DB: transazione breve: l'utente può modificare il viaggio?
    TS->>RS: route(partenza, fermate, arrivo, evita autostrade)
    RS->>VAL: profilo motorcycle, use_highways = 0
    alt Valhalla risponde
        VAL-->>RS: percorso, km, durata, indicazioni in italiano
    else non risponde o errore
        RS->>RS: stima in linea d'aria
    end
    TS->>DB: nuova transazione: salva tappa, fermate e percorso
    TC-->>U: redirect alla pagina della tappa
```

### Calcolo dei costi (alla visualizzazione)

I costi non vengono salvati: si calcolano ogni volta per **chi sta guardando**, con il consumo della sua
moto e i prezzi aggiornati all'ultimo import.

```mermaid
flowchart LR
    KM["Km della tappa<br/>da Valhalla"] --> L["Litri = km ÷ consumo"]
    CONS["Consumo km/l<br/>dal profilo, default 20"] --> L
    PTS["Partenza e arrivo"] --> PRICE["Prezzo medio benzina self<br/>dei distributori entro 15 km<br/>altrimenti media nazionale"]
    MIMIT[("fuel_stations<br/>open data MIMIT")] --> PRICE
    L --> COST["Costo = litri × prezzo"]
    PRICE --> COST
    TANK["Serbatoio in litri"] --> RANGE["Autonomia =<br/>serbatoio × consumo × 0,85"]
    CONS --> RANGE
    RANGE --> REF["Rifornimenti necessari<br/>partendo col pieno"]
    KM --> REF
    COST --> TOT["Totale viaggio =<br/>carburante + alloggi"]
```

## 7. Permessi in un road trip

| Azione | Organizzatore | Editor | Sola lettura | Non partecipante |
|---|:---:|:---:|:---:|:---:|
| Vedere viaggio e tappe | ✔ | ✔ | ✔ | 404 |
| Aggiungere, modificare, riordinare tappe | ✔ | ✔ | 403 | 404 |
| Modificare o eliminare il viaggio | ✔ | 403 | 403 | 404 |
| Invitare amici e cambiare ruoli | ✔ | 403 | 403 | 404 |
| Lasciare il viaggio | — | ✔ | ✔ | — |

Si possono invitare solo i propri amici; l'invitato riceve una notifica in tempo reale.
