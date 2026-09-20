# Commentator

Eine native Android-App zur Moderation von WordPress-Kommentaren.

Commentator ist kein WordPress-Reader und kein Ersatz für das Backend. Es ist
ein Werkzeug für genau eine Aufgabe: die täglichen Kommentare eines Blogs
sichten, genehmigen, ablehnen, beantworten – schnell, vom Telefon aus, ohne
den Umweg über die Weboberfläche.

---

## Für wen ist das gedacht?

Für Leute, die einen eigenen WordPress-Blog betreiben, dort regelmäßig
Kommentare bekommen und diese nicht über `wp-admin` auf einem
Mobilbildschirm moderieren wollen. Die App verlangt kein Konto bei einem
Dienst, keine App-Store-Anmeldung und keinen Server des Entwicklers – nur die
eigene WordPress-Installation.

---

## Funktionen

### Kommentare sichten

* Kommentare über die offizielle WordPress-REST-API abrufen
* Übersichtliche Liste mit Autor, Zeitpunkt, Text, zugehörigem Beitrag und
  Status
* Filter: Alle, Offen, Genehmigt, Spam, Papierkorb
* Optional Avatare (standardmäßig aus, siehe Datenschutz)
* Pull-to-Refresh und Aktualisieren über die Kopfleiste
* Seitenweises Nachladen, auch bei Blogs mit vielen Kommentaren
* Lokaler Zwischenspeicher: Die Liste ist sofort da, auch ohne Verbindung

### Moderieren

* Genehmigen
* Auf „ausstehend“ zurücksetzen
* Als Spam markieren
* In den Papierkorb verschieben
* Endgültig löschen (mit Rückfrage)
* Kommentartext bearbeiten, sofern das Konto es darf
* Jede zurücknehmbare Aktion bietet direkt „Rückgängig“ an

### Antworten

* Antwort direkt aus der Detailansicht schreiben
* Veröffentlichung als echter WordPress-Kommentar mit Bezug auf den
  ursprünglichen Kommentar
* Rückmeldung bei Erfolg, verständliche Fehlermeldung bei Problemen

### Benachrichtigungen

* Hinweis auf neue moderationsbedürftige Kommentare, auch wenn die App
  geschlossen ist
* Antippen öffnet unmittelbar den betreffenden Kommentar
* Getrennte Benachrichtigungskanäle, einzeln über die Android-Systemeinstellungen
  steuerbar
* Kein Kommentar wird zweimal gemeldet
* Prüfintervall einstellbar

### Sonstiges

* Material 3, Dynamic Color, heller und dunkler Modus nach Systemeinstellung
* Deutsch und Englisch
* Keine Werbung, keine Analytics, keine Telemetrie

---

## Voraussetzungen

### Gerät

* Android 8.0 (API 26) oder neuer

### WordPress

* WordPress 5.6 oder neuer (für Application Passwords)
* **HTTPS ist zwingend.** Ohne gesicherte Verbindung bietet WordPress keine
  Application Passwords an, und die App lässt Klartextverkehr grundsätzlich
  nicht zu.
* Die REST-API muss erreichbar sein (`https://<blog>/wp-json/`). Manche
  Sicherheits-Plugins sperren sie.
* Das verwendete Konto braucht die Berechtigung `moderate_comments`
  (Rolle Redakteur oder Administrator). Ohne sie richtet die App die
  Verbindung zwar ein, sperrt aber alle Moderationsaktionen.

### Optional: Plugin `commentator-bridge`

Nicht erforderlich. Es macht die regelmäßige Prüfung auf neue Kommentare
deutlich sparsamer, indem es drei Zahlen statt einer vollständigen
Kommentarliste liefert. Ohne das Plugin funktioniert die App vollständig.

### Push-Infrastruktur

