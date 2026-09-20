# Datenverarbeitung und Netzwerkverbindungen

Dieses Dokument benennt vollständig, welche Daten Commentator verarbeitet und
wohin die App Verbindungen aufbaut. Es ist bewusst so geschrieben, dass es
sich gegen den Quelltext prüfen lässt.

## Netzwerkverbindungen

| Ziel | Wann | Zweck | Abschaltbar |
|---|---|---|---|
| Die konfigurierte WordPress-Instanz | Beim Aktualisieren, bei jeder Moderationsaktion und beim periodischen Prüfen im Hintergrund | Kommentare lesen und moderieren | Durch Abmelden; die Hintergrundprüfung einzeln in den Einstellungen |
| `secure.gravatar.com` bzw. die von WordPress gelieferte Avatar-Adresse | Nur beim Anzeigen von Avataren | Profilbilder der Kommentatoren | Ja – Avatare sind **standardmäßig abgeschaltet** |

Weitere Verbindungen gibt es nicht. Insbesondere:

* keine Analytics, keine Absturzberichte, keine Telemetrie
* keine Werbung und keine Werbekennungen
* keine Firebase- oder Google-Play-Services-Bibliotheken
* keine Update- oder Lizenzprüfung
* kein Server des Entwicklers

Die Avatar-Adressen liefert WordPress selbst im Feld `author_avatar_urls`.
Bei einer Standardinstallation zeigen sie auf Gravatar, einen Dienst von
Automattic. Das ist eine Verbindung zu einem Dritten – deshalb ist die Anzeige
von Avataren standardmäßig aus und muss bewusst eingeschaltet werden.

## Verschlüsselung

Ausschließlich HTTPS. Klartextverkehr ist auf zwei Ebenen unterbunden:

* `network_security_config.xml` mit `cleartextTrafficPermitted="false"` und
  `android:usesCleartextTraffic="false"` im Manifest
* ein Interceptor, der jede Anfrage ohne HTTPS abbricht

Adressen, die mit `http://` beginnen, weist die App bereits bei der Eingabe
zurück.

Debug-Builds vertrauen zusätzlich vom Benutzer installierten Zertifikaten
(`<debug-overrides>`), damit sich gegen die lokale Testumgebung mit
selbstsigniertem Zertifikat entwickeln lässt. Die Plattform ignoriert diesen
Block in Release-Builds; Klartext bleibt auch im Debug-Build verboten.

## Zugangsdaten

| | |
|---|---|
| Verfahren | Application Password (WordPress-Kernfunktion seit 5.6) |
| Kontokennwort | Wird **nie** an die App übergeben; die Eingabe erfolgt im Browser |
| Ablage | `Base64(IV ‖ AES-256-GCM(JSON))` in DataStore |
| Schlüssel | Android Keystore, Alias `commentator_credentials_v1`, verlässt den Keystore nicht |
| Übertragung | Nur als `Authorization`-Kopfzeile an die konfigurierte Instanz |
| Widerruf | Jederzeit im WordPress-Profil, ohne das Kontokennwort zu ändern |

Maßnahmen gegen unbeabsichtigtes Preisgeben:

* `ApplicationPassword` und `InstanceCredentials` überschreiben `toString()`
  und geben den Wert nicht preis. Zwei Unit-Tests sichern das ab.
* Das HTTP-Logging existiert nur im Debug-Build – die Bibliothek ist in
  Release-Builds gar nicht Teil der Anwendung – und redigiert dort
  `Authorization`.
* Der Schritt zur manuellen Eingabe des Application Passwords setzt
  `FLAG_SECURE`. Damit erscheint ein eingegebenes Passwort weder in
  Screenshots noch in der App-Übersicht. Bewusst nur dieser eine Schritt:
  Adresseingabe und Browser-Weg zeigen nichts Schützenswertes, und eine
  App, die sich generell nicht fotografieren lässt, wäre ohne Gewinn
  lästig.
* `android:allowBackup="false"` und leere Extraktionsregeln: Es wird nichts in
  ein Cloud-Backup übernommen und nichts auf ein neues Gerät übertragen.
* Das Application Password wird aus dem Deep-Link-Intent gelesen und sofort
  über einen flüchtigen Kanal weitergereicht. Es wird bewusst **nicht** als
  Navigationsargument übergeben, weil es dort im Backstack und im
  gespeicherten Zustand landen würde.

## Lokal gespeicherte Daten

In der Room-Datenbank (`commentator.db`, App-privates Verzeichnis):

* Kommentare: Kennung, Autorname, E-Mail-Adresse, Website, Avatar-Adresse,
  Inhalt, Datum, Status, Beitrag
* Beitragstitel
* Zeitpunkt der letzten Synchronisierung
* Kennungen bereits gemeldeter Kommentare (zur Vermeidung von
  Doppelbenachrichtigungen, nach 30 Tagen automatisch entfernt)

In DataStore:

* Blog-Konfiguration: Kennung, Anzeigename, Adresse, Benutzername,
  Benutzerkennung, Berechtigung, ob das Plugin erkannt wurde
* Einstellungen
* der verschlüsselte Zugangsdatensatz

**Personenbezug:** Kommentardaten enthalten regelmäßig personenbezogene Daten
Dritter, insbesondere E-Mail-Adressen. Sie liegen ausschließlich lokal und
kommen von der eigenen WordPress-Installation. Die App gibt sie an niemanden
weiter. Die Anzeige der E-Mail-Adresse in der Detailansicht ist standardmäßig
abgeschaltet – sie wird für die Moderation selten gebraucht.

Beim Abmelden werden gelöscht: alle Kommentare und Beitragstitel der Instanz,
der Synchronisierungszustand, die Benachrichtigungsvermerke, die
Blog-Konfiguration und der Zugangsdatensatz. Ist danach keine Instanz mehr
eingerichtet, wird zusätzlich der Keystore-Schlüssel entfernt.

## Berechtigungen

| Berechtigung | Wofür |
|---|---|
| `INTERNET` | Zugriff auf die WordPress-REST-API |
| `ACCESS_NETWORK_STATE` | Offline-Erkennung und Bedingung für die Hintergrundprüfung |
| `POST_NOTIFICATIONS` | Hinweis auf neue Kommentare (ab Android 13, wird zur Laufzeit erfragt) |

Mehr nicht. Kein Speicherzugriff, keine Kontakte, kein Standort, keine
Kamera, kein Hintergrundstandort, kein Wecker, keine exakten Alarme.

## Datenverarbeitung durch das WordPress-Plugin

Das optionale Plugin `commentator-bridge` registriert zwei **Lese**endpunkte.
Es schreibt nichts, ändert kein Verhalten von WordPress, legt keine Tabellen
an, setzt keine Cookies und sendet keine Daten an Dritte. Beide Endpunkte
liefern ausschließlich Zahlen sowie Kennung und Zeitstempel des neuesten
moderationsbedürftigen Kommentars – keine Kommentarinhalte.
