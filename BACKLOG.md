# Backlog

Prioritäten:

| | |
|---|---|
| **P0** | zwingend für den MVP |
| **P1** | wichtig, kurz nach dem MVP |
| **P2** | später |
| **P3** | optional, nur wenn es sich anbietet |

Erledigte Punkte bleiben abgehakt stehen, damit der Weg nachvollziehbar
bleibt.

---

## MVP

- [x] **P0** WordPress-Verbindung herstellen und prüfen (`/wp-json/`)
- [x] **P0** Authentifizierung über Application Passwords
- [x] **P0** Autorisierungs-Flow im Browser mit Rücksprung per Deep Link
- [x] **P0** Manuelle Eingabe eines Application Passwords als Rückfallebene
- [x] **P0** Verschlüsselte Ablage der Zugangsdaten über den Android Keystore
- [x] **P0** Kommentare abrufen (`context=edit`)
- [x] **P0** Kommentarübersicht mit Autor, Zeit, Text, Beitrag, Status
- [x] **P0** Kommentarfilter: Alle, Offen, Genehmigt, Spam, Papierkorb
- [x] **P0** Paginierung über `X-WP-TotalPages`
- [x] **P0** Pull-to-Refresh
- [x] **P0** Kommentar genehmigen
- [x] **P0** Kommentar zurückstellen
- [x] **P0** Kommentar als Spam markieren
- [x] **P0** Kommentar in den Papierkorb verschieben
- [x] **P0** Kommentar endgültig löschen, mit Rückfrage
- [x] **P0** Antworten schreiben und veröffentlichen
- [x] **P0** Detailansicht
- [x] **P0** Fehlerbehandlung für alle geforderten Fälle
- [x] **P0** Rückgängig-Funktion für Moderationsaktionen
- [x] **P1** Kommentartext bearbeiten

---

## Benachrichtigungen

- [x] **P0** Erkennung neuer Kommentare im Hintergrund (WorkManager)
- [x] **P0** Benachrichtigung bei neuen Kommentaren
- [x] **P0** Deep Link aus der Benachrichtigung in die Detailansicht
- [x] **P0** Getrennte Benachrichtigungskanäle
- [x] **P0** Keine Doppelbenachrichtigung für denselben Kommentar
- [x] **P0** Laufzeitberechtigung `POST_NOTIFICATIONS` ab Android 13
- [x] **P1** Einstellbares Prüfintervall
- [x] **P1** Verweis in die Android-Systemeinstellungen
- [x] **P1** Dauerhafter Hinweis bei abgelehnten Zugangsdaten

### Offen

- [x] **P2** Moderationsaktionen direkt aus der Benachrichtigung
      *Technische Frage: Eine Aktion aus der Benachrichtigung braucht einen
      BroadcastReceiver, der ohne sichtbare App schreibend auf die API
      zugreift. Zu klären ist, wie Fehler dort sichtbar gemacht werden –
      eine stillschweigend fehlgeschlagene Moderation wäre schlimmer als gar
      keine Schnellaktion.*
      *Beantwortet: Der Receiver reicht nur an eine beschleunigte
      WorkManager-Arbeit weiter, die dieselben Use Cases nutzt wie die
      Oberfläche. Scheitert sie, wird nicht still wiederholt – eine später
      nachgeholte Moderation könnte eine inzwischen im Web getroffene
      Entscheidung überschreiben. Die Benachrichtigung bleibt stehen und nennt
      den Grund, eine gescheiterte Antwort steht mit ihrem Text darin.*
- [x] **P2** Echtes Push über FCM als zusätzliche `NewCommentSource`
      *Technische Frage: Ein Versandweg von WordPress zu FCM benötigt einen
      Dienstkontoschlüssel auf dem WordPress-Server und eine
      Geräteregistrierung im Plugin. Damit entstünden Metadaten bei Google
      und eine Abhängigkeit von den Play Services. Die Abwägung steht in
      `docs/architecture.md`; die Schnittstelle ist vorbereitet.*
      *Ersetzt: umgesetzt als Sofortmeldung über UnifiedPush, ohne Google;
      FCM entfällt. Das Plugin (ab 1.5) schickt einen inhaltslosen Weckruf an
      die hinterlegte Adresse, die App prüft daraufhin sofort. Eine eigene
      `NewCommentSource` war dafür nicht nötig.*
