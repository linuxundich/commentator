# WordPress REST API: endpoints used

All requests go to `https://<site>/wp-json/`. Only the official core API is
used; the endpoints under `commentator/v1` come from the optional plugin and
do not replace any core functionality.

Authentication: HTTP Basic with username and Application Password, over HTTPS
only.

---

## Detecting the installation

```
GET /wp-json/
```

The app evaluates:

| Field | Meaning for the app |
|---|---|
| `name` | Display name of the blog |
| `namespaces` contains `wp/v2` | This is a usable WordPress REST API |
| `namespaces` contains `commentator/v1` | The bridge plugin is installed |
| `authentication["application-passwords"].endpoints.authorization` | The authorization flow is available |

If the last entry is missing, Application Passwords cannot be used. In
practice this is almost always because the installation is not running over
HTTPS – WordPress then deliberately does not offer the feature.

---

## Sign-in

### Authorization flow

```
GET <authorization endpoint>
    ?app_name=Commentator
    &app_id=<fixed UUID of the app>
    &success_url=commentator://auth-callback
    &reject_url=commentator://auth-rejected
```

The request is made in a Custom Tab, i.e. in the user's browser. After
confirmation, WordPress calls:

```
commentator://auth-callback?site_url=…&user_login=…&password=…
```

WordPress allows app schemes at this point; only `http://` is rejected.

### Verifying the credentials

```
GET /wp-json/wp/v2/users/me?context=edit
```

The app evaluates `id` and `capabilities.moderate_comments`. Without this
capability, the app still sets up the connection, but locks all moderation
actions and says so.

---

## Reading comments

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

Important quirks:

* **`context=edit` is mandatory.** Without it, WordPress returns neither
  `status` nor `author_email`, and the filters would be worthless.
* **The status value differs depending on direction.** When listing,
  "approved" is `approve`, while in the returned object it is `approved`. The
  app maps this in one place (`CommentStatus`).
* **`spam` and `trash` require `moderate_comments`.**
* **`author_avatar_urls` is not always an object.** If avatars are disabled
  in WordPress, it contains `false`. The app therefore reads the field
  loosely.

### Pagination

The page count is not in the response body but in the headers:

| Header | Meaning |
|---|---|
| `X-WP-Total` | Total number of results |
| `X-WP-TotalPages` | Number of pages |

If they are missing, the app assumes a single page.

### Single comment and replies

```
GET /wp-json/wp/v2/comments/<id>?context=edit
GET /wp-json/wp/v2/comments?parent=<id>&status=all&order=asc&context=edit
```

### Replies to multiple comments ("Unanswered" filter)

```
GET /wp-json/wp/v2/comments
    ?status=approve
    &parent=<ids, comma-separated>
    &per_page=100
    &context=edit
```

The "Unanswered" filter fetches the same list as "Approved" and then asks, in
a single request, for the approved replies to the loaded comments. Whether
one of them is a reply from the team is decided by the app locally in its
cache; WordPress does not know this state and cannot count it either.

`parent` and `include` are passed comma-separated as a single value, not as a
repeated parameter: for repeated parameters, WordPress only evaluates the
last one.

### Reconciling open notifications

```
GET /wp-json/wp/v2/comments?include=<ids>&status=any&context=edit
```

The background check uses this to ask for the comments that still have a
notification showing – only if there are any, and before it reports anything
new. `status=any` is important: it includes spam and trash, whereas
`status=all` does not. A comment missing from the response has therefore
been permanently deleted and not merely moved somewhere else.

---

## Post titles

Comments only refer to an ID via `post`. Since comments can be attached to
both posts and pages, the app queries both endpoints – but only for IDs that
are not yet in the local cache:

```
GET /wp-json/wp/v2/posts?include=<ids>&_fields=id,title,link
GET /wp-json/wp/v2/pages?include=<ids>&_fields=id,title,link
```

---

## Moderation

### Changing the status

```
POST /wp-json/wp/v2/comments/<id>
Content-Type: application/json

{"status": "approved" | "hold" | "spam" | "trash"}
```

Fields that are not set are not transmitted, so that a partial update does
not overwrite other values.

### Editing the content

```
POST /wp-json/wp/v2/comments/<id>

{"content": "<new text>"}
```

Whether this is allowed is decided by WordPress based on the capabilities of
the signed-in account. The app offers the action and presents a rejection in
an understandable way.

### Trash and permanent deletion

```
DELETE /wp-json/wp/v2/comments/<id>              → trash
DELETE /wp-json/wp/v2/comments/<id>?force=true   → permanent
```

If the trash is disabled in the installation (`EMPTY_TRASH_DAYS = 0`), the
first request already deletes permanently. The app cannot read this setting
and therefore explicitly warns about it before deleting permanently.

---

## Replies

```
POST /wp-json/wp/v2/comments

{
  "post": <post ID>,
  "parent": <ID of the comment being replied to>,
  "content": "<text>",
  "status": "approved"
}
```

The reply is published as a real comment by the signed-in user and refers to
the original comment via `parent`. `status: approved` is sent deliberately: a
moderator's reply should not end up in the moderation queue itself.

If the original comment is still pending (`hold`), the app first approves it
with `POST /wp-json/wp/v2/comments/<id>` and `{"status": "approved"}` – just
like the WordPress admin. Without approval, the parent comment stays pending,
and WordPress does not show the reply beneath it on the website. The order is
intentional: if the approval fails, nothing has happened, and a second
attempt does not create a duplicate reply.

