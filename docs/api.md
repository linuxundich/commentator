# WordPress-REST-API: verwendete Endpunkte

Alle Aufrufe gehen gegen `https://<site>/wp-json/`. Es wird ausschließlich die
offizielle Kern-API verwendet; die Endpunkte unter `commentator/v1` stammen
aus dem optionalen Plugin und ersetzen keine Kernfunktion.

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

### Antworten auf mehrere Kommentare (Filter „Unbeantwortet“)

```
GET /wp-json/wp/v2/comments
    ?status=approve
    &parent=<ids, kommagetrennt>
    &per_page=100
    &context=edit
```

Der Filter „Unbeantwortet“ ruft dieselbe Liste ab wie „Genehmigt“ und fragt
danach in einer Anfrage nach den freigegebenen Antworten auf die geladenen
Kommentare. Ob darunter eine Antwort aus dem Team steht, entscheidet die App
lokal im Zwischenspeicher; WordPress kennt diesen Zustand nicht und kann ihn
auch nicht zählen.

`parent` und `include` werden kommagetrennt als ein Wert übergeben, nicht als
wiederholter Parameter: Von wiederholten Parametern wertet WordPress nur den
letzten aus.

### Abgleich offener Benachrichtigungen

```
GET /wp-json/wp/v2/comments?include=<ids>&status=any&context=edit
```

Die Hintergrundprüfung fragt damit nach den Kommentaren, zu denen noch eine
Benachrichtigung steht – nur wenn es solche gibt, und bevor sie Neues meldet.
Wichtig ist `status=any`: Es schließt Spam und Papierkorb ein, `status=all`
dagegen nicht. Ein Kommentar, der in der Antwort fehlt, ist damit endgültig
gelöscht und nicht bloß woanders einsortiert.

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

Ist der ursprüngliche Kommentar noch offen (`hold`), gibt die App ihn vorher
mit `POST /wp-json/wp/v2/comments/<id>` und `{"status": "approved"}` frei –
wie das WordPress-Backend. Ohne Freigabe bleibt der Elternkommentar offen, und
WordPress zeigt die Antwort darunter auf der Website nicht an. Die Reihenfolge
ist Absicht: Scheitert die Freigabe, ist nichts geschehen, und ein zweiter
Versuch erzeugt keine doppelte Antwort.

---

## Endpunkte des optionalen Plugins

Alle verlangen dieselbe Authentifizierung wie die Kern-API. Das nötige Recht
steht jeweils dabei; es ist dasselbe, das die entsprechende Aktion im Backend
verlangt.

| Route | Recht | Seit |
|---|---|---|
| `GET /commentator/v1/status` | `moderate_comments` | 1.0.0 |
| `GET /commentator/v1/summary` | `moderate_comments` | 1.0.0 |
| `POST /commentator/v1/empty` | `moderate_comments` | 1.2.0 |
| `GET`, `POST`, `DELETE /commentator/v1/blocklist` | `manage_options` | 1.2.0 |
| `GET /commentator/v1/team` | `moderate_comments` | 1.4.0 |
| `POST`, `DELETE /commentator/v1/push` | `moderate_comments` | 1.5.0 |

### Zustand

```
GET /wp-json/commentator/v1/status
```

```json
{
  "pending_count": 4,
  "latest_comment_id": 98,
  "latest_comment_date_gmt": "2026-09-19T10:00:00",
  "latest_any_comment_id": 101,
  "latest_any_comment_date_gmt": "2026-09-19T11:30:00",
  "plugin_version": "1.6.0"
}
```

`latest_comment_id` bezeichnet den neuesten Kommentar, der auf Moderation
wartet, `latest_any_comment_id` (seit 1.3.0) den neuesten unabhängig vom
Status – ohne ihn bemerkte die App auf Blogs mit automatischer Freischaltung
nie etwas. Beide werden nach Kennung bestimmt, nicht nach Datum. Die App
vergleicht sie mit dem zuletzt gemeldeten Wert und lädt nur dann tatsächlich
Kommentare nach, wenn sich etwas geändert hat. Ohne die Felder ab 1.3.0 fragt
sie regulär über die Kern-API.

