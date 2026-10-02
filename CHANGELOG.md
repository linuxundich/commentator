# Changelog

Alle nennenswerten Änderungen an diesem Projekt werden hier festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/).
Versionsnummern folgen [Semantic Versioning](https://semver.org/lang/de/).

Es werden bewusst keine Versionsnummern für noch nicht veröffentlichte Stände
vergeben. Alles Unveröffentlichte steht unter `[Unreleased]`.

## [Unreleased]

### Added

**Android-App**

- Ist die Sofortmeldung für jeden benachrichtigenden Blog eingerichtet, prüft
  die App nur noch alle sechs Stunden nach (oder seltener, wenn so
  eingestellt). Die Prüfung bleibt als Sicherheitsnetz für verlorene
  Weckrufe. Erst eine beim Plugin hinterlegte Adresse zählt.
- „Sofort melden“ nennt den Server der Push-Adresse. Beim öffentlichen
  ntfy.sh steht dazu, wie man auf einen eigenen ntfy-Server wechselt: dort als
  Standardserver eintragen, dann die Sofortmeldung aus- und wieder einschalten.

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
- Lokaler Room-Cache für Kommentare, Beitragstitel, Zählungen je Filter und
  Synchronisierungszustand.
- Die Rollenmarken des Teams stehen sofort da. Wer zum Team gehört, wird
  ebenfalls gespeichert: Vorher kam die Zuordnung erst nach dem
  Aktualisieren, die Kommentare des Teams standen beim Start also kurz ohne
  Marke, und eingeklappte Rollen klappten erst nachträglich zu. Ohne
  Verbindung gilt jetzt der zuletzt bekannte Stand, statt dass niemand als
  Team gilt – das betraf auch die Hintergrundprüfung, die sonst ausgerechnet
  die stummgeschalteten Rollen gemeldet hätte.
- Der Posteingang öffnet mit dem zuletzt gewählten Filter. Blogübergreifend,
  wie der Filter selbst: Beim Wechsel des Blogs bleibt er ohnehin stehen, und
  je Blog gespeichert spränge er beim Umschalten. Geholt wird er, bevor etwas
  geladen wird – sonst finge die Liste beim Posteingang an und spränge gleich
  darauf um.
- Der Posteingang zeigt beim Öffnen sofort den zuletzt bekannten Stand: Die
  Zahlen der Filterleiste stehen aus dem Zwischenspeicher da, bevor der Server
  geantwortet hat, und ein Filter, der beim letzten Mal leer war, zeigt den
  Leerzustand statt sekundenlang Platzhalterkarten. Dafür wird die Anzahl je
  Filter mitgespeichert: Eine leere Tabelle allein kann „hier ist nichts“ nicht
  von „hier wurde noch nichts geladen“ unterscheiden, eine festgehaltene 0
  schon. Aktualisiert wird trotzdem bei jedem Start – der gespeicherte Stand
  überbrückt nur die Wartezeit, er ersetzt den Abruf nicht.
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
- Aktionen in der Benachrichtigung: „Antworten“, „Freigeben“ und „Spam“.
  Geantwortet wird direkt in der Benachrichtigung über `RemoteInput`; bei
  einem offenen Kommentar heißt der Knopf „Freigeben und antworten“.
  „Freigeben“ steht nur an offenen Kommentaren, „Spam“ nicht an Spam. Die
  Knöpfe erscheinen nur bei Konten mit `moderate_comments`. Der nicht
  exportierte `NotificationActionReceiver` reicht an den
  `NotificationActionWorker` weiter – beschleunigte WorkManager-Arbeit, je
  Kommentar höchstens eine laufende Aktion (`ExistingWorkPolicy.KEEP`) – und
  der nutzt dieselben Use Cases wie die Oberfläche. Bei einem Fehler wird
  bewusst nicht wiederholt: Eine später still nachgeholte Moderation könnte
  eine inzwischen im Web getroffene Entscheidung überschreiben. Stattdessen
  bleibt die Benachrichtigung stehen und nennt den Grund; eine gescheiterte
  Antwort steht mit ihrem Text darin, damit nichts Geschriebenes verloren
  geht. Nach einer Direktantwort hält Android ab Version 15 die Meldung fest
  (`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`) und übergeht ein bloßes
  `cancel()`; sie wird deshalb durch eine kurze Bestätigung „Erledigt“
  ersetzt, die nach zwei Sekunden über `setTimeoutAfter` verschwindet.
- Benachrichtigungen räumen sich auf. Wird ein Kommentar in der App
  moderiert, beantwortet oder geöffnet, verschwindet seine Meldung (Schnittstelle
  `CommentAlerts`, umgesetzt von `CommentNotifier`). Wurde er im Web
  moderiert, gleicht die Hintergrundprüfung die offenen Meldungen ab, bevor
  sie Neues meldet – mit genau einer Anfrage
  (`include=<ids>&status=any`) und nur, wenn überhaupt Meldungen offen sind.
  Weg kommt, was gelöscht, Spam oder im Papierkorb ist, und was als offen
  gemeldet und inzwischen freigegeben wurde. Was schon freigegeben gemeldet
  wurde, bleibt stehen: Es wartet womöglich noch auf eine Antwort. Den Status
  zum Meldezeitpunkt trägt die Benachrichtigung in ihren Extras. Eine leer
  gewordene Sammelmeldung verschwindet mit.
- Filter „Unbeantwortet“ zwischen „Offen“ und „Genehmigt“: freigegebene
  Kommentare von Lesern, unter denen keine freigegebene Antwort aus dem Team
  oder vom eigenen Konto steht. Kommentare des Teams selbst erscheinen dort
  nicht. WordPress kennt diesen Zustand nicht; die App berechnet ihn per
  Room-Abfrage über den Zwischenspeicher. Abgerufen wird wie bei „Genehmigt“,
  ergänzt um eine Anfrage nach den Antworten auf die geladenen Kommentare
  (`status=approve&parent=<ids>`). Der Filter wirkt deshalb nur über die
  geladenen Seiten und trägt keine Zahl – der Server kann sie nicht zählen.
  Vorbild ist der gleichnamige Filter der WordPress-App.
- Das Symbol des Blogs steht als großes Bild in jeder
  Kommentar-Benachrichtigung und in der Sammelmeldung. Es kommt über Coil aus
  demselben Bildspeicher wie in Kopfleiste und Einstellungen
  (`SiteIconLoader`) und stammt vom eigenen Blog; eine Verbindung zu einem
  Dritten entsteht nicht.
- Sofortmeldung über UnifiedPush, je Blog in dessen Einstellungen unter
  „Sofort melden“ einschaltbar. Die App meldet sich je Blog bei einer
  UnifiedPush-App auf dem Telefon an, etwa ntfy, und hinterlegt die
  erhaltene Adresse beim Plugin (`POST /commentator/v1/push`). Beim
  Ausschalten, beim Entfernen des Blogs, bei einer Abmeldung durch die
  UnifiedPush-App und beim Ersetzen der Adresse wird sie dort wieder
  zurückgenommen. Jede eingehende Nachricht stößt eine einmalige,
  beschleunigte Prüfung aller Blogs an (`CommentSyncWorker`, eindeutig als
  „commentator-push-sync“); die Nachricht selbst ist bewusst inhaltslos und
  unverschlüsselt. Die regelmäßige Prüfung läuft weiter und holt verlorene
  Weckrufe ein. Kein Google, kein Firebase. Die Einstellung nennt ihren
  Zustand: braucht das Plugin ab 1.5, braucht eine UnifiedPush-App, wird
  eingerichtet, aktiv über die gewählte App oder der Grund eines Fehlers.
  Neue Abhängigkeit `org.unifiedpush.android:connector` 3.3.5, die Google Tink
  mitbringt. Damit ist der Backlog-Punkt „Echtes Push über FCM“ durch eine
  Lösung ohne Google ersetzt.

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

- Version 1.6.0: Auch eine Statusänderung (`transition_comment_status`) weckt
  die App, mit dem Rumpf „status“ und `Urgency: normal`. Eine im Backend
  erledigte Moderation nimmt die Benachrichtigung damit sofort zurück statt
  erst bei der nächsten regelmäßigen Prüfung; am Emulator gemessen 0,6 s.
  Änderungen aus der App selbst wecken nicht – sie hat die Meldung schon
  zurückgenommen; erkannt wird das an ihrem User-Agent `Commentator/`.
- Version 1.5.0: `commentator/v1/push` nimmt Push-Adressen entgegen (`POST`)
  und gibt sie wieder frei (`DELETE`). Bei jedem neuen Kommentar
  (`wp_insert_comment`) geht an jede hinterlegte Adresse aller Konten mit
  `moderate_comments` ein nicht blockierender Weckruf mit dem Rumpf „new“ –
  kein Name, kein Text, keine Kennung –, mit den Kopfzeilen `TTL: 3600` und
  `Urgency: high`. Als Spam oder Papierkorb eingegangene Kommentare wecken
  niemanden. Angenommen werden nur öffentliche HTTPS-Adressen
  (`wp_http_validate_url`, Versand über `wp_safe_remote_post`), höchstens
  fünf je Konto; die älteste fällt heraus. Abgelegt werden sie in der
  Benutzermeta `commentator_push_endpoints`. Ohne hinterlegte Adresse sendet
  das Plugin nichts nach außen.
- `commentator-bridge` mit den Leseendpunkten `commentator/v1/status` und
  `commentator/v1/summary`, beide abgesichert über `moderate_comments`.

**Entwicklung und Tests**

- Room exportiert sein Schema nach `android/app/schemas/`, damit spätere
  Migrationen automatisiert geprüft werden können.
- `DebugSyncReceiver` stößt die Hintergrundprüfung über `adb` sofort an.
  WorkManager zieht periodische Arbeit nicht vor; ohne den Empfänger hieß
  Testen, bis zu 15 Minuten zu warten. Er existiert nur im Debug-Build und ist
  mit `android.permission.DUMP` geschützt.
- README: Einrichtung eines Emulators ohne Android Studio, Betrieb der
  Testumgebung mit Podman statt Docker und Zugriff aus dem Emulator über
  `adb reverse`.

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

- Eine Antwort auf einen offenen Kommentar blieb auf der Website unsichtbar:
  Der Elternkommentar stand weiter auf „ausstehend“, und WordPress zeigt
  Antworten darunter nicht an – gegen eine lokale WordPress-Installation
  nachgeprüft. Die App gibt einen offenen Kommentar jetzt zuerst frei und
  antwortet dann, wie das WordPress-Backend. Der Knopf in der Detailansicht
  heißt in diesem Fall „Freigeben und antworten“. Die Reihenfolge ist
  Absicht: Scheitert die Freigabe, ist noch nichts geschehen, und ein zweiter
  Versuch erzeugt keine doppelte Antwort.
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
  unterschieden. Jede Rolle hat einen eigenen Farbton aus einer abgestimmten
  Auswahl – alle bewusst blass, damit eine Wortmeldung der eigenen Redaktion
  nicht nach Warnung aussieht. Die Marke nennt die Rolle beim Namen, der
  Unterschied hängt damit nicht allein an der Farbe. Welche Rollen als Team
  zählen, ist in den Einstellungen wählbar; voreingestellt sind Administrator
  und Redakteur. Das eigene Konto zählt immer dazu.
- Ein Einstellungsdialog je Rolle: ob sie als Team gilt, welchen Farbton sie
  bekommt und ob sie überhaupt eingefärbt wird, ob ihre Kommentare einzeln in
  der Liste stehen und ob sie auf dem Gerät benachrichtigen. Die vier
  Standardrollen – Administrator, Redakteur, Autor, Mitarbeiter – stehen auch
  ohne das Bridge-Plugin zur Auswahl.
- Rollen, die nicht einzeln in der Liste stehen sollen, werden dort
  eingeklappt statt ausgeblendet: Aufeinanderfolgende Beiträge stehen als eine
  Zeile da, die Anzahl und Rollen nennt und sich antippen lässt. Dass es
  Wortmeldungen aus dem Team gab, bleibt damit sichtbar – ganz zu verschwinden
  wäre schlechter als jede Filterung, man wüsste nicht einmal, dass etwas
  fehlt. Der Kommentar, auf den eine Antwort sich bezieht, bleibt immer
  sichtbar; eingeklappt fehlte genau der Bezug, dessentwegen er geladen wurde.
- Benachrichtigungen je Rolle abschaltbar. Stummgeschaltete Kommentare werden
  trotzdem vermerkt, sonst holte sie der Hintergrunddienst bei jedem Lauf
  erneut vom Blog.
- Über die eigenen Beiträge meldet die App voreingestellt nicht: Wer gerade
  geantwortet hat, weiß davon, und die Meldung käme erst mit der nächsten
  Hintergrundprüfung. Einschalten lässt sich das im Dialog „Eigenes Konto“.
  Die eigenen Beiträge entscheiden sich immer an diesem Eintrag, auch wenn das
  Plugin die tatsächliche Rolle kennt – sonst wäre der Schalter ausgerechnet
  dort wirkungslos, und die eigenen Antworten ließen sich nur zusammen mit
  denen aller anderen Administratoren stummschalten.
- Wischgesten in der Liste: nach rechts genehmigen, nach links als Spam
  markieren. Beides ist rücknehmbar, und die Schaltflächen auf der Karte
  bleiben erhalten – eine Geste ist nie der einzige Weg zu einer Aktion.
  Endgültiges Löschen ist bewusst nicht dabei. Unter der Karte wird ab der
  ersten Bewegung farbig angezeigt, was die Geste auslösen würde. Symbol und
  Beschriftung blenden auf, sobald sie vollständig neben die Karte passen –
  ein angeschnittenes Symbol oder ein mitten im Wort abgeschnittenes Wort sagt
  weniger als gar keines. Ist weit genug gezogen, damit das Loslassen die
  Aktion auslöst, gibt es einen kurzen Impuls, und die Anzeige wächst leicht.
- Blog-Symbol und Blogname sitzen jetzt auf einer Linie. Zentriert wurde
  zuvor nicht die Schrift, sondern ihr Kasten – und der enthält den
  Zeilenabstand und Platz für Unterlängen, die ein Blogname oft gar nicht
  hat. Am Gerät nachgemessen: aus 2 px Versatz wurde 1 px.
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
- Mehrere Blogs in einer App. Die Datenhaltung war darauf von Anfang an
  angelegt – jede Zeile trägt eine `instanceId`, Zugangsdaten liegen je Blog,
  der HTTP-Client wird je Blog erzeugt –, deshalb kam die Erweiterung ohne
  Datenmigration aus.
  - Umschalter in der Kopfleiste des Posteingangs. Er erscheint erst ab dem
    zweiten Blog; bei einem wäre ein Pfeil über einer Liste mit einem Eintrag
    ein Versprechen ohne Inhalt. Das Blatt nennt zu jedem Blog Symbol, Name
    und Adresse – zwei Blogs können denselben Namen tragen, die Adresse
    unterscheidet sie immer – und die Anzahl offener Kommentare aus dem
    Zwischenspeicher. Ein Blog, der noch nie geladen wurde, zeigt keine Zahl
    statt einer falschen Null.
  - Liste aller eingerichteten Blogs in den Einstellungen, mit Weg zum
    Hinzufügen weiterer. Dieselbe Einrichtung wie beim ersten Blog, nur mit
    einem Zurück – beim ersten liegt dahinter nichts.
  - Eigener Einstellungsbildschirm je Blog: Adresse, Konto, Plugin-Erkennung,
    ob der Blog meldet und worüber, seine Rollen und Farben, seine
    Textbausteine, und das Entfernen genau dieses Blogs. Alles zusammen auf
    einem Bildschirm hätte bei jeder Zeile die Frage offen gelassen, für
    welchen Blog sie gilt.
  - Benachrichtigungen je Blog abschaltbar, mit eigenem Umfang. Ein
    Nebenprojekt darf still bleiben, während der Hauptblog meldet. Der
    Hauptschalter und der Prüftakt gelten weiterhin für alle.
  - Die Hintergrundprüfung geht in einem Durchgang alle Blogs durch, statt je
    Blog eine eigene Arbeit zu planen: Das Gerät wacht einmal auf statt n-mal.
    Fehler werden je Blog behandelt – ein Blog mit abgelehnten Zugangsdaten
    oder ohne Verbindung hält die Meldungen der übrigen nicht auf.
  - Rollen, Farben und Textbausteine gelten je Blog: Die Rollen einer
    Redaktion sind nicht die eines Kundenprojekts, und der Ton auch nicht.
    Was vor dem Umstieg blogübergreifend eingestellt war, gilt weiter – die
    alten Schlüssel werden gelesen, geschrieben wird von da an je Blog.
  - Eine erneute Anmeldung bei einem bereits eingerichteten Blog aktualisiert
    dessen Eintrag, statt einen zweiten anzulegen. Ein zweiter Eintrag würde
    nicht nur doppelt in der Liste stehen, er würde auch jeden vorhandenen
    Kommentar noch einmal als neu melden.
  - Benachrichtigungen tragen den Blog als Marke neben der aus der
    Kommentar-ID abgeleiteten Kennung. IDs sind nur innerhalb eines Blogs
    eindeutig; ohne diese zweite Dimension hätte der Kommentar 5 des einen
    Blogs die Meldung zum Kommentar 5 des anderen ersetzt. Bei mehreren Blogs
    nennt die Unterzeile auch den Blog, bei einem bleibt sie, wie sie war.
  - Eine angetippte Benachrichtigung wechselt zum betreffenden Blog. Sonst
    stünde hinter dem geöffneten Kommentar der Posteingang eines anderen.
- Die Oberfläche folgt Material 3 Expressive.
  - **Bewegung** als Federphysik statt fester Dauern. Eine Feder kennt die
    Geschwindigkeit, mit der eine Bewegung ankommt, und läuft daraus weiter;
    eine Kurve über 200 ms beginnt immer bei Null, auch wenn der Finger das
    Element gerade noch geschoben hat. Getrennt nach räumlich (Platz und
    Größe, schwingt leicht über) und Effekt (Deckkraft und Farbe, schwingt
    nicht über - eine überschwingende Deckkraft müsste über 100 % hinaus und
    flackerte nur). Das Schema kommt aus dem Theme, nicht aus Konstanten in
    den Komponenten, damit keine Stelle bei ihrer eigenen Zeitangabe bleibt.
  - **Filterleiste als verbundene Gruppe** aus `ToggleButton`: außen rund,
    innen fast gerade, und damit als ein Ding erkennbar statt als fünf
    einzelne Marken. Jeder Schalter hat drei Formen - ruhend, unter dem
    Finger, ausgewählt - und wandelt zwischen ihnen, statt sie zu tauschen.
    Die Auswahl trägt damit ihre Form und nicht nur ihre Farbe; wer Farben
    schlecht unterscheidet, sieht an einer eingefärbten Marke nichts, an einer
    runden schon.
  - **Formenskala** mit größerer Spannweite: zurückhaltend dort, wo viele
    Elemente nebeneinanderliegen, rund dort, wo eine Fläche für sich steht.
    Die Kommentarliste bleibt bewusst dicht - sie ist der Arbeitsbereich, und
    jeder Millimeter Radius kostet dort nutzbare Breite.
  - **Betonte Typografie**: mehr Gewicht auf Titeln, Überschriften und
    Beschriftungen, damit die Gliederung beim Überfliegen erkennbar ist. Der
    Kommentartext selbst bleibt unberührt - er wird gelesen, nicht
    überflogen.
  - **Ladeanzeige** als Folge wandelnder Formen statt eines sich drehenden
    Kreises.
  - Das Auf- und Zuklappen zusammengefasster Team-Beiträge ist animiert; es
    wechselte zuvor ohne Übergang, und es war nicht zu sehen, woher die neuen
    Karten kamen. Der Pfeil dreht sich, statt gegen ein zweites Zeichen
    getauscht zu werden.
  - Dafür hängt `compose-material3` als einziges Compose-Artefakt an einer
    eigenen Version (1.5.0-alpha29) statt an der BOM. In der stabilen Linie
    1.4.0 ist die gesamte Expressive-API Kotlin-`internal` und aus App-Code
    nicht aufrufbar; die neuen Komponenten fehlen dort ganz. Begründung und
    Preis stehen in `docs/architecture.md`.
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
  Kommentare liegt hinter der Schnittstelle `NewCommentSource`. Die optionale
  Sofortmeldung über UnifiedPush stößt nur diese Prüfung früher an und kam
  deshalb ohne Eingriff in die Benachrichtigungslogik aus.