- [x] **P2** Benachrichtigungen räumen sich auf
      *In der App moderiert, beantwortet oder geöffnet: Die Meldung
      verschwindet sofort. Im Web moderiert: Die Hintergrundprüfung gleicht
      offene Meldungen mit einer Anfrage (`include`, `status=any`) ab. Was
      freigegeben gemeldet wurde, bleibt stehen – es wartet womöglich noch
      auf eine Antwort.*
- [x] **P2** Blog-Symbol in Benachrichtigungen
      *Aus demselben Bildspeicher wie in der Kopfleiste; es stammt vom eigenen
      Blog, eine Verbindung zu Dritten entsteht nicht.*
- [x] **P3** Benachrichtigung auch für automatisch genehmigte Kommentare
      *Umgesetzt und zur Voreinstellung gemacht: Auf Blogs, die automatisch
      freischalten, kam zuvor nie eine Benachrichtigung an. Ein Schalter
      beschränkt die Meldung wieder auf die Moderationswarteschlange.*

---

## UX

- [x] **P0** Dark Mode und heller Modus nach Systemeinstellung
- [x] **P1** Dynamic Color ab Android 12
- [x] **P0** Ladezustände
- [x] **P0** Leerzustände mit Erklärung statt leerer Fläche
- [x] **P0** Fehlerzustände mit Wiederholmöglichkeit
- [x] **P0** Sichtbare Unterscheidung zwischen Cache und frischem Stand
- [x] **P1** Statuskennzeichnung mit Symbol und Text, nicht nur über Farbe
- [x] **P1** Deutsche und englische Oberfläche

### Offen

- [x] **P1** Durchgang mit TalkBack und Korrektur der Vorlesereihenfolge
      *Die Vorlesereihenfolge war in Ordnung: Ein Abzug mit `uiautomator`
      legte zunächst nahe, die Kopfleiste komme zuletzt – der zeigt aber nur
      die Baumstruktur. Compose teilt die Reihenfolge über `traversalBefore`
      mit, und dort stand nichts: Dann sortiert der Bildschirmleser selbst
      nach der Lage auf dem Bildschirm, und die Kopfleiste steht oben. Eine
      vorschnell eingebaute `traversalIndex`-Korrektur wurde deshalb wieder
      entfernt. Geblieben sind zwei echte Funde: Beschriftung und Wert wurden
      getrennt vorgelesen, und die Filtermarken lasen den Mittelpunkt aus
      „Offen · 3" mit. Ein Test prüft jetzt, dass jede bedienbare Stelle einen
      Namen hat.*
- [x] **P1** Prüfung bei sehr großer Systemschriftgröße
      *Bei 200 % im Emulator durchgegangen. Ein Fund: Die Beschriftungsspalte
      der Detailansicht lag fest bei 64 dp und brach „Beitrag" mitten im Wort
      um. Breiten werden jetzt gemessen. Liste, Einstellungen und „Über diese
      App" hielten stand.*
- [x] **P2** Wischgesten für Genehmigen und Spam in der Liste
      *Die Aktion haengt am abgeschlossenen Wischen, nicht an
      `confirmValueChange` – das wird waehrend einer Geste mehrfach
      aufgerufen und loeste die Moderation doppelt aus.*
- [ ] **P2** Anpassung an große Bildschirme (Liste und Detail nebeneinander)
- [ ] **P3** Haptische Rückmeldung bei Moderationsaktionen

---

## Multi-Blog

Die Datenhaltung war von Anfang an darauf angelegt: Jede Zeile trägt eine
`instanceId`, Zugangsdaten liegen pro Instanz, und der HTTP-Client wird pro
Instanz erzeugt. Die Oberfläche ist nachgezogen.

- [x] **P2** Liste der eingerichteten Instanzen und Hinzufügen weiterer
- [x] **P2** Umschalter zwischen Blogs in der Kopfleiste
      *Erscheint erst ab dem zweiten Blog. Hinzufügen liegt deshalb auch in
      den Einstellungen, damit es mit nur einem Blog erreichbar bleibt.*
