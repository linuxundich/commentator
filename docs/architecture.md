# Architektur

Dieses Dokument hält die Analyse aus Phase 1 und alle daraus folgenden
Technologie- und Architekturentscheidungen fest. Es ist die Referenz dafür,
*warum* etwas so gebaut ist, wie es gebaut ist.

Stand der Analyse: **2026-09-20**

---

## 1. Ziel und Rahmen

Commentator ist ein spezialisiertes Werkzeug zur täglichen Moderation von
WordPress-Kommentaren – kein WordPress-Reader und kein Admin-Ersatz.

Rahmenbedingungen, die die Architektur prägen:

* Die Architektur trägt mehrere WordPress-Instanzen. Version 1 bediente nur
  einen Blog, war aber von Anfang an darauf angelegt – der Mehrfachbetrieb kam
  später ohne Umbau der Datenschicht.
* Es gibt **keinen eigenen Server**. Alles, was die App kann, muss sie
  gegen die WordPress-REST-API oder lokal erledigen.
* Datensparsamkeit und Nachvollziehbarkeit der Netzwerkverbindungen haben
  Vorrang vor Komfortfunktionen.

---

## 2. Ermittelte Toolchain (geprüft am 2026-09-20)

Alle Versionen wurden direkt aus den Maven-Metadaten von Google Maven und
Maven Central sowie aus den offiziellen Release Notes ermittelt, nicht aus
Tutorials.

| Komponente | Gewählt | Aktuellste gefundene | Begründung der Wahl |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | 9.5.0-alpha06 | Neueste stabile Version; Alphas sind für ein Produktivprojekt ungeeignet. |
| Gradle | 9.7.1 | 9.7.1 | AGP 9.4 verlangt min. 9.6.0; 9.7.1 ist aktueller Stable-Stand. |
| JDK (Build) | 21 | 26 | AGP 9.4 verlangt min. JDK 17, Gradle 9.7 unterstützt 17–26. JDK 21 ist der breit erprobte LTS-Mittelweg. |
| Kotlin | 2.3.21 | 2.4.20 | **Bewusst nicht die neueste Version:** KSP liegt aktuell nur bis zur 2.3.x-Linie vor (KSP 2.3.12 ist gegen kotlin-stdlib 2.3.20 gebaut). Room und Hilt brauchen KSP. Sobald KSP für Kotlin 2.4 erscheint, kann angehoben werden. |
| KSP | 2.3.12 | 2.3.12 | Ersetzt kapt vollständig; kapt wird nicht verwendet. |
| compileSdk / targetSdk | 37 | 37 | API 37 ist die höchste von AGP 9.4 unterstützte Ebene. Das SDK-Paket heißt seit den SDK-Minor-Releases `platforms;android-37.0`. |
| minSdk | 26 | – | Android 8.0. Notification Channels sind ab 26 Pflicht und ohne Kompatibilitätspfade nutzbar; deckt praktisch den gesamten aktiven Gerätebestand ab. |
| Build Tools | 37.0.0 | 37.0.0 | AGP 9.4 verlangt min. 36.0.0. |
| Compose BOM | 2026.09.00 | 2026.09.00 | Hält alle Compose-Artefakte konsistent. |
| compose-material3 | 1.5.0-alpha29 | 1.5.0-alpha29 | **Einzige bewusste Ausnahme von der Stable-Regel**, und das einzige Compose-Artefakt mit eigener Version. Begründung unten. |
| Navigation Compose | 2.10.1 | 2.10.1 | Typsichere Navigation über `@Serializable`-Routen. |
| Lifecycle | 2.11.0 | 2.12.0-alpha03 | Neueste stabile Version. |
| Hilt | 2.60.1 | 2.60.1 | In den Vorgaben ausdrücklich bevorzugt; mit KSP betrieben. |
| Retrofit | 3.0.0 | 3.0.0 | Mit `converter-kotlinx-serialization` in derselben Version. |
| OkHttp | 5.5.0 | 5.5.0 | Von Retrofit 3 ohnehin vorausgesetzt; liefert MockWebServer für Tests. |
| kotlinx.serialization | 1.11.0 | 1.12.0-RC | JSON ohne Reflection und ohne Code-Generator zur Laufzeit. |
| Coroutines | 1.11.0 | 1.11.0 | – |
| Room | 2.8.5 | 2.8.5 | Lokaler Cache, siehe Abschnitt 8. |
| WorkManager | 2.11.2 | 2.12.0-rc01 | Periodische Hintergrundprüfung auf neue Kommentare. |
| DataStore (Preferences) | 1.2.1 | 1.3.0-alpha11 | Ersatz für SharedPreferences, asynchron. |
| Coil | 3.6.3 | 3.6.3 | Avatare. Siehe Abschnitt 4 zur Drittanbieter-Abwägung. |
| material-icons-core | 1.7.8 | 1.7.8 | Auf dieser Version eingefroren. Material 3 liefert die Symbole nicht mehr mit, deshalb ausdrücklich aufgenommen. |
| Robolectric | 4.17 | 4.17 | Compose-Oberflächentests ohne Gerät. |

### Die Ausnahme: material3 als Alpha

Für das Android Gradle Plugin steht oben „Alphas sind für ein Produktivprojekt
ungeeignet". Bei `compose-material3` wird davon abgewichen, und das soll
nachvollziehbar bleiben.

Material 3 Expressive ist in der stabilen Linie **1.4.0 nicht benutzbar**.
Vorhanden ist es dort: `MaterialExpressiveTheme`, `MotionScheme`,
`MaterialTheme.motionScheme` und sogar `ExperimentalMaterial3ExpressiveApi`
liegen im Artefakt. Sie sind aber sämtlich Kotlin-`internal` und aus App-Code
nicht aufrufbar. Ein Blick mit `javap` täuscht hier: Kotlins `internal` ist im
Bytecode `public` und nur in den Metadaten markiert - erst der Compiler sagt
es. Die Komponenten `ButtonGroup`, `ToggleButton`, `LoadingIndicator`,
`MaterialShapes` und `FloatingToolbar` fehlen in 1.4.0 vollständig.

Ohne die Alpha wäre also nur die Hälfte erreichbar gewesen: Form und Schrift
öffentlich, die Bewegung nur als Nachbau mit von Hand gesetzten Federn - und
die mitgelieferten Komponenten wären beim Standardschema geblieben, weil jede
Material-Komponente ihr Bewegungsschema aus dem Theme liest und nicht aus dem
Aufrufer.

