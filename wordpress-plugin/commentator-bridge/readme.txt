=== Commentator Bridge ===
Contributors: christophlangner
Tags: comments, moderation, rest-api
Requires at least: 6.0
Tested up to: 6.9
Requires PHP: 7.4
Stable tag: 1.7.0
License: MIT
License URI: https://opensource.org/licenses/MIT

Schlanke REST-Endpunkte für die Android-App Commentator.

== Beschreibung ==

Die Android-App Commentator prüft regelmäßig, ob es neue moderationsbedürftige
Kommentare gibt. Ohne dieses Plugin ruft sie dafür die Kommentarliste der
WordPress-Kern-API ab. Das funktioniert, ist aber für eine reine
"Gibt es etwas Neues?"-Frage unnötig aufwendig. Außerdem kennt die Kern-API
keine Sammellöschung, keinen Zugriff auf die Sperrliste und keinen Weg, das
Team eines Blogs zu erkennen, wenn das Konto nur Redakteur ist.

Das Plugin ist optional. Ohne es funktioniert die App vollständig, sie stellt
dann lediglich etwas teurere Anfragen an die Kern-API.

Alle Endpunkte liegen unter `/wp-json/commentator/v1/` und verlangen dieselbe
Authentifizierung wie die Kern-API:

* `GET /status` liefert die Anzahl ausstehender Kommentare sowie Kennung und
  Zeitstempel des neuesten offenen und des neuesten Kommentars überhaupt.
  Verlangt `moderate_comments`.
* `GET /summary` liefert die Kommentaranzahl je Status in einem einzigen
  Aufruf. Verlangt `moderate_comments`.
* `GET /team` nennt die Rollen, die Beiträge schreiben oder Kommentare
  moderieren dürfen, und deren Mitglieder (Benutzerkennung und Rollen).
  Verlangt `moderate_comments`.
* `POST /empty` leert Spam oder Papierkorb endgültig, in Stapeln von
  höchstens 200 Kommentaren – wie die gleichnamigen Knöpfe in der
  Kommentarverwaltung. Verlangt `moderate_comments`.
* `GET`, `POST` und `DELETE /blocklist` lesen und pflegen die Option
  `disallowed_keys`, also die Sperrliste unter Einstellungen → Diskussion.
  Verlangt `manage_options`, weil es eine seitenweite Option ist.
* `POST` und `DELETE /push` hinterlegen beziehungsweise entfernen eine
  UnifiedPush-Adresse für das angemeldete Konto. Verlangt
  `moderate_comments`.

== Sofortmeldung über UnifiedPush ==

Seit Version 1.5.0 kann die App eine UnifiedPush-Adresse hinterlegen, die sie
von einer UnifiedPush-App auf dem Telefon (etwa ntfy) bekommen hat. Bei jedem
neuen Kommentar, der nicht als Spam oder Papierkorb eingeht, schickt das
Plugin an jede hinterlegte Adresse aller Konten mit `moderate_comments` einen
nicht blockierenden Weckruf mit dem Rumpf "new" – ohne Namen, Text oder
Kennung des Kommentars. Die App holt die Kommentare danach wie gewohnt selbst
über die REST-API. Der Push-Server erfährt damit nur, dass und wann
kommentiert wurde.

Seit 1.6.0 weckt auch eine Statusänderung, etwa eine Freigabe im Backend, mit
dem Rumpf "status". Die App nimmt ihre Benachrichtigung zu dem Kommentar dann
sofort zurück. Änderungen, die aus der App selbst kommen, wecken nicht.

Angenommen werden nur öffentlich erreichbare HTTPS-Adressen; versendet wird
über `wp_safe_remote_post`. Je Konto gelten höchstens fünf Adressen, die
älteste fällt heraus. Gespeichert werden sie in der Benutzermeta
`commentator_push_endpoints`. Ohne hinterlegte Adresse passiert nichts.

Weckrufe wegen Statusänderungen gehen an eine Adresse höchstens alle 30
Sekunden; Sammelmoderation im Backend löst so einen statt Dutzender aus. Neue
Kommentare wecken immer.