---

## Endpoints of the optional plugin

All of them require the same authentication as the core API. The required
capability is listed for each; it is the same one the corresponding action
requires in the admin.

| Route | Capability | Since |
|---|---|---|
| `GET /commentator/v1/status` | `moderate_comments` | 1.0.0 |
| `GET /commentator/v1/summary` | `moderate_comments` | 1.0.0 |
| `POST /commentator/v1/empty` | `moderate_comments` | 1.2.0 |
| `GET`, `POST`, `DELETE /commentator/v1/blocklist` | `manage_options` | 1.2.0 |
| `GET /commentator/v1/team` | `moderate_comments` | 1.4.0 |
| `POST`, `DELETE /commentator/v1/push` | `moderate_comments` | 1.5.0 |

### Status

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

`latest_comment_id` refers to the newest comment awaiting moderation,
`latest_any_comment_id` (since 1.3.0) to the newest one regardless of status
– without it, the app never noticed anything on blogs with automatic
approval. Both are determined by ID, not by date. The app compares them with
the last reported value and only actually loads comments if something has
changed. Without the fields added in 1.3.0, it queries the core API as usual.

### Summary

```
GET /wp-json/commentator/v1/summary
```

```json
{ "counts": { "approve": 120, "hold": 4, "spam": 17, "trash": 2, "all": 124 } }
```

Without the plugin, the filter bar would need five separate queries just to
display numbers. `all` is approved plus pending – exactly what the REST API
lists for `status=all`.

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

Only roles that may write posts or moderate comments are returned, and at
most 200 members. The core API is not suitable for this: `wp/v2/users` with
`context=edit` requires `list_users`, which an editor does not have.

### Emptying spam or trash

```
POST /wp-json/commentator/v1/empty

{"status": "spam" | "trash"}
```

```json
{ "deleted": 200, "remaining": 1340 }
```

Deletes permanently, in batches of at most 200 comments. If `remaining` is
greater than zero, the app asks again. Without the plugin, it deletes with
one request per comment.

### Blocklist

```
GET    /wp-json/commentator/v1/blocklist
POST   /wp-json/commentator/v1/blocklist           {"value": "<entry>"}
DELETE /wp-json/commentator/v1/blocklist?value=<entry>
```

```json
{ "entries": ["spam@example.com", "example.net"] }
```

Maintains the `disallowed_keys` option, the same list as under Settings →
Discussion. An empty entry is rejected with 400 – it would match every
comment. The app currently only uses `POST` ("Block sender") and only offers
the action if the account has `manage_options`.

### Push address for instant notifications

```
POST   /wp-json/commentator/v1/push                {"endpoint": "https://…"}
DELETE /wp-json/commentator/v1/push?endpoint=https://…
```

```json
{ "registered": true }
{ "removed": true }
```

Stores or removes, for the signed-in account, the endpoint address the app
received from the UnifiedPush app on the phone. Only publicly reachable HTTPS
addresses are accepted (`wp_http_validate_url`); a private address is
rejected with 400 (`commentator_invalid_endpoint`). At most five addresses
apply per account; the oldest one is dropped. They are stored in the user
meta `commentator_push_endpoints`.

If `POST` responds with 404, the plugin is older than 1.5.0; the app shows
this in the blog's settings. With 403 (`commentator_push_disabled`), instant
notifications are disabled on the blog (since 1.7.0, see below).

```
POST /wp-json/commentator/v1/push/test
```

```json
{ "sent": 1, "failed": 0 }
```

Since 1.7.0. Sends a wake-up with the body `test` to all addresses of the
signed-in account and – unlike in normal operation – waits for the push
server's response. `sent` counts the addresses that accepted with 2xx. The
app recognizes the `test` body and confirms with a notification of its own
instead of checking for comments.

For admins, without a settings page:

* `define( 'COMMENTATOR_BRIDGE_DISABLE_PUSH', true );` in `wp-config.php`
  disables instant notifications: no new addresses, no sending.
* The filter `commentator_bridge_push_allow_local` (`bool`, default `false`)
  allows push servers on the local network; sending then uses
  `wp_remote_post` instead of `wp_safe_remote_post`.
* The filter `commentator_bridge_push_endpoint_allowed` (`bool $allowed,
  string $endpoint`) restricts addresses further, for example to your own
  ntfy server.

For every new comment that does not arrive as spam or trash, the plugin sends
to every stored address of all accounts with `moderate_comments`:

```
POST <endpoint>
Content-Type: text/plain
TTL: 3600
Urgency: high

new
```

Sending is non-blocking and uses `wp_safe_remote_post`. The body deliberately
contains nothing – no name, no text, no ID. The app then checks on its own
via the endpoints described above.

---

## Error responses

WordPress responds uniformly:

```json
{"code": "rest_comment_invalid_id", "message": "…", "data": {"status": 404}}
```

The app reads `code` and maps it. The raw message is never shown; instead,
the app uses its own text – see `AppError` and `ErrorTexts`.

| Situation | HTTP | Handling in the app |
|---|---|---|
| Application Password invalid or revoked | 401 | Mark session as invalid, offer to sign in again |
| Account without `moderate_comments` | 403 | Lock moderation actions, state the reason |
| Comment no longer exists | 404 | Leave the detail view, clean up the cache |
| Too many requests | 429 | Evaluate and display `Retry-After` |
| Server problem | 5xx | Retry the background check, show with retry option |