- [x] **P2** Eigene Zugangsdaten je Instanz in den Einstellungen sichtbar
      *Als eigener Bildschirm je Blog. Zusammen mit dem Blogunabhängigen auf
      einem Bildschirm wäre bei jeder Zeile offen geblieben, für welchen Blog
      sie gilt.*
- [x] **P2** Benachrichtigungen je Blog getrennt steuerbar
      *Entschieden für eine gemeinsame Arbeit, die alle Instanzen durchläuft:
      Das Gerät wacht einmal auf statt n-mal. Die Fehlerbehandlung geschieht
      dafür von Hand je Instanz – ein Blog mit abgelehnten Zugangsdaten oder
      ohne Verbindung hält die übrigen nicht auf, und wiederholt wird der
      Durchgang erst, nachdem alle abgearbeitet sind.*

### Offen

- [ ] **P3** Kombinierter Posteingang über alle Blogs
      *Technische Frage: Die Sortierung über Instanzgrenzen hinweg erfordert
      eine gemeinsame Abfrage; Paginierung je Instanz und globale Sortierung
      vertragen sich nicht ohne Weiteres.*
- [ ] **P3** Anzahl offener Kommentare im Umschalter auch für Blogs, die noch
      nie geladen wurden
      *Die Zahl kommt heute aus dem Zwischenspeicher; ein ungeladener Blog
      zeigt deshalb keine. Sie vom Server zu holen wäre eine Anfrage je Blog
      beim Öffnen des Umschalters – mit Plugin über den Statusendpunkt
      vertretbar, ohne Plugin nicht.*
- [ ] **P3** Blogs in der Liste umsortieren
      *Heute stehen sie in der Reihenfolge ihrer Einrichtung. Ab etwa vier
      Blogs dürfte das Sortieren nach Wichtigkeit spürbar fehlen.*

---

## Offline

- [x] **P0** Lokaler Cache für Kommentare und Beitragstitel
- [x] **P0** Verständlicher Hinweis statt Netzwerkfehler
- [x] **P0** Schreibende Aktionen ohne Verbindung gesperrt und erklärt

### Offen

- [ ] **P2** Warteschlange für Moderationsaktionen ohne Verbindung
      *Technische Frage: Was passiert, wenn ein Kommentar zwischenzeitlich im
      Web moderiert oder gelöscht wurde? Ohne Konfliktstrategie überschreibt
      die App fremde Entscheidungen. Denkbar ist, den Status beim Absenden
      erneut zu lesen und bei Abweichung nachzufragen statt zu überschreiben.
      Erst mit dieser Strategie ist das Merkmal sinnvoll.*
- [x] **P2** Im Web gelöschte Kommentare verschwinden nicht aus dem
      Zwischenspeicher
      *Gefunden bei den README-Aufnahmen (2026-10-02), behoben: Nach dem
      Laden der ersten Seite prüft die App die älteren zwischengespeicherten
      Einträge desselben Filters mit einer Anfrage (`include`, `status=any`,
      bis zu 100). Was der Blog nicht mehr kennt, fliegt hinaus, der Rest
      übernimmt seinen aktuellen Status. Höchstens alle zehn Minuten je Blog
      und Filter.*
- [ ] **P3** Cache-Obergrenze und Aufräumen alter Kommentare

---

## Moderationshilfen

Alles hier dient einer Frage: Wie komme ich schneller zu einer begründeten
Entscheidung? Die Punkte wurden gegen eine echte WordPress-Installation
geprüft, die Abrufe sind belegt.

Die ursprünglichen Punkte dieses Abschnitts sind umgesetzt. „Spam leeren" und
das Sperren von Absendern nutzen zwei Endpunkte, die das Plugin ab 1.2.0
mitbringt; das Leeren funktioniert auch ohne Plugin, dann mit einer Anfrage je
Kommentar. Die offenen Punkte am Ende sind später hinzugekommen.