**Wird nicht benötigt.** Es gibt kein Firebase, keinen Vermittlungsserver und
keine Google Play Services. Die Begründung für diese Entscheidung und der
Vergleich mit den Alternativen stehen in
[`docs/architecture.md`](docs/architecture.md#9-benachrichtigungen-über-neue-kommentare).

---

## Einrichtung

### 1. WordPress vorbereiten

1. Sicherstellen, dass der Blog über HTTPS erreichbar ist.
2. `https://<blog>/wp-json/` im Browser aufrufen. Es muss JSON erscheinen.
   Kommt stattdessen eine Fehlerseite, blockiert etwas die REST-API.
3. Ein Konto mit der Rolle Redakteur oder Administrator bereithalten.

### 2. Optional: Plugin installieren

```bash
cp -r wordpress-plugin/commentator-bridge <wordpress>/wp-content/plugins/
```

Anschließend im Backend unter „Plugins“ aktivieren. Die App erkennt es
automatisch und weist in den Einstellungen darauf hin.

### 3. Zugangsdaten erzeugen

Der bequeme Weg läuft über die App selbst – Schritt 4 übernimmt das.

Manuell geht es so: In WordPress unter **Benutzer → Profil → Application
Passwords** einen Namen eingeben, etwa „Commentator Android“, und das erzeugte
Passwort notieren. Es wird nur einmal angezeigt.

Das Kontokennwort wird dafür nicht gebraucht und gehört auch nicht in die App.

### 4. App einrichten

1. App starten.
2. Blog-Adresse eingeben, etwa `https://example.com`. Die App prüft, ob dort
   eine WordPress-REST-API erreichbar ist.
3. **In WordPress autorisieren** wählen. Es öffnet sich der Browser mit der
   Autorisierungsseite der eigenen Installation. Dort anmelden und bestätigen.
4. WordPress springt zurück in die App, die das erhaltene Application Password
   sofort prüft und verschlüsselt ablegt.

Bietet die Installation den Autorisierungs-Flow nicht an, führt
**Application Password manuell eingeben** zum selben Ergebnis – dann mit dem
in Schritt 3 erzeugten Passwort.

### 5. Benachrichtigungen einrichten

Beim ersten Start fragt die App die Berechtigung für Benachrichtigungen ab
(ab Android 13). In den App-Einstellungen lassen sich Benachrichtigungen und
das Prüfintervall festlegen; die Feineinstellung je Kanal erfolgt in den
Android-Systemeinstellungen, die aus der App heraus verlinkt sind.

Das kürzest mögliche Intervall sind 15 Minuten – das ist die Untergrenze von
WorkManager für periodische Arbeit.

### 6. App bauen

```bash
cd android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk    # Pfad je nach System
./gradlew assembleDebug
```

Das APK liegt danach unter
`android/app/build/outputs/apk/debug/app-debug.apk`.

### 7. App installieren

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Entwicklung

### Werkzeuge

| Werkzeug | Version |
|---|---|
| JDK | 21 (Minimum 17) |
| Gradle | 9.7.1, über den mitgelieferten Wrapper |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.3.21 |
| Android SDK Platform | 37 (`platforms;android-37.0`) |
| Build Tools | 37.0.0 |
| Android Studio | Quail 4 (2026.1.4) oder neuer, optional |

Android Studio ist nicht erforderlich – das Projekt baut vollständig über die
Kommandozeile.

Unter Arch Linux:

```bash
sudo pacman -S --needed jdk21-openjdk
yay -S --needed android-sdk-cmdline-tools-latest android-sdk-platform-tools \
                android-sdk-build-tools android-platform
```

`android/local.properties` muss auf das SDK zeigen:

```properties
sdk.dir=/opt/android-sdk
```

Diese Datei gehört nicht ins Repository und steht in `.gitignore`.

### Build-Befehle

```bash
cd android

./gradlew assembleDebug          # Debug-APK
./gradlew assembleRelease        # Release-APK (siehe unten)
./gradlew testDebugUnitTest      # Unit- und UI-Tests auf der JVM
./gradlew connectedDebugAndroidTest   # Instrumentierungstests, Gerät nötig
./gradlew lint                   # Android Lint
./gradlew check                  # Lint und Tests zusammen
```

### Datenbankschema

Room exportiert das Schema nach `android/app/schemas/`. Diese Dateien gehören
ins Repository: Nur mit ihnen lassen sich spätere Migrationen automatisiert
prüfen.

### Release-Build

```bash
./gradlew assembleRelease
```

Release-Builds verwenden R8 mit Code- und Ressourcenverkleinerung.

Das Repository enthält **weder Keystore noch Passwörter**. Die
Signaturkonfiguration liest vier Gradle-Properties, die außerhalb des
Projekts liegen müssen – in `~/.gradle/gradle.properties`:

```properties
COMMENTATOR_STORE_FILE=/home/<benutzer>/.android/commentator-release.p12
COMMENTATOR_STORE_PASSWORD=<Passwort>
COMMENTATOR_KEY_ALIAS=commentator
COMMENTATOR_KEY_PASSWORD=<Passwort>
```

Fehlt auch nur eine davon, entsteht ein **unsigniertes** Release statt eines
Build-Fehlers. Ein fremder Klon des Repositories baut also weiterhin, nur eben
ohne Signatur.

Einen passenden Schlüssel erzeugt man so:

```bash
keytool -genkeypair \
  -keystore ~/.android/commentator-release.p12 \
  -storetype PKCS12 -alias commentator \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=<Name>, O=<Organisation>, C=DE"
chmod 600 ~/.android/commentator-release.p12
```

> **Der Schlüssel ist nicht ersetzbar.** Android erlaubt ein Update nur, wenn
> es mit demselben Schlüssel signiert ist wie die installierte Version. Geht
> er verloren, lässt sich die App nur deinstallieren und neu installieren –
> mit Verlust aller lokalen Daten. Keystore und Passwort gehören deshalb in
> eine Sicherung und in einen Passwortmanager, nicht ins Repository.

### Tests

| Ebene | Befehl | Inhalt |
|---|---|---|
| Unit | `./gradlew testDebugUnitTest` | Mapping, Statuslogik, Fehlerzuordnung, Use Cases, ViewModels |
| Netzwerk | dito | MockWebServer: Parameter, Paginierung, Fehlercodes, Auth-Kopfzeile |
| UI | dito | Compose-Tests über Robolectric: Liste, Filter, Moderation, Leer- und Fehlerzustände |
| Integration | siehe unten | Gegen eine lokale WordPress-Installation |

Es gibt keinen Testpfad, der auf einen produktiven Blog zeigt. Die
Integrationstests überspringen sich selbst, solange keine Umgebungsvariablen
gesetzt sind, und weigern sich abzulaufen, wenn die angegebene Adresse nicht
`localhost` oder `127.0.0.1` ist.

So laufen sie gegen die lokale Umgebung:

```bash
cd docker
./scripts/generate-cert.sh
docker compose up -d
./scripts/seed.sh          # gibt am Ende ein Application Password aus

cd ../android
COMMENTATOR_IT_URL=https://localhost:8443 \
COMMENTATOR_IT_USER=moderator \
COMMENTATOR_IT_PASSWORD='<das ausgegebene Passwort>' \
  ./gradlew :app:testDebugUnitTest --tests '*WordPressIntegrationTest'
```

Geprüft wird dabei der komplette Weg gegen eine echte WordPress-Installation:
Erkennung der REST-API, Anmeldung, Berechtigungen, Kommentarabruf,
Statuswechsel samt Zurücknahme, Antworten und der Endpunkt des Plugins. Die
Tests räumen hinter sich auf.

### Lokale WordPress-Testumgebung

```bash
cd docker
./scripts/generate-cert.sh
docker compose up -d
./scripts/seed.sh
```

Beenden:

```bash
docker compose down        # Daten bleiben erhalten
docker compose down -v     # alles verwerfen
```

Einzelheiten, auch zum Zugriff aus dem Emulator, stehen in
[`docker/README.md`](docker/README.md).

---

## Architektur

Ausführlich in [`docs/architecture.md`](docs/architecture.md). In Kurzform:

```
UI (Compose, Material 3)
 ▼
ViewModel (StateFlow)
 ▼
Use Cases / Repository
 ▼
WordPress-API-Client (Retrofit) + Room-Cache + Keystore
 ▼
WordPress REST API
```

Die wichtigsten Festlegungen:

* **Room ist die einzige Quelle für die Anzeige.** Netzwerkantworten werden in
  die Datenbank geschrieben, die UI beobachtet die Datenbank. Deshalb ist der
  Offline-Fall kein Sonderfall.
* **Die UI kennt weder DTOs noch HTTP-Codes.** Beides wird in der Datenschicht
  in Domänenmodelle und eine geschlossene Fehlerhierarchie übersetzt.
* **Die WordPress-Instanz ist nie implizit.** Auch wenn Version 1 nur einen
  Blog bedient, trägt jede Datenbankzeile eine `instanceId`, der HTTP-Client
  wird pro Instanz erzeugt, und Zugangsdaten liegen pro Instanz. Ein zweiter
  Blog ist damit eine Oberflächenaufgabe, keine Migration.
* **Die Erkennung neuer Kommentare liegt hinter einer Schnittstelle.** Heute
  gibt es eine Polling-Implementierung; eine Push-Variante ließe sich
  ergänzen, ohne Benachrichtigungen, Deep Links oder UI anzufassen.

Verzeichnisse:

```
android/                Die App
wordpress-plugin/       Optionales Plugin commentator-bridge
docker/                 Lokale WordPress-Testumgebung
docs/                   Architektur, API-Nutzung, Datenschutz
```

---

## Sicherheit

* **Kein WordPress-Kennwort in der App.** Verwendet werden ausschließlich
  Application Passwords. Beim Autorisierungs-Flow wird das Kontokennwort nur
  im Browser eingegeben; die App bekommt es nie zu sehen. Ein Application
  Password lässt sich im WordPress-Profil einzeln widerrufen.
* **Verschlüsselte Ablage.** Das Application Password liegt als
  `AES-256-GCM`-Chiffrat in DataStore. Der Schlüssel steckt im Android
  Keystore und verlässt ihn nicht. `EncryptedSharedPreferences` wird bewusst
  nicht verwendet – die Bibliothek ist seit Mitte 2025 deprecated, und Google
  verweist auf genau diesen Weg.
* **Nur HTTPS**, erzwungen über Netzwerkkonfiguration, Manifest und einen
  eigenen Interceptor.
* **Keine Zugangsdaten in Logs.** HTTP-Logging existiert nur im Debug-Build
  und redigiert dort die `Authorization`-Kopfzeile. Die Typen für
  Zugangsdaten geben ihren Wert über `toString()` nicht preis; zwei Unit-Tests
  sichern das ab.
* **Keine Zugangsdaten in Screenshots.** Der Schritt zur manuellen Eingabe
  des Application Passwords setzt `FLAG_SECURE`; dort verweigert Android
  Screenshots und zeigt in der App-Übersicht keine Vorschau. Der übrige
  Einrichtungsablauf und die gesamte App bleiben fotografierbar.
* **Keine Zugangsdaten in Backups.** `allowBackup="false"` und leere
  Extraktionsregeln.
* **Keine Zugangsdaten im Repository.** Weder Keystores noch
  Signaturkonfiguration noch `local.properties` sind eingecheckt.

Umgang mit Kommentardaten: Sie enthalten personenbezogene Daten Dritter,
insbesondere E-Mail-Adressen. Sie liegen ausschließlich in der App-privaten
Datenbank, werden an niemanden weitergegeben und beim Abmelden vollständig
gelöscht. Die Anzeige der E-Mail-Adresse ist standardmäßig abgeschaltet.

---

## Datenschutz

Vollständig beschrieben in [`docs/privacy.md`](docs/privacy.md).

Die Kurzfassung: Die App baut Verbindungen ausschließlich zur konfigurierten
WordPress-Instanz auf. Die einzige mögliche Ausnahme sind Avatarbilder, die
bei einer Standardinstallation von Gravatar stammen – deshalb ist die Anzeige
von Avataren standardmäßig **aus**.

Es gibt keine Analytics, keine Absturzberichte, keine Telemetrie, keine
Werbung, keine Werbekennungen und keinen Server des Entwicklers. Die App
fordert drei Berechtigungen an: `INTERNET`, `ACCESS_NETWORK_STATE` und
`POST_NOTIFICATIONS`.

---

## Bekannte Einschränkungen

* **Ein Blog.** Die Datenhaltung ist mehrinstanzenfähig, die Oberfläche noch
  nicht. Siehe [`BACKLOG.md`](BACKLOG.md).
* **Keine Offline-Moderation.** Ohne Verbindung sind schreibende Aktionen
  gesperrt und als solche gekennzeichnet. Eine Warteschlange bräuchte eine
  Konfliktauflösung für zwischenzeitlich anderswo moderierte Kommentare; das
  halbfertig zu bauen wäre schlechter als es wegzulassen.
* **Benachrichtigungen mit Verzögerung.** Die Prüfung läuft periodisch,
  frühestens alle 15 Minuten. Echtzeit-Push würde Fremdinfrastruktur
  erfordern – die Abwägung steht in der Architekturdokumentation.
* **Erster Lauf meldet nichts.** Beim allerersten Hintergrundlauf wird nur der
  Ausgangszustand festgehalten, damit nicht der gesamte vorhandene Rückstand
  als Benachrichtigungsflut ankommt.
* **Keine Suche und keine Sammelaktionen.** Beides steht im Backlog.
* **Kein Kommentar-Threading in der Liste.** Antworten erscheinen in der
  Detailansicht des übergeordneten Kommentars, die Liste ist flach.
* **Papierkorb-Verhalten hängt von WordPress ab.** Ist der Papierkorb in der
  Installation abgeschaltet, löscht bereits die Papierkorb-Aktion endgültig.
  Die App kann das nicht auslesen und weist vor dem endgültigen Löschen
  ausdrücklich darauf hin.
* **Kommentarbearbeitung ist reines HTML.** Es gibt keinen Rich-Text-Editor;
  bearbeitet wird der gerenderte HTML-Text.
* **Instrumentierungstests brauchen ein Gerät.** Ohne Gerät oder Emulator
  laufen nur die JVM-Tests, die aber den Großteil abdecken.

---

## Marken

Das App-Symbol verwendet die WordPress-Bildmarke, eingefärbt in Android-Grün.
„WordPress“ und das zugehörige Logo sind Marken der WordPress Foundation.
Dieses Projekt steht in keiner Verbindung zur WordPress Foundation oder zu
Automattic und wird von ihnen weder unterstützt noch geprüft. Wer die App
veröffentlichen möchte, sollte vorher ein eigenes Symbol verwenden.

---

## Lizenz

MIT – siehe [`LICENSE`](LICENSE).
