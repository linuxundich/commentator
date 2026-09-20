# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).
Versionsnummern folgen [Semantic Versioning](https://semver.org/lang/de/).

Es werden bewusst keine Versionsnummern für noch nicht veröffentlichte Stände
vergeben. Alles Unveröffentlichte steht unter `[Unreleased]`.

## [Unreleased]

### Added

**Android-App**

- Einrichtung einer WordPress-Verbindung über den Autorisierungs-Flow für
  Application Passwords, mit manueller Eingabe als Rückfallebene.
- Kommentar-Posteingang mit Autor, Zeitpunkt, Text, zugehörigem Beitrag und
  Status.
- Filter nach Alle, Offen, Genehmigt, Spam und Papierkorb.
- Seitenweises Nachladen anhand der Kopfzeile `X-WP-TotalPages`.
- Pull-to-Refresh und Aktualisieren über die Kopfleiste.
- Moderationsaktionen: genehmigen, zurückstellen, als Spam markieren, in den
  Papierkorb verschieben, endgültig löschen (mit Rückfrage).
- Bearbeiten des Kommentartexts, sofern die API es für das Konto zulässt.
- Rückgängig-Funktion für alle zurücknehmbaren Moderationsaktionen.
- Detailansicht mit vollständigem Text, Autor, Website, optional
  E-Mail-Adresse, Datum, Beitrag, Status und vorhandenen Antworten. Der
  Antwortfaden wird eingerückt und mit einer senkrechten Linie abgesetzt, damit
  Antworten nicht wie eigenständige Kommentare wirken. Er wird beim Öffnen
  eigens vom Server geholt und ist damit unabhängig vom Filter der Liste.
- Antworten auf Kommentare; die Antwort wird als echter WordPress-Kommentar
  mit Bezug auf den ursprünglichen veröffentlicht.
- „Spam leeren" und „Papierkorb leeren" in der Kopfleiste, mit Rückfrage vor
  dem endgültigen Löschen. Mit Plugin eine Anfrage, ohne Plugin eine je
  Kommentar. Bei vielen Einträgen wird stapelweise gearbeitet; die Meldung
  nennt dann, wie viele noch übrig sind.
- „Absender sperren" in der Detailansicht: trägt die Adresse in WordPress'
  Sperrliste `disallowed_keys` ein, künftige Kommentare landen direkt im
  Papierkorb. Erscheint nur, wenn das Plugin vorhanden ist und das Konto
  seitenweite Optionen ändern darf – ein Redakteur darf moderieren, aber
  keine Optionen ändern.
- Bildschirm „Über diese App" mit Version, Buildnummer und dem Commit, aus
  dem der Build entstanden ist, dazu eine Kurzfassung zu Datenverarbeitung
  und Lizenz.
- Symbol des Blogs neben seinem Namen, in der Kopfleiste des Posteingangs und
  in den Einstellungen. Es stammt aus dem REST-Index des eigenen Blogs; es
  entsteht dadurch keine Verbindung zu einem Dritten.
- Textbausteine für wiederkehrende Antworten. In den Einstellungen anlegen,
  bearbeiten und löschen; über dem Antwortfeld erscheint eine Leiste, die sie
  mit einem Tipp einfügt. Ein Baustein hängt an den vorhandenen Text an, statt
  ihn zu ersetzen. Die Texte liegen ausschließlich auf dem Gerät.
- Sprachwahl je App: Die App erscheint in den Systemeinstellungen unter
  „Sprachen der App" und lässt sich dort unabhängig vom Systemgebietsschema
  auf Deutsch oder Englisch stellen (ab Android 13). Die Liste der Sprachen
  wird beim Bauen aus den vorhandenen Ressourcen erzeugt, damit eine neue
  Übersetzung nicht vergessen werden kann.
- Anzahl der Kommentare an jedem Filter, etwa „Offen · 3". Mit dem Plugin
  zwei Abrufe, ohne Plugin fünf, von denen jeweils nur die Kopfzeile
  `X-WP-Total` ausgewertet wird. Die Zahlen ziehen nach jeder Moderation nach.
  Lässt sich eine Zahl nicht ermitteln, steht dort nur der Name.
- Autorenkontext in der Detailansicht: „Kommentiert zum ersten Mal" oder die
  Zahl der bisher freigeschalteten Kommentare dieser Adresse. Kostet eine
  Anfrage, von der nur die Kopfzeile `X-WP-Total` ausgewertet wird. Scheitert
  sie, bleibt der Hinweis aus.
- Hinweise auf Auffälligkeiten an Kommentaren: Anzahl der Links und Texte, die
  mehrfach vorkommen. Rein lokal aus dem Zwischenspeicher, ohne zusätzlichen
  Abruf. Die App kennzeichnet nur und stuft nichts selbsttätig als Spam ein.
- Hintergrundprüfung auf neue Kommentare über WorkManager, mit einstellbarem
  Intervall.
- Benachrichtigungen mit drei getrennten Kanälen, die jeweils tatsächlich
  bespielt werden: neue Wortbeiträge, Antworten auf bestehende Kommentare und
  Hinweise zur Synchronisierung. Entdopplung über eine aus der Kommentar-ID
  abgeleitete Kennung.
- Deep Link `commentator://comment/<instanceId>/<commentId>` aus der
  Benachrichtigung direkt in die Detailansicht.
- Lokaler Room-Cache für Kommentare, Beitragstitel und
  Synchronisierungszustand.
- Sichtbare Unterscheidung zwischen gespeichertem und frisch geladenem Stand,
  einschließlich Offline-Band mit Zeitpunkt der letzten Aktualisierung.
- Einstellungen für Benachrichtigungen, Prüfintervall, Avatare und die Anzeige
  von E-Mail-Adressen, mit Verweis in die Android-Systemeinstellungen.
- Material 3 mit Dynamic Color, hellem und dunklem Modus nach
  Systemeinstellung.
- Deutsche und englische Oberfläche.
- Eigenes App-Symbol als adaptives Icon: eine Sprechblase mit einem W darin.
  Zwei Farbvarianten, in den Einstellungen umschaltbar - Android-Grün und
  WordPress-Blau -, umgesetzt über je einen `activity-alias`. Mit monochromer
  Variante für themenbezogene Symbole ab Android 13; das
  Benachrichtigungssymbol greift dieselbe Silhouette auf. Es wird keine fremde
  Wort- oder Bildmarke verwendet.

- Play-Store-Symbol als 512 × 512 px großes 32-Bit-PNG unter `store/play/`,
  geprüft gegen die Spezifikationen für das App-Symbol im Play Store.

**Behoben nach dem ersten Lauf auf einem Gerät**

- Die Detailansicht lud den Avatar eines Kommentators, ohne die Einstellung
  „Avatare anzeigen" zu beachten. Damit entstand eine Anfrage an Gravatar,
  obwohl sie abgelehnt war. Die Listenansicht war davon nicht betroffen.
- Eine soeben veröffentlichte Antwort zeigte „Vor 0 Min.". Unterhalb einer
  Minute steht jetzt „Gerade eben".
- `seed.sh` hängte die Testkommentare an WordPress' Standardbeitrag
  „Hello world!" statt an die selbst angelegten Beiträge.
- Der Hintergrunddienst leitete „erster Lauf" aus einem Datenbankfeld ab, das
  die Kommentarquelle nebenbei beschrieb - ein Vertrag, der nicht in der
  Schnittstelle stand. `NewCommentSource.hasBaseline` macht ihn jetzt
  ausdrücklich. Dabei fiel auf: Lief die Prüfung zum ersten Mal, während nichts
  offen war, entstand gar kein Ausgangszustand, und die erste echte
  Benachrichtigung blieb aus.
- Die Beschriftungsspalte der Detailansicht war mit 96 dp rund doppelt so breit
  wie nötig und riss eine Lücke zwischen Bezeichnung und Wert.
- Die blaue Symbolvariante erreichte auf dem dunklen Grund nur 3,3:1 Kontrast.
  Sie ist jetzt umgekehrt eingefärbt - weisse Blase auf WordPress-Blau - und
  kommt auf 5,6:1.
- Die Marke sitzt in allen Symbolebenen optisch statt geometrisch zentriert.
  Der Blasenkörper trägt fast die gesamte Fläche, weshalb die am Rahmen
  ausgerichtete Zeichnung nach oben gerutscht wirkte.

**WordPress-Plugin**

- `commentator-bridge` mit den Leseendpunkten `commentator/v1/status` und
  `commentator/v1/summary`, beide abgesichert über `moderate_comments`.

**Entwicklung und Tests**

- Room exportiert sein Schema nach `android/app/schemas/`, damit spätere
  Migrationen automatisiert geprüft werden können.

- Lokale WordPress-Testumgebung über Docker Compose, einschließlich
  TLS-Proxy, Zertifikatsskript und Skript für reproduzierbare Testdaten.
- Unit-Tests für Statuslogik, Fehlerzuordnung, DTO-Mapping, Use Cases und
  ViewModels.
- Netzwerktests gegen MockWebServer für Abfrageparameter, Paginierung,
  Authentifizierungs-Kopfzeile und die vollständige Fehlerbehandlung.
- Dokumentation: Architekturentscheidungen, verwendete API-Endpunkte,
  Datenverarbeitung.

### Fixed

**Android-App**

- Die Auswahl des Prüfintervalls stand in einer einzeiligen Reihe. Für die
  fünfte Option blieben nur 39 dp Breite, ihr Text brach senkrecht um und riss
  eine hohe leere Fläche in die Einstellungen; bedienbar war sie damit auch
  nicht mehr. Die Chips brechen jetzt um.
- Ein nachträglich installiertes Plugin `commentator-bridge` wurde nie
  bemerkt. Bridge-Erkennung, Moderationsrecht und Blogname stammten
  ausschließlich aus dem Moment der Anmeldung und wurden danach nie wieder
  gelesen. Sie werden jetzt beim Aktualisieren des Posteingangs und beim
  Öffnen der Einstellungen neu bewertet.

**WordPress-Plugin**

- Zwei neue Endpunkte in Version 1.2.0: `/empty` leert Spam oder Papierkorb
  in Stapeln, `/blocklist` pflegt `disallowed_keys`. Beide tun nur das, was im
  Backend ohnehin möglich ist, und prüfen dieselben Rechte – `/empty`
  `moderate_comments`, `/blocklist` `manage_options`.
- `commentator/v1/summary` meldete unter `all` die Zahl `total_comments`, die
  Spam mitzählt. Die REST-API listet bei `status=all` aber nur Genehmigtes und
  Offenes auf – die Zahl passte damit nicht zu der Liste, die sie beschreibt.
  Jetzt genehmigt plus offen. Plugin-Version 1.1.0.

### Changed

**Android-App**

- Version und Buildnummer kommen aus Git statt aus fest eingetragenen Werten:
  die Buildnummer aus der Anzahl der Commits, der Versionsname aus dem
  jüngsten Tag beziehungsweise aus Basisversion und Commit. Ohne Git greifen
  Rückfallwerte, damit ein Build aus einem Quellarchiv nicht scheitert.

### Security

- Zugangsdaten werden mit AES-256-GCM verschlüsselt abgelegt; der Schlüssel
  liegt im Android Keystore und verlässt ihn nicht.
- `androidx.security:security-crypto` wird bewusst nicht verwendet: Die
  Bibliothek ist seit 1.1.0-beta01 (Juni 2025) vollständig deprecated.
- Klartextverkehr ist auf drei Ebenen unterbunden: Netzwerkkonfiguration,
  Manifest und ein eigener Interceptor.
- HTTP-Logging existiert ausschließlich in Debug-Builds und redigiert dort die
  Kopfzeile `Authorization`; in Release-Builds ist die Bibliothek nicht Teil
  der Anwendung.
- Typen für Zugangsdaten geben ihren Wert über `toString()` nicht preis, was
  durch Unit-Tests abgesichert ist.
- `FLAG_SECURE` im Schritt zur manuellen Eingabe des Application Passwords,
  gekapselt in `ScreenshotProtection` und durch Tests abgesichert - auch
  dahingehend, dass das Flag beim Verlassen wieder entfernt wird.
- `allowBackup="false"` und leere Extraktionsregeln verhindern, dass
  Zugangsdaten in Cloud-Backups oder auf ein neues Gerät gelangen.
- Das Application Password wird nicht als Navigationsargument weitergereicht,
  damit es nicht im Backstack oder im gespeicherten Zustand landet.
- Die App fordert selbst drei Berechtigungen an. Drei weitere steuert
  `androidx.work` beim Zusammenführen der Manifeste bei; alle sechs sind in
  `docs/privacy.md` einzeln aufgeführt.
- Die Signaturkonfiguration für Release-Builds liest ausschließlich
  Gradle-Properties von außerhalb des Projekts. Weder Keystore noch Passwörter
  liegen im Repository, und ohne sie entsteht ein unsigniertes Release statt
  eines Build-Fehlers.
- Sicherungsregeln greifen auch auf Android 11 und älter (`fullBackupContent`),
  nicht nur über `dataExtractionRules` ab Android 12.
- Die Laufzeitberechtigung für Benachrichtigungen wird nur dort abgefragt, wo
  es sie gibt (ab Android 13). Eine ungeprüfte Abfrage auf älteren Versionen
  hätte Benachrichtigungen dort vollständig unterdrückt.

### Notes

- Keine Analytics, keine Absturzberichte, keine Telemetrie, keine Werbung.
- Keine Firebase- oder Google-Play-Services-Abhängigkeit. Die Erkennung neuer
  Kommentare liegt hinter der Schnittstelle `NewCommentSource`, sodass eine
  Push-Variante später ergänzt werden kann, ohne die Benachrichtigungslogik
  anzufassen.