- [x] **P1** Autorenkontext in der Detailansicht
      *Zeigt, ob jemand zum ersten Mal kommentiert oder schon bekannt ist -
      die Frage, die bei fast jedem offenen Kommentar zuerst kommt. Die
      Kern-API reicht dafür, ein Plugin ist nicht nötig:*
      `GET /wp/v2/comments?author_email=<adresse>&status=approve&per_page=1`
      *Die Zahl steht in `X-WP-Total`, der Rumpf wird nicht gebraucht. Ein
      Tipp auf den Hinweis könnte die bisherigen Kommentare zeigen.*
      *Datenschutz: Die Adresse holt die App ohnehin mit `context=edit`. Die
      reine Anzahl ist weniger heikel als die Adresse selbst und sollte
      deshalb unabhängig von der Einstellung „E-Mail anzeigen" sichtbar sein.*
- [x] **P1** Spam-Signale kennzeichnen, ohne zu urteilen
      *Rein lokal aus dem bereits zwischengespeicherten HTML, ohne zusätzliche
      Abrufe: Anzahl der Links im Kommentar (die beste Einzelheuristik),
      Erstkommentator, Text identisch zu einem anderen offenen Kommentar.
      Ein Hinweis wie „3 Links" genügt - die App soll kennzeichnen, nicht
      entscheiden.*
- [x] **P2** Absender dauerhaft sperren
      *Adresse oder Domain in WordPress' `disallowed_keys` eintragen, statt
      denselben Absender täglich erneut als Spam zu markieren.*
      *Braucht das Plugin: `wp/v2/settings` gibt die Moderationsoptionen
      nicht heraus - geprüft, die Antwort enthält kein einziges Feld zu
      Kommentarmoderation oder Sperrlisten.*
- [x] **P2** „Spam leeren" und „Papierkorb leeren"
      *Der häufige Sammelfall braucht keine Mehrfachauswahl, sondern einen
      Knopf. Die Kern-API kennt keinen Sammelendpunkt, es würden N
      Einzellöschungen; ein Endpunkt im Plugin macht daraus eine Anfrage.
      Sinnvoller Anlass, das Plugin für etwas zu nutzen, das es wirklich
      besser kann als die Kern-API.*
- [x] **P2** Textbausteine für wiederkehrende Antworten
      *Kein API-Thema, reine lokale Ablage. „Danke für den Hinweis, ist
      korrigiert." tippt man sonst zum zwanzigsten Mal.*
- [x] **P2** „Freigeben und antworten“
      *Gegen ein lokales WordPress geprüft: Ohne Freigabe blieb der
      Elternkommentar offen, und die Antwort war auf der Website unsichtbar.
      Erst freigeben, dann antworten – scheitert die Freigabe, ist nichts
      geschehen, und ein zweiter Versuch erzeugt keine doppelte Antwort.*

### Offen

- [ ] **P2** Antwortentwürfe behalten
- [ ] **P2** Nach einer Aktion zum nächsten offenen Kommentar
- [ ] **P3** Beim Sperren alle bisherigen Kommentare des Absenders als Spam
      markieren

---

## Suche und Filter

- [x] **P1** Kommentarsuche über den `search`-Parameter der API
      *Wirkt innerhalb des gewählten Filters, mit Verzögerung und ab zwei
      Zeichen. Die Treffer landen im Zwischenspeicher und werden von dort
      beobachtet – dadurch wirkt eine Moderation auch in der Trefferliste
      sofort, ohne einen zweiten Weg für Listenzustände.*
- [x] **P2** Filter „Unbeantwortet“
      *Freigegebene Leserkommentare ohne freigegebene Antwort aus dem Team.
      WordPress kennt den Zustand nicht; die App berechnet ihn aus dem
      Zwischenspeicher und holt dazu die Antworten auf die geladenen
      Kommentare nach (`parent=<ids>`). Deshalb ohne Zahl an der Marke und nur
      über die geladenen Seiten. Vorbild ist die WordPress-App.*
- [ ] **P2** Filter nach Beitrag
- [ ] **P2** Filter nach Autor
- [ ] **P3** Lokale Volltextsuche im Cache über Room FTS
      *Technische Frage: Eine lokale Suche findet nur, was zwischengespeichert
      ist. Zu klären ist, wie der Unterschied zur Serversuche sichtbar wird,
      damit niemand ein unvollständiges Ergebnis für vollständig hält.*