Was die Entscheidung kostet:

* Die Alpha-Linie ändert Signaturen zwischen den Fassungen. `MaterialShapes`
  und `LoadingIndicator` wurden in der 1.5.0-Reihe schon einmal zurück auf
  experimentell gestuft. Ein Anheben ist deshalb nichts, was nebenbei
  mitläuft.
* Die BOM wird für genau ein Artefakt überstimmt. Die übrigen
  Compose-Artefakte bleiben konsistent aus der BOM; `material3` hängt an einer
  eigenen Version in `libs.versions.toml`.
* Alles Expressive steht hinter `@OptIn(ExperimentalMaterial3ExpressiveApi)`.

Sobald 1.5.0 stabil ist, entfällt der Sondereintrag und `material3` kommt
wieder aus der BOM.

### Bewusst *nicht* verwendet

* **androidx.security:security-crypto / EncryptedSharedPreferences** –
  seit 1.1.0-beta01 (Juni 2025) vollständig deprecated. Google verweist
  ausdrücklich auf die direkte Nutzung des Android Keystore. Wir folgen dem
  (siehe Abschnitt 6) und nehmen keine veraltete Abhängigkeit auf.
* **kapt** – durch KSP ersetzt, deutlich schneller und offiziell empfohlen.
* **XML-Layouts, Fragments, AppCompat-Activities** – nicht nötig, die App ist
  vollständig Compose-basiert mit einer einzigen `ComponentActivity`.
* **Accompanist** – die früher dort beheimateten Funktionen (Pull-to-Refresh,
  SwipeRefresh, Permissions-Helfer) sind in Material 3 bzw. der Plattform
  angekommen. Wir verwenden `PullToRefreshBox` aus Material 3.
* **material-icons-extended** – bringt mehrere tausend Vektoren mit, von denen
  die App eine Handvoll bräuchte. Stattdessen wird das Kernset verwendet und
  die Symbolauswahl daran angepasst.
* **Mocking-Bibliotheken** (Mockito, MockK) – die Tests verwenden
  handgeschriebene Doppelgänger. Das ist bei dieser Codegröße übersichtlicher
  und erzwingt nebenbei Schnittstellen, die auch der Produktivcode gut
  gebrauchen kann.
* **Firebase / Google Play Services** – siehe Abschnitt 9. Die App enthält
  keine proprietären Cloud-SDKs.
* **Analytics-, Crash- oder Telemetrie-SDKs** – keine.

### Drittanbieter-Abhängigkeiten und ihre Rechtfertigung

Die Vorgabe lautet: vor jeder Bibliothek prüfen, ob es eine offizielle
Jetpack-Lösung gibt. Ergebnis:

| Abhängigkeit | Offizielle Alternative? | Entscheidung |
|---|---|---|
| Retrofit + OkHttp | `HttpURLConnection`, Ktor (JetBrains, nicht Google) | Retrofit. Jetpack hat keinen HTTP-Client; Retrofit/OkHttp ist der De-facto-Standard, wird in Googles eigenen Samples verwendet und ist in den Vorgaben genannt. |
| kotlinx.serialization | Keine Jetpack-JSON-Lösung | kotlinx.serialization, von JetBrains, Compiler-Plugin statt Reflection. |
| Hilt | Manuelle DI | Hilt – in den Vorgaben bevorzugt und von Google gepflegt. |
| Coil | Kein Jetpack-Image-Loader | Coil 3. Alternative wäre eigenes Laden von Gravatar-Bildern inkl. Cache – unnötige Eigenentwicklung. Avatare sind zudem per Einstellung abschaltbar. |
| UnifiedPush Connector (`org.unifiedpush.android:connector` 3.3.5), bringt Google Tink transitiv mit | FCM (proprietär, siehe Abschnitt 9) | UnifiedPush. Offener Standard für Push ohne Google; der Connector übernimmt Anmeldung bei der UnifiedPush-App auf dem Telefon und Empfang der Nachrichten. Tink braucht er für die Web-Push-Verschlüsselung, die hier nicht genutzt wird – die Nachricht ist inhaltslos. Die Funktion ist optional und je Blog abschaltbar. |

---

## 3. Schichten

```
UI (Compose, Material 3)
 │   Screens, Komponenten, Navigation. Kennt nur UI-State und Events.
 ▼
ViewModel
 │   Hält StateFlow<UiState>, übersetzt Events in Use-Case-Aufrufe.
 ▼
Use Cases / Repository (domain)
 │   Geschäftsregeln, Statuswechsel, Cache-/Netz-Koordination.
 ▼
Data
 │   WordPressApiClient (Retrofit), Room-Cache, CredentialStore
 ▼
WordPress REST API
```

Verbindliche Regeln:

1. **Die UI kennt keine DTOs.** Retrofit-DTOs leben in `data.remote.dto` und
   werden dort in Domain-Modelle gemappt. Ändert WordPress ein Feld, ist nur
   das Mapping betroffen – die Vorgabe „API-Änderungen von der UI entkoppeln“.
2. **Die UI kennt keine HTTP-Fehler.** Alle Fehler werden früh in eine
   geschlossene Fehlerhierarchie (`AppError`) übersetzt, die die UI in Texte
   auflöst.
3. **Repositories geben `Flow` aus dem Cache aus**, nicht das Ergebnis eines
   einzelnen Netzwerkaufrufs. Dadurch aktualisiert sich die UI nach einer
   Moderationsaktion automatisch.
4. **Kein Domain-Code kennt Android-Klassen.** `domain` ist reines Kotlin und
   damit ohne Emulator testbar.

### Paketstruktur

```
de.christophlangner.commentator
├── core/            Ergebnis- und Fehlertypen, Zeit-/HTML-Hilfen
├── domain/
│   ├── model/       Comment, CommentStatus, WordPressInstance, ...
│   ├── repository/  Interfaces (die Implementierung liegt in data/)
│   └── usecase/     ModerateCommentUseCase, ReplyToCommentUseCase, ...
├── data/
│   ├── remote/      Retrofit-Service, DTOs, Interceptors, Fehler-Mapping
│   ├── local/       Room (Entities, DAOs, DB), DataStore
│   ├── account/     Keystore-Krypto, CredentialStore, InstanceStore
│   └── repository/  Implementierungen
├── notification/    Channels, WorkManager-Worker, Deep Links,
│                    Aktionen in der Benachrichtigung
├── push/            Sofortmeldung über UnifiedPush (InstantPush, PushSetup,
│                    CommentatorPushService)
├── ui/
│   ├── theme/       Material-3-Theme, Dynamic Color, Dark Mode
│   ├── setup/       Einrichtung/Anmeldung
│   ├── inbox/       Kommentarliste + Filter
│   ├── detail/      Kommentardetail + Antwort
│   ├── settings/    Einstellungen
│   └── navigation/  Typsichere Routen, Deep-Link-Behandlung
└── di/              Hilt-Module
```