### Zusammenfassung

```
GET /wp-json/commentator/v1/summary
```

```json
{ "counts": { "approve": 120, "hold": 4, "spam": 17, "trash": 2, "all": 124 } }
```

Ohne Plugin bräuchte die Filterleiste fünf getrennte Abfragen, nur um Zahlen
anzuzeigen. `all` ist genehmigt plus offen – genau das, was die REST-API bei
`status=all` auflistet.

### Team

```
GET /wp-json/commentator/v1/team
```

```json
{
  "roles": [ { "slug": "administrator", "name": "Administrator" },
             { "slug": "editor", "name": "Redakteur" } ],
  "members": [ { "id": 1, "roles": ["administrator"] } ]
}
```

Geliefert werden nur Rollen, die Beiträge schreiben oder Kommentare moderieren
dürfen, und höchstens 200 Mitglieder. Die Kern-API taugt dafür nicht:
`wp/v2/users` mit `context=edit` verlangt `list_users`, und das hat ein
Redakteur nicht.

### Spam oder Papierkorb leeren

```
POST /wp-json/commentator/v1/empty

{"status": "spam" | "trash"}
```

```json
{ "deleted": 200, "remaining": 1340 }
```

Löscht endgültig, in Stapeln von höchstens 200 Kommentaren. Ist `remaining`
größer als null, fragt die App erneut. Ohne Plugin löscht sie mit einer
Anfrage je Kommentar.

### Sperrliste

```
GET    /wp-json/commentator/v1/blocklist
POST   /wp-json/commentator/v1/blocklist           {"value": "<Eintrag>"}
DELETE /wp-json/commentator/v1/blocklist?value=<Eintrag>
```

```json
{ "entries": ["spam@example.com", "example.net"] }
```

Pflegt die Option `disallowed_keys`, dieselbe Liste wie unter Einstellungen →
Diskussion. Ein leerer Eintrag wird mit 400 abgelehnt – er träfe jeden
Kommentar. Die App nutzt heute nur `POST` („Absender sperren“) und bietet die
Aktion nur an, wenn das Konto `manage_options` hat.

### Push-Adresse für die Sofortmeldung

```
POST   /wp-json/commentator/v1/push                {"endpoint": "https://…"}
DELETE /wp-json/commentator/v1/push?endpoint=https://…
```

```json
{ "registered": true }
{ "removed": true }
```

Hinterlegt beziehungsweise entfernt die Endpoint-Adresse, die die App von der
UnifiedPush-App auf dem Telefon bekommen hat, für das angemeldete Konto.
Angenommen werden nur öffentlich erreichbare HTTPS-Adressen
(`wp_http_validate_url`); eine private Adresse wird mit 400
(`commentator_invalid_endpoint`) abgelehnt. Je Konto gelten höchstens fünf
Adressen, die älteste fällt heraus. Gespeichert werden sie in der Benutzermeta
`commentator_push_endpoints`.

Antwortet `POST` mit 404, ist das Plugin älter als 1.5.0; die App zeigt das in
den Einstellungen des Blogs an.

Bei jedem neuen Kommentar, der nicht als Spam oder Papierkorb eingeht, sendet
das Plugin an jede hinterlegte Adresse aller Konten mit `moderate_comments`:

```
POST <endpoint>
Content-Type: text/plain
TTL: 3600
Urgency: high

new
```

Der Versand ist nicht blockierend und läuft über `wp_safe_remote_post`. Der
Rumpf enthält bewusst nichts – keinen Namen, keinen Text, keine Kennung. Die
App prüft daraufhin selbst über die oben beschriebenen Endpunkte.

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
