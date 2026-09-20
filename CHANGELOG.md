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
  E-Mail-Adresse, Datum, Beitrag, Status und vorhandenen Antworten.
- Antworten auf Kommentare; die Antwort wird als echter WordPress-Kommentar
  mit Bezug auf den ursprünglichen veröffentlicht.
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
- App-Symbol als adaptives Icon mit monochromer Variante für themenbezogene
  Symbole ab Android 13.

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
- Der Einrichtungsbildschirm setzt `FLAG_SECURE`, solange er sichtbar ist.
- `allowBackup="false"` und leere Extraktionsregeln verhindern, dass
  Zugangsdaten in Cloud-Backups oder auf ein neues Gerät gelangen.
- Das Application Password wird nicht als Navigationsargument weitergereicht,
  damit es nicht im Backstack oder im gespeicherten Zustand landet.
- Die App fordert drei Berechtigungen an und keine weiteren.
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