---

## 4. Mehrere WordPress-Instanzen

Die App verwaltet mehrere Blogs. Die Instanz ist dabei nie implizit.

```kotlin
data class WordPressInstance(
    val id: String,          // stabile lokale UUID, nicht die Blog-URL
    val name: String,
    val siteUrl: HttpUrl,    // https erzwungen
    val username: String,
)
```

Konsequenzen, die heute schon umgesetzt sind:

* Jede Room-Zeile trägt eine `instanceId`; der Primärschlüssel der
  Kommentartabelle ist `(instanceId, id)`. Ein zweiter Blog fügt nur Zeilen
  hinzu, er erzwingt keine Migration.
* Zugangsdaten liegen **pro Instanz** unter einem eigenen Schlüssel im
  CredentialStore, nicht in einem globalen „das Passwort“-Eintrag.
* Der Retrofit-Client wird nicht als Singleton mit fester Basis-URL gebaut,
  sondern von einer `WordPressClientFactory` pro Instanz erzeugt und
  gecacht. Die Basis-URL kommt nie aus einer Konstante.
* Repositories nehmen die `instanceId` als Parameter. Es gibt keine
  „aktuelle Instanz“ tief in der Datenschicht – die Auswahl passiert einmal
  oben (`ActiveInstanceProvider`) und wird nach unten durchgereicht.
* Benachrichtigungen führen den Zustand „zuletzt gesehener Kommentar“
  pro Instanz.

Dass diese Entscheidungen von Anfang an so fielen, hat sich ausgezahlt: Der
Mehrfachbetrieb kam ohne Datenmigration und ohne Eingriff in die Datenschicht.

Darauf aufbauend gilt:

* Einstellungen sind geteilt in **blogübergreifend** (`AppSettings`:
  Hauptschalter für Benachrichtigungen, Prüftakt, Avatare, E-Mail-Anzeige,
  Fadenansicht) und **je Blog** (`SiteSettings`: ob dieser Blog meldet,
  worüber, welche Rollen als Team gelten, deren Farben). Wären die Rollen
  global, würde eine Redaktion auf einem Blog die Farben eines anderen
  mitverstellen. Textbausteine liegen ebenfalls je Blog.
* Blogbezogene Werte liegen in DataStore unter einem Schlüssel mit angehängter
  Kennung. Fehlt er, greift der frühere blogübergreifende Schlüssel: Als es
  nur einen Blog gab, galten diese Werte für ihn, und dort sollen sie bleiben.
  Geschrieben wird immer der blogbezogene Schlüssel; der alte wird nur noch
  gelesen. Damit brauchte auch die Einstellungsschicht keine Migration.
* Die Hintergrundprüfung ist **eine** WorkManager-Arbeit, die alle Blogs
  durchläuft. Das Gerät wacht einmal auf statt n-mal, und die Abfragen laufen
  ohnehin über dieselbe Verbindung. Der Preis ist, dass die Fehlerbehandlung
  von Hand je Blog geschieht: Ein Blog mit abgelehnten Zugangsdaten bekommt
  seinen Hinweis und blockiert die übrigen nicht; ein netzbedingter Fehler
  lässt den ganzen Durchgang wiederholen, aber erst, nachdem alle Blogs
  abgearbeitet sind. Ein früher Abbruch hieße, dass ein unerreichbarer Blog
  die Benachrichtigungen aller anderen aufhält.
* Benachrichtigungen tragen den Blog als **Marke** (`tag`) neben der aus der
  Kommentar-ID abgeleiteten Zahl. Kommentar-IDs sind nur innerhalb eines Blogs
  eindeutig; die Kennung des Blogs über einen Hashwert in die Zahl zu falten
  hätte Zusammenstöße nur unwahrscheinlicher gemacht, nicht unmöglich – und
  ein Zusammenstoß hieße, dass ein Blog die Meldung eines anderen überschreibt.
* Eine erneute Anmeldung bei einer bereits eingerichteten Adresse aktualisiert
  den vorhandenen Eintrag und behält seine `id`. Daran hängen Zwischenspeicher
  und Meldestand: Ein zweiter Eintrag für denselben Blog würde jeden
  vorhandenen Kommentar noch einmal als neu melden.

Was noch fehlt – der kombinierte Posteingang über alle Blogs – steht in
`BACKLOG.md`. Die Sortierung über Instanzgrenzen hinweg ist dort die offene
Frage, nicht die Datenhaltung.

---

## 5. WordPress-Anbindung

Genutzt werden ausschließlich offizielle Endpunkte:

| Zweck | Endpunkt |
|---|---|
| API-Erkennung, Auth-Fähigkeiten | `GET /wp-json/` |
| Angemeldeten Benutzer prüfen | `GET /wp-json/wp/v2/users/me` |
| Kommentare auflisten | `GET /wp-json/wp/v2/comments` |
| Einzelner Kommentar | `GET /wp-json/wp/v2/comments/<id>` |
| Status ändern / bearbeiten | `POST /wp-json/wp/v2/comments/<id>` |
| Antworten | `POST /wp-json/wp/v2/comments` |
| Papierkorb / löschen | `DELETE /wp-json/wp/v2/comments/<id>` |
| Beitragstitel | `GET /wp-json/wp/v2/posts?include=…` |

Details, Parameter und Fehlerfälle stehen in [`api.md`](api.md).

Wichtige Eigenheiten, die die Implementierung berücksichtigt:

* Moderation erfordert `context=edit`. Ohne `edit`-Kontext liefert WordPress
  weder `status` noch `author_email` zuverlässig.
* Nicht-öffentliche Status (`hold`, `spam`, `trash`) sind nur mit
  `moderate_comments`-Berechtigung sichtbar.
* Paginierung läuft über die Header `X-WP-Total` und `X-WP-TotalPages`, nicht
  über den Body.
