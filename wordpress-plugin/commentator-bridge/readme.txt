=== Commentator Bridge ===
Contributors: christophlangner
Tags: comments, moderation, rest-api
Requires at least: 6.0
Tested up to: 6.9
Requires PHP: 7.4
Stable tag: 1.0.0
License: MIT
License URI: https://opensource.org/licenses/MIT

Zwei schlanke REST-Endpunkte für die Android-App Commentator.

== Beschreibung ==

Die Android-App Commentator prüft regelmäßig, ob es neue moderationsbedürftige
Kommentare gibt. Ohne dieses Plugin ruft sie dafür die Kommentarliste der
WordPress-Kern-API ab. Das funktioniert, ist aber für eine reine
"Gibt es etwas Neues?"-Frage unnötig aufwendig.

Dieses Plugin ergänzt zwei Leseendpunkte:

* `GET /wp-json/commentator/v1/status` liefert die Anzahl ausstehender
  Kommentare sowie Kennung und Zeitstempel des neuesten davon.
* `GET /wp-json/commentator/v1/summary` liefert die Kommentaranzahl je Status
  in einem einzigen Aufruf.

Beide verlangen dieselbe Authentifizierung wie die Kern-API und die
Berechtigung `moderate_comments`.

== Was dieses Plugin nicht tut ==

* Es ändert kein Verhalten von WordPress.
* Es hängt sich nicht in die Kommentarverarbeitung ein.
* Es schreibt keine Optionen und legt keine Tabellen an.
* Es sendet keine Daten an Dritte.
* Es registriert keine schreibenden Endpunkte.

== Installation ==

1. Den Ordner `commentator-bridge` nach `wp-content/plugins/` kopieren.
2. Das Plugin im WordPress-Backend aktivieren.

Die App erkennt das Plugin automatisch am Namensraum `commentator/v1` in der
Antwort von `/wp-json/`.

== Changelog ==

= 1.0.0 =
* Erste Fassung mit den Endpunkten `status` und `summary`.