---

## Weiteres

- [x] **P2** Kommentarfaden statt rein chronologischer Liste
      *Der Aufbau ist eine reine Funktion über `parentId`; die Kommentare,
      auf die geantwortet wurde, holt das Repository mit einer Anfrage
      (`include`, `status=any`) nach – ohne sie bliebe der Faden im Filter
      „Offen" fast immer leer. Nur eine Stufe weit: der unmittelbare Bezug.
      Wer den ganzen Faden sehen will, öffnet den Kommentar.*
- [ ] **P2** Sammelmoderation mit Mehrfachauswahl
- [x] **P1** Kommentarzahlen je Filter anzeigen - „Offen · 3" statt „Offen"
      *Anders als hier ursprünglich vermerkt hängt das nicht am Plugin: Vier
      Abrufe mit `per_page=1` liefern alle Zahlen aus `X-WP-Total`, ohne
      nennenswertes Datenvolumen. Der Endpunkt `summary` des Plugins macht
      daraus einen Abruf statt vier - eine Verbesserung, keine Voraussetzung.*
- [ ] **P3** Statistik über Kommentaraufkommen und Spam-Anteil
- [ ] **P3** Kommentar im Browser öffnen
- [ ] **P3** Widget mit der Anzahl offener Kommentare

---

## Technische Schulden und Pflege

- [ ] **P1** Kotlin auf 2.4 anheben, sobald KSP dafür vorliegt
      *Nachgesehen am 21.09.2026: Kotlin steht bei 2.4.20, KSP bei 2.3.12 –
      und dessen POM hängt an `kotlin-stdlib` 2.3.20. Eine offizielle Paarung
      für die 2.4-Linie gibt es also weiterhin nicht. Room und Hilt brauchen
      KSP.*
      *Probeweise trotzdem umgestellt: Der Build läuft durch, KSP erzeugt
      Hilt- und Room-Code, 267 Unit-Tests, 8 Gerätetests und der
      Release-Build sind grün. Die Umstellung wurde dennoch zurückgenommen –
      KSP würde die Quellen mit einem Compiler der 2.3-Linie analysieren,
      während übersetzt wird mit 2.4. Heute fällt das nicht auf; ein
      Sprachmerkmal aus 2.4 in einer annotierten Klasse könnte still danebengehen.
      Sobald KSP nachzieht, ist es eine Zeile in `libs.versions.toml`.*
- [x] **P1** Instrumentierungstests auf einem Gerät oder Emulator ausführen
      *Laufen auf einem Pixel-10-Emulator (API 37) und auf einem Gerät.*
- [x] **P2** Room-Migrationen vorbereiten, sobald sich das Schema ändert
      *Mit der Nutzer-ID des Verfassers stand die erste an. Schema 2 ist
      exportiert, die Migration ist registriert und wird auf dem Gerät gegen
      das Schema geprüft – die exportierten JSON-Dateien liegen dafür als
      Assets der Instrumentierungstests bereit, nicht im APK.*
- [ ] **P2** `material-icons-core` ablösen, sobald es einen gepflegten
      Nachfolger gibt
      *Das Paket ist auf 1.7.8 eingefroren.*
- [ ] **P2** Prüfen, ob `android.newDsl` und die Variant-API-Umstellung für
      AGP 10 Anpassungen erfordern
- [ ] **P3** Neue Schnittstellen aus API 37 im Blick behalten
      *Durchgesehen am 21.09.2026: Der Zuwachs betrifft fast ausschließlich
      Bereiche ohne Bezug zu dieser App (App Functions, Health Connect, HPKE,
      Ranging, Photo Picker, serielle Schnittstellen). Naheliegend wären
      allenfalls `Notification.createSemanticStyleAnnotation` und die
      Handoff-Schnittstellen in `Activity`; beides bringt hier derzeit keinen
      erkennbaren Gewinn.*
- [ ] **P3** Signaturkonfiguration und reproduzierbarer Release-Build
- [ ] **P3** Continuous Integration für Build, Lint und Tests