Im Benutzerprofil zeigt der Abschnitt „Commentator: Sofortmeldung“, wohin der
Blog Weckrufe schickt, mit „Entfernen“ und „Testweckruf senden“.

== Für Admins ==

Es gibt keine Einstellungsseite. Für seltene Fälle:

* `define( 'COMMENTATOR_BRIDGE_DISABLE_PUSH', true );` in der `wp-config.php`
  schaltet die Sofortmeldung ab. Das Plugin nimmt dann keine Adressen an und
  sendet nichts nach außen; alles andere bleibt.
* Laufen Blog und ntfy im selben Netz, erlaubt
  `add_filter( 'commentator_bridge_push_allow_local', '__return_true' );`
  Adressen im lokalen Netz.
* Der Filter `commentator_bridge_push_endpoint_allowed` schränkt Adressen
  weiter ein, etwa auf den eigenen Server:

    add_filter( 'commentator_bridge_push_endpoint_allowed', function ( $ok, $endpoint ) {
        return $ok && 'ntfy.example.org' === wp_parse_url( $endpoint, PHP_URL_HOST );
    }, 10, 2 );

Einen ntfy-Server stellt man nicht hier ein, sondern in der ntfy-App auf dem
Telefon: Die App gibt die Adresse, die sie von dort bekommt, an das Plugin
weiter.

== Was dieses Plugin nicht tut ==

* Es ändert kein Verhalten von WordPress.
* Es sendet nur dann etwas nach außen, wenn ein Moderator die Sofortmeldung
  eingerichtet hat – und dann nichts als einen inhaltslosen Weckruf an die
  hinterlegte Adresse.
* Es schreibt Optionen nur über `/blocklist` (`disallowed_keys`) und
  Benutzermeta nur über `/push` (`commentator_push_endpoints`). Beides ist
  auch über das Backend möglich und verlangt dieselben Rechte.
* Es legt keine Tabellen an und setzt keine Cookies.
* Es liefert keine Kommentarinhalte; dafür bleibt die Kern-API zuständig.

== Installation ==

1. Den Ordner `commentator-bridge` nach `wp-content/plugins/` kopieren.
2. Das Plugin im WordPress-Backend aktivieren.

Die App erkennt das Plugin automatisch am Namensraum `commentator/v1` in der
Antwort von `/wp-json/`.

== Changelog ==

= 1.7.0 =
* `/push/test` und Abschnitt im Benutzerprofil: hinterlegte Adressen sehen,
  entfernen, Testweckruf senden.
* Weckrufe wegen Statusänderungen höchstens alle 30 Sekunden je Adresse.
* `COMMENTATOR_BRIDGE_DISABLE_PUSH`, Filter
  `commentator_bridge_push_allow_local` und
  `commentator_bridge_push_endpoint_allowed`.

= 1.6.0 =
* Auch Statusänderungen wecken die App (`transition_comment_status`), außer
  sie kommen aus der App selbst.

= 1.5.0 =
* Neuer Endpunkt `/push` für die Sofortmeldung über UnifiedPush. Bei neuen
  Kommentaren geht ein inhaltsloser Weckruf an die hinterlegten Adressen.

= 1.4.0 =
* Neuer Endpunkt `/team`: Rollen des Blogs, die schreiben oder moderieren
  dürfen, und ihre Mitglieder.

= 1.3.0 =
* `/status` meldet zusätzlich den neuesten Kommentar unabhängig vom Status
  (`latest_any_comment_id`, `latest_any_comment_date_gmt`). Ohne dieses Feld
  bemerkt die App auf Blogs mit automatischer Freischaltung keine neuen
  Kommentare.

= 1.2.0 =
* Neue Endpunkte `/empty` (Spam oder Papierkorb leeren) und `/blocklist`
  (Sperrliste `disallowed_keys` pflegen).

= 1.1.0 =
* `/summary` meldet unter `all` genehmigte plus offene Kommentare statt
  `total_comments`, das Spam mitzählt. Die Zahl passt damit zu der Liste, die
  die REST-API bei `status=all` liefert.

= 1.0.0 =
* Erste Fassung mit den Endpunkten `status` und `summary`.
