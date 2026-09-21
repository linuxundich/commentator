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

- Version 1.4.0: `commentator/v1/team` nennt die Rollen des Blogs und ihre
  Mitglieder. Ohne diesen Endpunkt kann die App das Team nicht erkennen –
  `wp/v2/users` mit `context=edit` verlangt `list_users`, und das hat ein
  Redakteur nicht. Geliefert werden nur Rollen, die Beiträge schreiben oder
  moderieren dürfen; Abonnenten gehören nicht dazu.
- Version 1.3.0: `commentator/v1/status` meldet zusätzlich den neuesten
  Kommentar unabhängig vom Status. Ohne dieses Feld könnte die App auf Blogs
  mit automatischer Freischaltung nicht abkürzen. Ältere Fassungen sind
  weiterhin nutzbar; die App fragt dann regulär über die Kern-API.
- Der neueste Kommentar wird nach Kennung statt nach Datum bestimmt. Die App
  vergleicht Kennungen; ein zurückdatierter Kommentar – beim Import keine
  Seltenheit – wäre sonst der „neueste“ gewesen und hätte die Prüfung
  fälschlich abbrechen lassen.
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

- Rollenmarke und Statuskennzeichen stehen nebeneinander auf einer Linie,
  mit gleicher Höhe und Form. Zuvor saßen sie versetzt übereinander und waren
  unterschiedlich hoch.
- Die Rollenmarke steht jetzt unter dem Namen statt daneben. Neben Marke und
  Statuskennzeichen blieben für den Namen kaum 80 dp – „Christoph Langner"
  wurde zu „Christop…".

- Erste Datenbankmigration des Projekts: Die Kommentartabelle bekommt die
  Nutzer-ID des Verfassers. Bewusst eine Migration statt eines Neuaufbaus –
  mit dem Cache ginge der Ausgangszustand der Benachrichtigungen verloren, und
  beim nächsten Lauf käme ein Schwall über alle vorhandenen Kommentare.

- Kommentare aus dem eigenen Team werden abgesetzt dargestellt, nach Rolle
  unterschieden: Administratoren in Rot, übrige Rollen in einem eigenen Ton.
  Die Marke nennt die Rolle beim Namen – der Unterschied hängt damit nicht
  allein an der Farbe. Welche Rollen dazuzählen, ist in den Einstellungen
  wählbar; voreingestellt sind Administrator und Redakteur. Das eigene Konto
  zählt immer dazu.
- Wischgesten in der Liste: nach rechts genehmigen, nach links als Spam
  markieren. Beides ist rücknehmbar, und die Schaltflächen auf der Karte
  bleiben erhalten – eine Geste ist nie der einzige Weg zu einer Aktion.
  Endgültiges Löschen ist bewusst nicht dabei. Unter der Karte wird ab der
  ersten Bewegung farbig angezeigt, was die Geste auslösen würde. Symbol und
  Beschriftung blenden auf, sobald sie vollständig neben die Karte passen –
  ein angeschnittenes Symbol oder ein mitten im Wort abgeschnittenes Wort sagt
  weniger als gar keines. Ist weit genug gezogen, damit das Loslassen die
  Aktion auslöst, gibt es einen kurzen Impuls, und die Anzeige wächst leicht.
- Das Blog-Symbol wird auch dann angezeigt, wenn der Blog kein
  WordPress-Site-Icon gesetzt hat. Viele Blogs bringen ihr Symbol im Theme
  mit und tragen es nur als `<link rel="icon">` in den Seitenkopf ein – für
  die REST-API ist es dann unsichtbar, obwohl es im Browser überall
  auftaucht. Gesucht wird nur, wenn kein Site-Icon gesetzt ist, und genommen
  wird nur ein Bild auf demselben Rechner wie der Blog: Ein Symbol von einem
  Auslieferungsnetz wäre eine Verbindung zu einem Dritten. `.ico` und `.svg`
  scheiden aus, weil Android sie nicht zeichnen kann.
- Die Liste im Posteingang steht als Gesprächsfaden: Antworten stehen
  eingerückt unter dem Kommentar, auf den sie sich beziehen, und Fäden mit
  neuen Beiträgen stehen oben. Weil beim Filter „Offen" der Kommentar davor
  meist längst genehmigt ist und dort gar nicht auftauchen würde, wird er
  eigens nachgeholt und gedämpft als Zusammenhang gezeigt – ohne
  Schaltflächen, denn er gehört nicht zum Filter. Abschaltbar in den
  Einstellungen; dann bleibt es bei der rein chronologischen Liste. Die
  Trefferliste einer Suche bleibt immer flach.
- Kommentarsuche über die Kopfleiste. Gesucht wird auf dem Server über den
  `search`-Parameter der WordPress-API und innerhalb des gewählten Filters –
  die Filterleiste behält damit ihre Bedeutung. Lokal zu suchen wäre
  schneller, durchsuchte aber nur, was zufällig im Zwischenspeicher liegt.
  Gesucht wird erst, wenn die Eingabe kurz steht, und ab zwei Zeichen; sonst
  wäre jeder Tastendruck eine eigene Anfrage. Die Treffer kommen wie die
  Liste aus dem Zwischenspeicher, deshalb wirkt eine Moderation auch in der
  Trefferliste sofort.
