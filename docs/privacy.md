# Datenverarbeitung und Netzwerkverbindungen

Dieses Dokument benennt vollständig, welche Daten Commentator verarbeitet und
wohin die App Verbindungen aufbaut. Es ist bewusst so geschrieben, dass es
sich gegen den Quelltext prüfen lässt.

## Netzwerkverbindungen

| Ziel | Wann | Zweck | Abschaltbar |
|---|---|---|---|
| Die konfigurierte WordPress-Instanz | Beim Aktualisieren, bei jeder Moderationsaktion und beim periodischen Prüfen im Hintergrund | Kommentare lesen und moderieren | Durch Abmelden; die Hintergrundprüfung einzeln in den Einstellungen |
| `secure.gravatar.com` bzw. die von WordPress gelieferte Avatar-Adresse | Nur beim Anzeigen von Avataren | Profilbilder der Kommentatoren | Ja – Avatare sind **standardmäßig abgeschaltet** |

Weitere Verbindungen baut die App nicht auf. Insbesondere:

* keine Analytics, keine Absturzberichte, keine Telemetrie
* keine Werbung und keine Werbekennungen
* keine Firebase- oder Google-Play-Services-Bibliotheken
* keine Update- oder Lizenzprüfung
* kein Server des Entwicklers

Die Avatar-Adressen liefert WordPress selbst im Feld `author_avatar_urls`.
Bei einer Standardinstallation zeigen sie auf Gravatar, einen Dienst von
Automattic. Das ist eine Verbindung zu einem Dritten – deshalb ist die Anzeige
von Avataren standardmäßig aus und muss bewusst eingeschaltet werden.

Das Symbol des Blogs, das in Kopfleiste, Einstellungen und als großes Bild in
den Benachrichtigungen erscheint, stammt vom eigenen Blog. Die
Benachrichtigungen nehmen es aus dem lokalen Bildspeicher der App; eine
Verbindung zu einem Dritten entsteht dadurch nicht.

### Optional: Sofortmeldung über UnifiedPush

Wer in den Einstellungen eines Blogs „Sofort melden“ einschaltet, bezieht
weitere Beteiligte ein. Die App selbst verbindet sich auch dann nur mit dem
eigenen Blog; die übrigen Verbindungen bauen die UnifiedPush-App und das
Plugin auf:

| Beteiligter | Was er erfährt |
|---|---|
| Die UnifiedPush-App auf dem Telefon (etwa ntfy) | Dass Commentator je Blog Weckrufe empfangen möchte, und den Namen des Blogs, unter dem sie die Anmeldung anzeigt. Sie vergibt dafür eine Endpoint-Adresse und reicht eingehende Nachrichten an die App weiter. |
| Der Push-Server dieser App (etwa `ntfy.sh` oder ein eigener ntfy) | Dass und wann auf dem Blog kommentiert wurde – sonst nichts. Die Nachricht besteht nur aus dem Wort „new“. |
| Das Plugin auf dem Blog | Die Endpoint-Adresse. Die App hinterlegt sie dort und nimmt sie beim Ausschalten, beim Entfernen des Blogs oder bei einer Abmeldung durch die UnifiedPush-App wieder zurück. |

Bei jedem neuen Kommentar, der nicht als Spam oder Papierkorb eingeht, schickt
das Plugin an diese Adresse einen Weckruf ohne Inhalt: kein Name, kein Text,
keine Kennung. Die Nachricht ist bewusst unverschlüsselt – es steht nichts
darin, was eine Verschlüsselung schützen könnte. Die Kommentare holt die App
danach wie gewohnt selbst über die REST-API. Google und Firebase sind an
keiner Stelle beteiligt.

Wer den Push-Server selbst betreibt, behält auch den Zeitpunkt der Kommentare
bei sich. Ist die Sofortmeldung aus, findet nichts davon statt.

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

In den angezeigten Benachrichtigungen selbst steht neben dem sichtbaren Text
der Status des Kommentars zum Meldezeitpunkt. Die Hintergrundprüfung braucht
ihn, um erledigte Meldungen abzuräumen.

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
Blog-Konfiguration und der Zugangsdatensatz. War die Sofortmeldung
eingeschaltet, nimmt die App zuvor ihre Push-Adresse beim Plugin zurück und
meldet sich bei der UnifiedPush-App ab. Ist danach keine Instanz mehr
eingerichtet, wird zusätzlich der Keystore-Schlüssel entfernt.

## Berechtigungen

Von der App selbst angefordert:

| Berechtigung | Wofür |
|---|---|
| `INTERNET` | Zugriff auf die WordPress-REST-API |
| `ACCESS_NETWORK_STATE` | Offline-Erkennung und Bedingung für die Hintergrundprüfung |
| `POST_NOTIFICATIONS` | Hinweis auf neue Kommentare (ab Android 13, wird zur Laufzeit erfragt) |

Beim Zusammenführen der Manifeste kommen drei weitere aus `androidx.work`
hinzu. Sie stehen nicht im Manifest der App, landen aber im fertigen APK und
sollen deshalb hier stehen:

| Berechtigung | Wofür |
|---|---|
| `WAKE_LOCK` | WorkManager hält das Gerät wach, solange eine Hintergrundprüfung läuft |
| `RECEIVE_BOOT_COMPLETED` | stellt die geplante Prüfung nach einem Neustart wieder her; ohne sie bliebe sie liegen, bis die App wieder geöffnet wird |
| `FOREGROUND_SERVICE` | von WorkManager für beschleunigte Arbeit deklariert. Die App nutzt das nicht, die Bibliothek deklariert es aber pauschal |

Dazu kommt `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` – eine
Berechtigung, die WorkManager für sich selbst definiert, damit seine internen
Broadcast-Empfänger für andere Apps unerreichbar bleiben. Sie ist auf
Signaturebene und für Dritte nicht erlangbar.

Keine dieser Berechtigungen eröffnet Zugriff auf Nutzerdaten, und keine
verlangt eine Zustimmung zur Laufzeit außer `POST_NOTIFICATIONS`.

Nicht dabei: kein Speicherzugriff, keine Kontakte, kein Standort, keine
Kamera, kein Mikrofon, kein Hintergrundstandort, kein Wecker, keine exakten
Alarme, keine Abfrage aller installierten Apps.

Der UnifiedPush-Connector fordert keine Berechtigung an. Er deklariert im
Manifest lediglich `<queries>` für die drei UnifiedPush-Aktionen `LINK`,
`REGISTER` und `UNREGISTER`. Damit sieht die App nur Apps, die sich als
UnifiedPush-Verteiler anbieten – sie muss sie finden, um sich anzumelden.

Der Debug-Build enthält zusätzlich den Empfänger `DebugSyncReceiver`, der die
Hintergrundprüfung sofort anstößt. Er ist mit `android.permission.DUMP`
geschützt und damit nur über `adb` erreichbar; im Release-Build fehlt er.


## Datenverarbeitung durch das WordPress-Plugin

Das optionale Plugin `commentator-bridge` ändert kein Verhalten von
WordPress, legt keine Tabellen an und setzt keine Cookies. Seine Endpunkte
sind in [`api.md`](api.md#endpunkte-des-optionalen-plugins) beschrieben.

Gelesen wird:

* `status` und `summary` liefern ausschließlich Zahlen sowie Kennung und
  Zeitstempel der neuesten Kommentare – keine Kommentarinhalte.
* `team` liefert die Rollen, die schreiben oder moderieren dürfen, und je
  Mitglied Benutzerkennung und Rollen – keine Namen, keine E-Mail-Adressen.

Geschrieben wird an zwei Stellen:

* `/blocklist` ändert die Option `disallowed_keys`, also die Sperrliste unter
  Einstellungen → Diskussion. Sie kann E-Mail-Adressen, Namen oder IP-Adressen
  von Kommentierenden enthalten – genau wie bei einem Eintrag über das
  Backend. Verlangt `manage_options`.
* `/push` legt die Push-Adressen des angemeldeten Kontos in dessen Benutzermeta
  `commentator_push_endpoints` ab, höchstens fünf; die älteste fällt heraus.
  Die Adresse ist eine Endpoint-URL des Push-Servers und enthält keine
  Kommentardaten.

`/empty` löscht Spam oder Papierkorb endgültig, wie die gleichnamigen Knöpfe
im Backend.

**Nach außen** sendet das Plugin nur, wenn mindestens ein Konto mit
`moderate_comments` eine Push-Adresse hinterlegt hat. Dann geht bei jedem
neuen Kommentar, der nicht als Spam oder Papierkorb eingeht, an jede
hinterlegte Adresse ein nicht blockierender `POST` mit dem Rumpf „new“ – ohne
Namen, Text oder Kennung des Kommentars. Angenommen werden nur öffentlich
erreichbare HTTPS-Adressen; versendet wird über `wp_safe_remote_post`, das
Ziele im eigenen Netz ablehnt. Ohne hinterlegte Adresse sendet das Plugin
nichts.
