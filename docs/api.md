# WordPress-REST-API: verwendete Endpunkte

Alle Aufrufe gehen gegen `https://<site>/wp-json/`. Es wird ausschließlich die
offizielle Kern-API verwendet; die beiden Endpunkte unter `commentator/v1`
stammen aus dem optionalen Plugin und ersetzen keine Kernfunktion.

Authentifizierung: HTTP Basic mit Benutzername und Application Password,
ausschließlich über HTTPS.

---

## Erkennung der Installation

```
GET /wp-json/
```

Ausgewertet werden:

| Feld | Bedeutung für die App |
|---|---|
| `name` | Anzeigename des Blogs |
| `namespaces` enthält `wp/v2` | Es handelt sich um eine nutzbare WordPress-REST-API |
| `namespaces` enthält `commentator/v1` | Das Bridge-Plugin ist installiert |
| `authentication["application-passwords"].endpoints.authorization` | Autorisierungs-Flow ist verfügbar |

Fehlt der letzte Eintrag, sind Application Passwords nicht nutzbar. In der
Praxis liegt das fast immer daran, dass die Installation nicht über HTTPS
läuft – WordPress bietet die Funktion dann bewusst nicht an.

---

## Anmeldung

### Autorisierungs-Flow

```
GET <authorization endpoint>
    ?app_name=Commentator
    &app_id=<feste UUID der App>
    &success_url=commentator://auth-callback
    &reject_url=commentator://auth-rejected
```

Der Aufruf erfolgt in einem Custom Tab, also im Browser des Benutzers. Nach
der Bestätigung ruft WordPress auf:

```
commentator://auth-callback?site_url=…&user_login=…&password=…
```

WordPress erlaubt an dieser Stelle App-Schemata; abgelehnt wird lediglich
`http://`.

### Prüfung der Zugangsdaten

```
GET /wp-json/wp/v2/users/me?context=edit
```

Ausgewertet werden `id` und `capabilities.moderate_comments`. Ohne diese
Berechtigung richtet die App die Verbindung zwar ein, sperrt aber alle
Moderationsaktionen und weist darauf hin.

---

## Kommentare lesen

```
GET /wp-json/wp/v2/comments
    ?status=<all|hold|approve|spam|trash>
    &page=<n>
    &per_page=20
    &context=edit
    &orderby=date_gmt
    &order=desc
    &type=comment
```

Wichtige Eigenheiten:

* **`context=edit` ist Pflicht.** Ohne ihn liefert WordPress weder `status`
  noch `author_email`, und die Filter wären wertlos.
* **Der Statuswert unterscheidet sich je nach Richtung.** Beim Auflisten heißt
  „genehmigt“ `approve`, im zurückgelieferten Objekt dagegen `approved`. Die
  App bildet das an einer Stelle ab (`CommentStatus`).
* **`spam` und `trash` erfordern `moderate_comments`.**
* **`author_avatar_urls` ist nicht immer ein Objekt.** Sind Avatare in
  WordPress deaktiviert, steht dort `false`. Die App liest das Feld deshalb
  unspezifisch ein.

### Paginierung

Die Seitenzahl steht nicht im Antwortkörper, sondern in den Kopfzeilen:

| Kopfzeile | Bedeutung |
|---|---|
| `X-WP-Total` | Gesamtzahl der Treffer |
| `X-WP-TotalPages` | Anzahl der Seiten |

Fehlen sie, geht die App von einer einzigen Seite aus.

### Einzelner Kommentar und Antworten

```
GET /wp-json/wp/v2/comments/<id>?context=edit
GET /wp-json/wp/v2/comments?parent=<id>&status=all&order=asc&context=edit
```

---

## Beitragstitel

Kommentare verweisen nur über `post` auf eine Kennung. Da Kommentare sowohl an
Beiträgen als auch an Seiten hängen können, fragt die App beide Endpunkte ab –
aber nur für Kennungen, die noch nicht im lokalen Cache stehen:

```
GET /wp-json/wp/v2/posts?include=<ids>&_fields=id,title,link
GET /wp-json/wp/v2/pages?include=<ids>&_fields=id,title,link
```

---

## Moderation

### Status ändern

```
POST /wp-json/wp/v2/comments/<id>
Content-Type: application/json

{"status": "approved" | "hold" | "spam" | "trash"}
```

Nicht gesetzte Felder werden nicht übertragen, damit eine Teilaktualisierung
keine anderen Werte überschreibt.

### Inhalt bearbeiten

```
POST /wp-json/wp/v2/comments/<id>

{"content": "<neuer Text>"}
```

Ob das erlaubt ist, entscheidet WordPress anhand der Berechtigungen des
angemeldeten Kontos. Die App bietet die Aktion an und stellt eine Ablehnung
verständlich dar.

### Papierkorb und endgültiges Löschen

```
DELETE /wp-json/wp/v2/comments/<id>              → Papierkorb
DELETE /wp-json/wp/v2/comments/<id>?force=true   → endgültig
```

Ist der Papierkorb in der Installation deaktiviert (`EMPTY_TRASH_DAYS = 0`),
löscht bereits der erste Aufruf endgültig. Die App kann diese Einstellung
nicht auslesen und weist deshalb vor dem endgültigen Löschen ausdrücklich
darauf hin.

---

## Antworten

```
POST /wp-json/wp/v2/comments

{
  "post": <Beitrags-ID>,
  "parent": <ID des beantworteten Kommentars>,
  "content": "<Text>",
  "status": "approved"
}
```

Die Antwort wird als echter Kommentar des angemeldeten Benutzers
veröffentlicht und verweist über `parent` auf den ursprünglichen Kommentar.
`status: approved` wird bewusst mitgesendet: Eine Antwort des Moderators soll
nicht selbst in der Moderationswarteschlange landen.

---

## Endpunkte des optionalen Plugins

Beide verlangen `moderate_comments` und dieselbe Authentifizierung wie die
Kern-API.

### Zustand

```
GET /wp-json/commentator/v1/status
```

```json
{
  "pending_count": 4,
  "latest_comment_id": 98,
  "latest_comment_date_gmt": "2026-09-19T10:00:00",
  "plugin_version": "1.0.0"
}
```

`latest_comment_id` bezeichnet den neuesten Kommentar, der auf Moderation
wartet. Die App vergleicht ihn mit dem zuletzt gemeldeten Wert und lädt nur
dann tatsächlich Kommentare nach, wenn sich etwas geändert hat.

### Zusammenfassung

```
GET /wp-json/commentator/v1/summary
```

```json
{ "counts": { "approve": 120, "hold": 4, "spam": 17, "trash": 2, "all": 143 } }
```

Ohne Plugin bräuchte die Filterleiste fünf getrennte Abfragen, nur um Zahlen
anzuzeigen.

---

## Fehlerantworten

WordPress antwortet einheitlich:

```json
{"code": "rest_comment_invalid_id", "message": "…", "data": {"status": 404}}
```

Die App liest `code` aus und ordnet ihn zu. Angezeigt wird nie die Rohmeldung,
sondern ein eigener Text – siehe `AppError` und `ErrorTexts`.

| Situation | HTTP | Behandlung in der App |
|---|---|---|
| Application Password ungültig oder widerrufen | 401 | Sitzung als ungültig markieren, erneute Anmeldung anbieten |
| Konto ohne `moderate_comments` | 403 | Moderationsaktionen sperren, Grund nennen |
| Kommentar existiert nicht mehr | 404 | Detailansicht verlassen, Cache bereinigen |
| Zu viele Anfragen | 429 | `Retry-After` auswerten und anzeigen |
| Serverproblem | 5xx | Hintergrundprüfung wiederholen, Anzeige mit Wiederholung |