- Beschriftungsspalten richten sich nach der gemessenen Textbreite statt nach
  einer festen Angabe. Bei 200 % Systemschriftgröße wurde aus „Beitrag" zuvor
  ein „Beitr / ag" – ein Umbruch mitten im Wort. Ein Test hält das für die
  Detailansicht fest.
- Beschriftung und Wert werden für Bildschirmleser zusammen vorgelesen
  („Beitrag Hello world!") statt als zwei Stationen, von denen die erste für
  sich nichts aussagt.
- Die Filtermarken sagen Bildschirmlesern die Zahl ausgeschrieben an („Alle,
  7 Kommentare"); auf dem Bildschirm steht weiterhin „Alle · 7". Der
  Mittelpunkt trennt fürs Auge, vorgelesen ergibt er nichts.
- Die Blog-Zeile in den Einstellungen steht wie die Zeilen darunter mit der
  Beschriftung über dem Wert.
- Adressen, die unverlinkt im Text stehen, zählen jetzt als Verweis mit.
  Zuvor wurde nur das Markup betrachtet – WordPress verlinkt aber nicht jede
  Adresse, und ein Kommentar mit ausgeschriebener URL blieb damit unauffällig.
  Eine verlinkte Adresse zählt weiterhin nur einmal.
- Schalter, um Kommentare des Teams aus allen Übersichten auszublenden – sie
  müssen in der Regel nicht moderiert werden. Der Ausschluss geschieht
  serverseitig über `author_exclude` und wirkt deshalb auch auf die Zahlen an
  den Filtern und auf das Nachladen weiterer Seiten.
- Beim Erstaufbau der Liste liefen zwei Ladeanzeigen gleichzeitig: die des
  Herunterziehens und die der Liste. Statt eines Kreises im Leeren stehen
  jetzt Platzhalterkarten in der Form der späteren Inhalte; die Liste springt
  dadurch beim Eintreffen der Daten nicht mehr.
- Die Aktionen auf einer Kommentarkarte standen als vier beschriftete
  Schaltflächen auf zwei Zeilen. Jetzt trägt eine hervorgehobene Hauptaktion
  die Beschriftung – genehmigen, bei bereits genehmigten Kommentaren
  antworten –, die übrigen stehen als Symbole daneben. Auf denselben
  Bildschirm passen dadurch drei Kommentare statt zweieinhalb.
- Die Kopfleiste des Posteingangs weicht beim Scrollen nach oben und kommt
  beim Zurückscrollen sofort wieder.
- Das Symbol in der Statusleiste füllte nur rund die Hälfte seiner Fläche und
  erschien neben den Symbolen anderer Apps als kaum erkennbarer Punkt. Es
  füllt jetzt gut 90 Prozent, wie für Statusleistensymbole vorgesehen, und
  zeigt dieselbe Blasenform wie das App-Symbol.
- App-Symbol nach dem Muster gängiger Messenger überarbeitet: fast
  quadratischer Blasenkörper mit großem Eckradius statt eines länglichen, und
  ein kurzer angewachsener Schweif statt eines dünnen Stachels. Die Marke hält
  jetzt rund 5 Einheiten Abstand zur Sicherheitszone statt 1,8.

- Benachrichtigungen lassen sich in drei Stufen einstellen: nur was auf
  Moderation wartet, jeder neue Kommentar (Voreinstellung), oder zusätzlich
  Spam und Papierkorb. Zuvor wurde nur die Moderationswarteschlange geprüft –
  auf Blogs, die Kommentare automatisch freischalten, kam deshalb nie eine
  Benachrichtigung an. Was ein Spamfilter aussortiert hat, bleibt in der
  Voreinstellung außen vor; die dritte Stufe macht Fehleinstufungen sichtbar.
- Kommentatoren ohne Gravatar bekommen ein eigenes Platzhaltersymbol statt der
  Silhouette von Gravatar. Dafür fragt die App `d=404` statt `d=mm` an.
  Avatar-Adressen anderer Dienste bleiben unangetastet.
- Umschalten zwischen den Filtern kostet keine Netzanfragen mehr, solange der
  Stand jünger als zwei Minuten ist. Zuvor löste jeder Tipp auf einen Filter
  bis zu neun Anfragen aus – Liste, Beitragstitel, Rechteabfrage und
  Zählungen –, obwohl sich beim reinen Umschalten nichts davon ändern kann.
  Gemessen: vorher 7 bis 9 Anfragen je Wechsel, jetzt 2 beim ersten Besuch
  eines Filters und 0 bei jedem weiteren. Ausdrückliches Aktualisieren holt
  weiterhin alles.
- Bilder werden auf der Platte zwischengespeichert. Blog-Symbol und Avatare
  wurden zuvor nach jedem Neustart neu geladen.
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