* `DELETE` verschiebt in den Papierkorb; `DELETE?force=true` löscht endgültig.
  Ist der Papierkorb in WordPress deaktiviert, löscht schon der erste Aufruf
  endgültig – die App weist darauf hin.
* Kommentarinhalte kommen als gerendertes HTML (`content.rendered`). Die App
  rendert das nicht in einem WebView, sondern wandelt es in eine
  `AnnotatedString` um (kein JavaScript, keine Remote-Inhalte).

---

## 6. Authentifizierung und Umgang mit Zugangsdaten

### Verfahren: Application Passwords (WordPress ab 5.6)

Geprüfte Alternativen:

| Verfahren | Bewertung |
|---|---|
| Benutzername + echtes Kennwort (Basic Auth) | **Ausgeschlossen.** Die Vorgabe verbietet es, und ein Kontokennwort lässt sich nicht einzeln widerrufen. |
| Cookie + Nonce | Nur für Code gedacht, der innerhalb von WordPress läuft. Für native Apps unbrauchbar. |
| OAuth 2.0 | Kein Kernbestandteil von WordPress; benötigt ein Plugin und eine Client-Registrierung. Zusätzliche Serverabhängigkeit ohne Mehrwert. |
| JWT-Plugins | Drittanbieter-Plugins unterschiedlicher Qualität, kein Kernstandard. |
| **Application Passwords** | **Gewählt.** Kernbestandteil seit 5.6, einzeln widerrufbar, pro Anwendung getrennt, ohne Zusatz-Plugin, mit Autorisierungs-Flow für native Apps. |

### Ablauf

1. Die App fragt `GET /wp-json/` ab und liest
   `authentication['application-passwords'].endpoints.authorization`.
   Fehlt der Schlüssel, sind Application Passwords nicht verfügbar
   (typischerweise fehlendes HTTPS) – die App erklärt das konkret.
2. Die App öffnet diesen Endpunkt in einem **Custom Tab**, mit
   `app_name`, `app_id` (feste UUID der Anwendung) und
   `success_url=commentator://auth-callback`.
   WordPress erlaubt an dieser Stelle ausdrücklich App-Schemata; nur
   `http://` wird abgelehnt.
3. Der Benutzer meldet sich in seinem Browser bei WordPress an und bestätigt.
   WordPress leitet auf `success_url` zurück und hängt `site_url`,
   `user_login` und `password` an.
4. Die App nimmt das Application Password entgegen, verifiziert es sofort mit
   `GET /wp/v2/users/me?context=edit` und speichert es verschlüsselt.

Das eigentliche Kontokennwort des Benutzers wird dabei **nie** an die App
weitergereicht – es wird ausschließlich im Browser eingegeben.

Als Rückfallebene gibt es die manuelle Eingabe eines im WP-Profil erzeugten
Application Passwords. Das ist nötig für Installationen, bei denen der
Autorisierungs-Endpunkt deaktiviert ist.

### Speicherung

Da `EncryptedSharedPreferences` deprecated ist, verschlüsselt die App selbst:

* Ein Schlüssel `commentator_credentials_v1` im **Android Keystore**
  (`AES/GCM/NoPadding`, 256 Bit, `setUserAuthenticationRequired(false)`,
  `setRandomizedEncryptionRequired(true)`), erzeugt beim ersten Bedarf.
  Der Schlüssel verlässt den Keystore nie; auf Geräten mit StrongBox/TEE
  liegt er in Hardware.
* Das Application Password wird mit einem zufälligen IV verschlüsselt,
  `IV || Ciphertext` Base64-kodiert und in DataStore abgelegt.
* Schlüssel für die Ablage: `credential_<instanceId>` – damit ist die
  Mehr-Instanzen-Fähigkeit auch hier gegeben.
* Beim Abmelden wird sowohl der DataStore-Eintrag als auch der
  Keystore-Schlüssel gelöscht.

### Weitere Schutzmaßnahmen

* **Nur HTTPS.** Die Netzwerkkonfiguration verbietet Klartextverkehr
  (`cleartextTrafficPermitted="false"`), und die App weist `http://`-URLs
  schon bei der Eingabe zurück.
* **Kein Credential-Logging.** Der OkHttp-Logging-Interceptor läuft nur in
  Debug-Builds und redigiert `Authorization`. In Release-Builds ist er
  komplett abwesend.
* Das `Authorization`-Objekt existiert als eigener Typ, dessen `toString()`
  überschrieben ist, damit es nicht versehentlich in Logs oder
  Exception-Meldungen landet.
* `android:allowBackup="false"` – der verschlüsselte Zugangsdatensatz wird
  nicht über Cloud-Backups exportiert.
* `FLAG_SECURE` ist im Schritt zur manuellen Eingabe des Application
  Passwords gesetzt, damit dieses nicht in Screenshots oder der
  App-Übersicht landet. Gekapselt in `ScreenshotProtection`, damit die
  Zusage prüfbar ist - einschließlich der Gegenrichtung, dass das Flag beim
  Verlassen wieder verschwindet.

### Abgelaufene oder widerrufene Zugangsdaten

Antwortet WordPress mit **401** oder mit dem Fehlercode
`rest_cannot_*`/`invalid_username`, markiert die App die Sitzung als ungültig,
verwirft die gespeicherten Zugangsdaten nicht sofort (der Benutzer soll den
Grund sehen), sperrt aber alle schreibenden Aktionen und bietet eine erneute
Autorisierung an. Ein **403** wird davon unterschieden: Die Zugangsdaten sind
gültig, aber die Rolle reicht nicht.

---

## 7. Fehlerbehandlung

Zentrale, geschlossene Hierarchie `AppError` in `core/error`:

| Fall | Erkennung | Darstellung für den Benutzer |
|---|---|---|
| Keine Verbindung | `UnknownHostException`, `ConnectException`, Connectivity-Status | „Keine Internetverbindung.“ + Hinweis auf Cache |
| Zeitüberschreitung | `SocketTimeoutException` | „Der Server hat nicht rechtzeitig geantwortet.“ |
| TLS-Problem | `SSLException` | „Die sichere Verbindung ist fehlgeschlagen.“ |
| 401 | HTTP-Code | „Zugangsdaten ungültig oder widerrufen.“ + Aktion „Neu anmelden“ |
| 403 | HTTP-Code | „Dieses Konto darf keine Kommentare moderieren.“ |
| 404 | HTTP-Code | Kontextabhängig: Kommentar gelöscht bzw. REST-API nicht erreichbar |
| 429 | HTTP-Code, `Retry-After` | „Zu viele Anfragen. Erneut in n Sekunden.“ |
| 5xx | HTTP-Code | „Der Server meldet ein Problem.“ |
| Ungültige Konfiguration | `/wp-json/` liefert kein JSON bzw. keinen `namespaces`-Eintrag | „Unter dieser Adresse wurde keine WordPress-REST-API gefunden.“ |
| Unbekannt | Rest | Allgemeiner Text + Code für Rückfragen |

Regeln:

* Stacktraces erreichen nie die UI. Der Text kommt aus `strings.xml`.
* WordPress-Fehlercodes (`code`/`message` im JSON-Body) werden ausgewertet und
  fließen in die Zuordnung ein, die Rohmeldung wird aber nicht ungefiltert
  angezeigt.
* Technische Details gehen über `Timber`-artiges Logging nur in Debug-Builds.
  Es wird kein eigener Logger gebaut: `android.util.Log`, gekapselt in
  `core/AppLog`, das in Release-Builds nichts tut.

---

## 8. Offline-Verhalten

### Lesen

Room ist die **Single Source of Truth** für die Kommentarliste. Der Ablauf:

1. Die UI beobachtet einen `Flow` aus Room und zeigt sofort den Cache.
2. Parallel läuft die Aktualisierung gegen die API.
3. Ergebnisse werden in Room geschrieben, die UI aktualisiert sich dadurch.

Gecacht werden: Kommentare (inkl. Status), Beitragstitel, Blog-Konfiguration,
die Anzahl je Filter, die Teamzugehörigkeit und der Zeitpunkt der letzten
erfolgreichen Synchronisierung.

Die Anzahl je Filter (`filter_counts`) steht dort nicht nur für die
Filterleiste. Eine leere Kommentartabelle ist mehrdeutig — sie heißt entweder
„dieser Filter ist leer“ oder „dieser Filter wurde noch nie geholt“, und für
die Oberfläche ist das der Unterschied zwischen Leerzustand und Ladeanzeige.
Ohne die gespeicherte Zahl musste sie die zweite Deutung annehmen und beim
Start Platzhalterkarten zeigen, bis der Server bestätigte, was die App schon
wusste. Eine festgehaltene 0 ist eine Antwort, ein fehlender Eintrag heißt
„noch nie geholt“.

Das Team (`team_members`, `team_roles`) liegt aus demselben Grund dort. An ihm
hängt mehr als die Rollenmarke: Ohne bekanntes Team gilt niemand als Mitglied,
eingeklappte Rollen klappen auf, und die Hintergrundprüfung meldet
ausgerechnet die Rollen, die stummgeschaltet sind. Schlägt der Abruf fehl,
gilt deshalb der gespeicherte Stand statt eines leeren Teams — anders als bei
den Kommentaren ist „nichts bekannt“ hier keine harmlose Antwort. Die Auswahl,
welche Rollen als Team gelten, entscheidet über die gespeicherte Zuordnung;
eine Änderung verwirft sie deshalb mit (`invalidate`).

Die Zahl verkürzt nur die Wartezeit, sie ersetzt den Abruf nicht: Nach einem
Neustart wird weiterhin bei jedem Öffnen aktualisiert. Deshalb bleibt auch der
Zeitpunkt der letzten Aktualisierung je Filter bewusst im Arbeitsspeicher — er
entscheidet über das Überspringen eines Abrufs, und diese Entscheidung soll
einen Neustart nicht überleben.

Die Unterscheidung zwischen lokalem und frischem Stand ist **sichtbar**: Eine
Leiste über der Liste nennt den Zeitpunkt der letzten Synchronisierung und
kennzeichnet den Offline-Zustand. Es gibt keinen stillen Cache.

Ein Sonderfall ist der Antwortfaden in der Detailansicht: Die Liste lädt immer
nur einen Status, eine freigeschaltete Antwort auf einen offenen Kommentar
käme darüber also nie in den Cache. Deshalb holt `fetchComment` den Faden mit
`GET /wp/v2/comments?parent=<id>&status=all` eigens nach. Scheitert dieser
Nachschlag, bleibt der Kommentar selbst erhalten — es fehlt dann nur der
Faden.

### Schreiben

Bewusste Entscheidung: **Offline-Moderation wird in Version 1 nicht
angeboten**, statt sie halbfertig zu bauen.

Begründung: Eine Offline-Warteschlange für Moderation braucht eine
Konfliktstrategie (der Kommentar wurde zwischenzeitlich im Web moderiert oder
gelöscht), sonst überschreibt die App fremde Entscheidungen. Das ist deutlich
mehr Aufwand, als es nach außen aussieht, und die Vorgabe erlaubt
ausdrücklich, schreibende Aktionen offline zu sperren.

Umsetzung: Ohne Verbindung sind Moderations- und Antwort-Aktionen deaktiviert
und erklärt („Offline – Moderation nicht möglich“). Nichts geht still
verloren, weil nichts still angenommen wird. Die Warteschlange inkl.
Konfliktauflösung steht als P2 im Backlog.

---

## 9. Benachrichtigungen über neue Kommentare

### Vergleich der Optionen

| Option | Last auf WordPress | Latenz | Externe Abhängigkeit | Bewertung |
|---|---|---|---|---|
| 1. Polling gegen `wp/v2/comments` | Mittel – jede Prüfung ist eine volle Kommentarabfrage | 15 min | keine | Funktioniert überall, aber unnötig teuer. |
| 2. WordPress-Webhooks | Gering | Sekunden | **Erfordert einen erreichbaren Endpunkt**, den ein Telefon nicht hat | Ohne eigenen Server nicht nutzbar. |
| 3. Plugin als Push-Bridge | Gering | Sekunden | Plugin + Push-Dienst | Sinnvoll, aber nur zusammen mit Option 4 oder eigenem Relay. |
| 4. Firebase Cloud Messaging | Gering | Sekunden | **Google-Konto, Play Services, Firebase-Projekt, Service-Account-Schlüssel auf dem WP-Server** | Echter Push, aber proprietär und mit Datenabfluss an Google. |
| 5. Kombination | Gering | Sekunden bis Minuten | je nach Ausbaustufe | – |
| 6. UnifiedPush | Gering | Sekunden | UnifiedPush-App auf dem Telefon (z. B. ntfy) und deren Push-Server; Plugin ab 1.5 | Echter Push ohne Google. Der Push-Server erfährt nur, dass und wann kommentiert wurde. |

### Entscheidung

Gewählt wird eine **Kombination aus 1 und 3 ohne Fremdinfrastruktur**:

* Ein sehr kleines WordPress-Plugin (`commentator-bridge`) stellt einen
  Endpunkt `GET /commentator/v1/status` bereit, der im Kern drei Werte liefert:
  Anzahl ausstehender Kommentare, ID und Zeitstempel des neuesten Kommentars
  (spätere Fassungen ergänzen den neuesten Kommentar jeden Status, siehe
  `docs/api.md`).
  Das ist **eine** indizierte Abfrage statt einer vollständigen
  Kommentarauflistung – die WordPress-Installation wird also gerade *nicht*
  unnötig belastet.
* Die App prüft diesen Endpunkt periodisch über **WorkManager**
  (Standardintervall 15 Minuten, einstellbar, nur bei vorhandener Verbindung).
  Nur wenn sich die Werte geändert haben, werden die neuen Kommentare geladen.
* Ist das Plugin nicht installiert, fällt die App automatisch auf eine
  sparsame Kernabfrage zurück:
  `wp/v2/comments?status=hold&per_page=1&_fields=id,date_gmt&context=edit`.
  Die App funktioniert also vollständig ohne Plugin, nur etwas teurer.

**Warum kein FCM:** FCM verlangt Google Play Services auf dem Gerät, ein
Firebase-Projekt und einen Service-Account-Schlüssel auf dem WordPress-Server.
Damit flössen Metadaten über jeden neuen Kommentar über Google-Server. Die
Vorgaben verlangen ausdrücklich, proprietäre Cloud-Dienste zu meiden, wenn es
eine gleichwertige lokale Lösung gibt, und keine Daten an Dritte zu senden.
Für die Moderation eines Blogs ist eine Latenz von Minuten fachlich
gleichwertig zu einer Latenz von Sekunden.

**Die Architektur bleibt aber push-fähig:** Die Erkennung neuer Kommentare
liegt hinter der Schnittstelle `NewCommentSource`. Heute gibt es genau eine
Implementierung (`PollingNewCommentSource`). Ursprünglich war vorgesehen, für
echten Push eine zweite Quelle danebenzustellen.

### Ergänzung: Sofortmeldung über UnifiedPush

Der Wunsch nach Meldungen in Sekunden ist geblieben, nur der Weg über Google
nicht. Umgesetzt ist deshalb Option 6 als **optionale Ergänzung** zu 1 und 3,
je Blog in dessen Einstellungen unter „Sofort melden“ einschaltbar:

* Die App meldet sich je Blog bei einer UnifiedPush-App auf dem Telefon an
  (die UnifiedPush-Instanz ist die `instanceId`), etwa bei ntfy, und bekommt
  eine Endpoint-Adresse. Die hinterlegt sie beim Plugin über
  `POST /commentator/v1/push`. Beim Ausschalten, beim Entfernen des Blogs und
  bei einer Abmeldung durch die UnifiedPush-App nimmt
  `DELETE /commentator/v1/push` sie zurück; eine ersetzte Adresse ebenso.
* Das Plugin hängt sich an `wp_insert_comment` (nicht `comment_post`, das nur für das Kommentarformular feuert) und schickt an jede hinterlegte
  Adresse aller Konten mit `moderate_comments` einen nicht blockierenden
  `POST` mit dem Rumpf „new“ – kein Name, kein Text, keine Kennung. Als Spam
  oder Papierkorb eingegangene Kommentare wecken niemanden.
* Die Nachricht ist **bewusst inhaltslos und unverschlüsselt**; der Connector
  liefert sie mit `decrypted=false`. Eine Verschlüsselung schützte nichts,
  weil nichts darin steht. Jede Nachricht stößt eine einmalige, beschleunigte
  Prüfung aller Blogs an (`CommentSyncWorker`, eindeutig als
  „commentator-push-sync“ mit `KEEP`, damit ein Schwall von Weckrufen nicht
  ebenso viele Prüfungen auslöst). Die Kommentare holt die App danach wie
  gewohnt selbst über die REST-API.
* Die regelmäßige Prüfung läuft unverändert weiter und holt ein, was auf dem
  Push-Weg verloren ging. Der Weckruf verkürzt nur die Wartezeit.

Damit hat sich die vorgesehene `FcmNewCommentSource` erübrigt: Ein Weckruf, der
dieselbe Prüfung früher auslöst, braucht keine eigene Quelle. Benachrichtigungen,
Entdopplung, Deep Links und UI blieben unberührt.

Was der Push-Server erfährt: dass und wann auf einem Blog kommentiert wurde,
mehr nicht. Wer einen eigenen ntfy betreibt, behält auch das bei sich. Das
Plugin nimmt nur öffentliche HTTPS-Adressen an und sendet über
`wp_safe_remote_post`, damit sich der Blog nicht als Sprungbrett ins eigene
Netz missbrauchen lässt.

Der Code liegt in `push/`. `PushSetup` ist die Schnittstelle, über die die
Blog-Einstellungen die Sofortmeldung steuern – als Schnittstelle, damit sich
der Bildschirm ohne UnifiedPush und ohne Gerät prüfen lässt. `InstantPush`
setzt sie mit dem Connector um und stimmt sich mit dem Plugin ab;
`CommentatorPushService` nimmt die Ereignisse des Connectors entgegen. Die
Einstellung nennt ihren Zustand: braucht das Plugin ab 1.5, braucht eine
UnifiedPush-App, wird eingerichtet, aktiv über die gewählte App oder Fehler –
etwa ein zu altes Plugin, erkennbar an einem 404 auf `/push`.

### Aktionen in der Benachrichtigung

Jede Kommentar-Benachrichtigung trägt bis zu drei Knöpfe: „Antworten“ (bei
einem offenen Kommentar „Freigeben und antworten“, mit Texteingabe in der
Benachrichtigung über `RemoteInput`), „Freigeben“ (nur bei offenen
Kommentaren) und „Spam“ (nicht bei Spam). Sie erscheinen nur bei Konten mit
`moderate_comments`.

Der nicht exportierte `NotificationActionReceiver` arbeitet nichts selbst ab,
sondern reicht an den `NotificationActionWorker` weiter: beschleunigte
WorkManager-Arbeit, je Kommentar höchstens eine laufende Aktion
(`ExistingWorkPolicy.KEEP`), mit denselben Use Cases wie die Oberfläche.

**Keine Wiederholung bei Fehlern.** WorkManager könnte eine gescheiterte
Aktion später erneut versuchen – aber eine still nachgeholte Moderation
überschriebe womöglich eine Entscheidung, die inzwischen im Web gefallen ist.
Stattdessen bleibt die Benachrichtigung stehen und nennt den Grund; eine
gescheiterte Antwort steht mit ihrem Text darin, damit nichts Geschriebenes
verloren geht.

**Abschluss nach einer Direktantwort.** Ab Android 15 hält das System eine
Benachrichtigung nach einer Direktantwort fest
(`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`) und übergeht ein bloßes
`cancel()`. Die Meldung wird deshalb durch eine kurze Bestätigung „Erledigt“
ersetzt, die nach zwei Sekunden über `setTimeoutAfter` verschwindet.

### Aufräumen erledigter Meldungen

Eine Benachrichtigung zu einem längst erledigten Kommentar ist Lärm.

* **In der App erledigt:** Wird ein Kommentar moderiert, beantwortet oder in
  der Detailansicht geöffnet, verschwindet seine Meldung. Die Schnittstelle
  `CommentAlerts` (umgesetzt von `CommentNotifier`) hält dafür
  `ModerateCommentUseCase`, `ReplyToCommentUseCase` und
  `CommentDetailViewModel` frei von Android-Klassen.
* **Im Web erledigt:** Die Hintergrundprüfung gleicht offene Meldungen ab,
  bevor sie Neues meldet – und nur, wenn überhaupt welche offen sind. Dafür
  genügt eine Anfrage: `GET wp/v2/comments?include=<ids>&status=any`. `any`
  schließt Spam und Papierkorb ein, `all` nicht; fehlt ein Kommentar in der
  Antwort, ist er endgültig gelöscht.
* Weg kommt, was gelöscht, Spam oder im Papierkorb ist, und was als offen
  gemeldet wurde und inzwischen freigegeben ist. Was bereits freigegeben
  gemeldet wurde, bleibt stehen: Es wartet womöglich noch auf eine Antwort.
  Den Status zum Meldezeitpunkt trägt die Benachrichtigung in ihren Extras.
  Eine leer gewordene Sammelmeldung verschwindet mit.

Jede Kommentar-Benachrichtigung und die Sammelmeldung tragen das Symbol des
Blogs als großes Bild (`setLargeIcon`). `SiteIconLoader` lädt es über Coil aus
demselben Bildspeicher wie Kopfleiste und Einstellungen, mit
`allowHardware(false)`: Benachrichtigungen zeichnet ein anderer Prozess, der
mit Hardware-Bitmaps nichts anfangen kann. Es stammt vom eigenen Blog; eine Verbindung zu Dritten entsteht
nicht.

### Benachrichtigungskanäle

Die Kanäle sind durch Aktionen, Aufräumen und Sofortmeldung unverändert
geblieben.

| Kanal | ID | Inhalt | Standardwichtigkeit |
|---|---|---|---|
| Neue Kommentare | `new_comments` | Kommentare, die auf Moderation warten | `DEFAULT` |
| Antworten | `moderation_events` | Kommentare, die auf einen bestehenden Kommentar antworten | `LOW` |
| Synchronisierung | `sync_status` | Dauerhafte Hinweise bei Problemen, z. B. ungültige Zugangsdaten | `LOW` |

Jeder dieser Kanäle wird auch tatsächlich bespielt - ein Kanal, der in den
Systemeinstellungen erscheint, aber nie etwas meldet, ist für den Benutzer
irreführend. Die Zuordnung entscheidet sich an `Comment.isReply`. Alle Kanäle
sind über die Android-Systemeinstellungen einzeln steuerbar; die App verlinkt
direkt dorthin. Die Laufzeitberechtigung
`POST_NOTIFICATIONS` (ab Android 13) wird erst kontextbezogen erfragt.

**Erster Lauf meldet nichts.** Beim allerersten Durchgang gibt es keinen
Vergleichspunkt - alles Offene wäre „neu" und käme als Schwall. Der erste Lauf
hält deshalb nur den Stand fest. Ob ein solcher Ausgangszustand existiert,
beantwortet `NewCommentSource.hasBaseline`; der Hintergrunddienst liest dafür
bewusst kein Datenbankfeld mehr aus, das die Quelle nebenbei beschreibt. Der
Stand wird auch dann festgehalten, wenn gerade nichts offen ist - sonst gälte
der nächste Lauf erneut als erster und die erste echte Meldung bliebe aus.

**Keine Doppelbenachrichtigungen:** Gemeldet wird nur, was neuer ist als die
pro Instanz gespeicherte `lastNotifiedCommentId`/`lastNotifiedDate`. Zusätzlich
werden bereits gemeldete IDs in Room vermerkt. Die Notification-ID leitet sich
deterministisch aus der Kommentar-ID ab, sodass ein erneutes Melden desselben
Kommentars dieselbe Benachrichtigung ersetzt statt eine zweite zu erzeugen.

### Deep Links

Benachrichtigungen öffnen
`commentator://comment/<instanceId>/<commentId>` über einen
`PendingIntent` mit `FLAG_IMMUTABLE` auf die einzige Activity. Die Route ist
dieselbe, die die App intern verwendet – es gibt keinen zweiten Pfad in die
Detailansicht.

---

## 9a. Umschaltbares App-Symbol

Android bietet keine Möglichkeit, das Startsymbol zur Laufzeit zu ändern.
Der übliche Weg ist deshalb ein `activity-alias` je Variante, die alle auf
dieselbe Activity zeigen; eingeschaltet ist immer genau einer
(`PackageManager.setComponentEnabledSetting`).

Drei Dinge sind dabei entscheidend und deshalb im Code festgehalten:

* **Reihenfolge.** Erst die neue Variante einschalten, dann die alte aus.
  Andersherum gibt es einen Moment ohne eingeschaltetes Startsymbol - die App
  verschwindet dann aus dem Startbildschirm, bei manchen Herstellern dauerhaft.
* **`DONT_KILL_APP`.** Ohne dieses Flag beendet Android den Prozess sofort,
  mitten in der Bedienung der Einstellungen.
* **Namensraum statt Paketkennung.** Der Klassenname des Alias folgt der
  `namespace` des Moduls, die installierte Paketkennung dagegen der
  `applicationId` - im Debug-Build mit dem Zusatz `.debug`. Wer den
  Klassennamen aus der Paketkennung zusammensetzt, zeigt ins Leere, und das
  Umschalten bleibt wirkungslos. Ein Test hält das fest.

Maßgeblich für die Anzeige ist der Zustand im PackageManager, nicht eine
gespeicherte Einstellung. Damit kann beides nicht auseinanderlaufen, etwa wenn
die App-Daten gelöscht werden, der Systemzustand aber bestehen bleibt.

Bekannte Einschränkung: `android:icon` am `<application>` bleibt unverändert.
Die Systemeinstellungen und die Freigabeauswahl zeigen deshalb weiterhin die
Standardvariante.

---

## 10. Umfang des WordPress-Plugins

Das Plugin `commentator-bridge` ist optional und ändert **kein** Verhalten von
WordPress. Es fügt ausschließlich Routen unter `commentator/v1` hinzu, alle mit
derselben Authentifizierung wie die Kern-API:

| Route | Recht | Zweck |
|---|---|---|
| `GET /status` | `moderate_comments` | Kompakter Zustand für die regelmäßige Prüfung |
| `GET /summary` | `moderate_comments` | Kommentaranzahl je Status in einem Aufruf, damit die Filterleiste keine fünf Abfragen braucht |
| `GET /team` | `moderate_comments` | Rollen, die schreiben oder moderieren dürfen, und ihre Mitglieder |
| `POST /empty` | `moderate_comments` | Spam oder Papierkorb stapelweise endgültig leeren (seit 1.2.0) |
| `GET`, `POST`, `DELETE /blocklist` | `manage_options` | Die Sperrliste `disallowed_keys` lesen und pflegen (seit 1.2.0) |
| `POST`, `DELETE /push` | `moderate_comments` | Push-Adresse für die Sofortmeldung hinterlegen und zurücknehmen (seit 1.5.0) |
| `POST /push/test` | `moderate_comments` | Testweckruf an die eigenen Adressen, wartet auf den Push-Server (seit 1.7.0) |

Geschrieben wird nur an zwei Stellen, und beide entsprechen dem, was im Backend
ohnehin möglich ist: `/blocklist` ändert die Option `disallowed_keys` – dieselbe
Liste wie unter Einstellungen → Diskussion –, `/push` die Benutzermeta
`commentator_push_endpoints` des angemeldeten Kontos. `/empty` löscht, was im
Backend der Knopf „Spam leeren“ beziehungsweise „Papierkorb leeren“ löscht.

Seit 1.5.0 hängt sich das Plugin an `wp_insert_comment` und **sendet optional nach
außen**: Ist für ein Konto mit `moderate_comments` eine Push-Adresse
hinterlegt, geht dorthin bei jedem neuen Kommentar, der nicht als Spam oder
Papierkorb eingeht, ein inhaltsloser Weckruf (Abschnitt 9); seit 1.6.0 auch
bei jeder Statusänderung, die nicht aus der App kommt. Ohne hinterlegte
Adresse sendet es nichts. Tabellen legt es nicht an, Cookies setzt es nicht.

---

## 10a. Nahtstellen für Tests

An vier Stellen steht bewusst eine Schnittstelle, wo auch eine konkrete Klasse
gereicht hätte. Alle vier sind aus einem Testproblem entstanden und haben den
Produktivcode nebenbei entkoppelt:

| Schnittstelle | Statt | Warum |
|---|---|---|
| `CredentialSource` | `CredentialStore` | Der HTTP-Interceptor hängt sonst an DataStore und Android Keystore und ist ohne Gerät nicht prüfbar. |
| `WordPressApiProvider` | `WordPressClientFactory` | Repository und Hintergrundprüfung brauchen nur „gib mir den Client zu dieser Instanz“, nicht die gesamte Client-Erzeugung. |
| `SiteUrl` | private Methode im Repository | Hier fällt die Entscheidung, dass nur HTTPS zulässig ist. Eine Sicherheitsregel gehört an einen prüfbaren Ort. |
| `InboxScreenContent` / `CommentDetailBody` | Bildschirme mit ViewModel | Die Oberfläche lässt sich mit einem Zustand füttern und auf Ereignisse prüfen, ohne Hilt und ohne Gerät. |

### Fallstricke bei Compose-Tests unter Robolectric

Zwei Einstellungen sind zwingend, sonst sind die Tests wertlos statt rot:

* `@Config(qualifiers = "de-rDE-w411dp-h891dp")` – ohne Bildschirmgröße misst
  Robolectric mit 0 × 0, und in Compose gilt dann nichts als sichtbar. Und
  ohne Gebietsschema läuft der Test gegen die englischen Texte aus
  `values-en`, während die Standardsprache der App Deutsch ist.
* `graphicsMode=NATIVE` in `robolectric.properties`.

---

## 11. Teststrategie

| Ebene | Werkzeuge | Inhalt |
|---|---|---|
| Unit (JVM) | JUnit 4, kotlinx-coroutines-test, Turbine | DTO-Mapping, Statuslogik, Fehlerzuordnung, Use Cases, ViewModels |
| Netzwerk (JVM) | MockWebServer | Paginierung, Header-Auswertung, Fehlercodes, Auth-Header |
| UI | Compose UI-Test + Robolectric | Liste, Filter, Detail, Antwort, Moderationsaktion, Fehler- und Leerzustände |
| Integration | Docker-Compose-WordPress | Echter End-to-End-Durchlauf gegen eine lokale Installation |

Integrationstests laufen ausschließlich gegen die lokale Docker-Instanz. Es
gibt keinen Testpfad, der auf einen produktiven Blog zeigt; die Testkonfiguration
enthält ausschließlich `localhost`-Adressen.

---

## 12. Bekannte Risiken

* **Kotlin 2.3 statt 2.4** wegen KSP – bei einem KSP-Release für 2.4 anheben.
* **Robolectric + Compose** ist empfindlich gegenüber Versionssprüngen. Falls
  UI-Tests dort brechen, laufen dieselben Tests unverändert als
  Instrumentierungstests auf einem Gerät.
* **Papierkorb-Verhalten** hängt von `EMPTY_TRASH_DAYS` der Installation ab.
  Die App kann das nicht auslesen und weist deshalb vor dem endgültigen
  Löschen ausdrücklich darauf hin.
* **Gravatar** ist eine Verbindung zu einem Dritten (Automattic). Avatare sind
  deshalb abschaltbar; siehe `docs/privacy.md`.
