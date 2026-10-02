=== Commentator Bridge ===
Contributors: christophlangner
Tags: comments, moderation, rest-api
Requires at least: 6.0
Tested up to: 6.9
Requires PHP: 7.4
Stable tag: 1.7.0
License: MIT
License URI: https://opensource.org/licenses/MIT

Lightweight REST endpoints for the Commentator Android app.

== Description ==

The Commentator Android app regularly checks whether there are new comments
awaiting moderation. Without this plugin, it fetches the comment list from
the WordPress core API to do so. That works, but is unnecessarily expensive
for a simple "Is there anything new?" question. In addition, the core API
offers no bulk deletion, no access to the blocklist and no way to identify a
blog's team if the account is only an editor.

The plugin is optional. The app works fully without it; it then merely makes
somewhat more expensive requests to the core API.

All endpoints live under `/wp-json/commentator/v1/` and require the same
authentication as the core API:

* `GET /status` returns the number of pending comments as well as the ID and
  timestamp of the newest pending comment and of the newest comment overall.
  Requires `moderate_comments`.
* `GET /summary` returns the comment count per status in a single request.
  Requires `moderate_comments`.
* `GET /team` lists the roles that may write posts or moderate comments, and
  their members (user ID and roles). Requires `moderate_comments`.
* `POST /empty` permanently empties spam or trash, in batches of at most 200
  comments – like the buttons of the same name in the comment management.
  Requires `moderate_comments`.
* `GET`, `POST` and `DELETE /blocklist` read and maintain the
  `disallowed_keys` option, i.e. the blocklist under Settings → Discussion.
  Requires `manage_options`, because it is a site-wide option.
* `POST` and `DELETE /push` store or remove a UnifiedPush address for the
  signed-in account. Requires `moderate_comments`.

== Instant notifications via UnifiedPush ==

Since version 1.5.0, the app can store a UnifiedPush address it received from
a UnifiedPush app on the phone (e.g. ntfy). For every new comment that does
not arrive as spam or trash, the plugin sends a non-blocking wake-up with the
body "new" to every stored address of all accounts with `moderate_comments`
– without the comment's name, text or ID. The app then fetches the comments
itself via the REST API as usual. The push server thus only learns that, and
when, a comment was posted.

Since 1.6.0, a status change, such as an approval in the admin, also sends a
wake-up, with the body "status". The app then immediately withdraws its
notification for that comment. Changes made from the app itself do not
trigger a wake-up.

Only publicly reachable HTTPS addresses are accepted; sending uses
`wp_safe_remote_post`. At most five addresses apply per account; the oldest
one is dropped. They are stored in the user meta
`commentator_push_endpoints`. Without a stored address, nothing happens.

Wake-ups for status changes go to an address at most once every 30 seconds;
bulk moderation in the admin thus triggers one instead of dozens. New
comments always trigger a wake-up.

In the user profile, the section "Commentator: Sofortmeldung" (instant
notifications) shows where the blog sends wake-ups, with "Entfernen" (remove)
and "Testweckruf senden" (send test wake-up).

== For admins ==

There is no settings page. For rare cases:

* `define( 'COMMENTATOR_BRIDGE_DISABLE_PUSH', true );` in `wp-config.php`
  disables instant notifications. The plugin then accepts no addresses and
  sends nothing outbound; everything else stays the same.
* If the blog and ntfy run on the same network,
  `add_filter( 'commentator_bridge_push_allow_local', '__return_true' );`
  allows addresses on the local network.
* The filter `commentator_bridge_push_endpoint_allowed` restricts addresses
  further, for example to your own server:

    add_filter( 'commentator_bridge_push_endpoint_allowed', function ( $ok, $endpoint ) {
        return $ok && 'ntfy.example.org' === wp_parse_url( $endpoint, PHP_URL_HOST );
    }, 10, 2 );

An ntfy server is not configured here but in the ntfy app on the phone: the
app passes the address it receives from there on to the plugin.

== What this plugin does not do ==

* It does not change any WordPress behavior.
* It only sends anything outbound if a moderator has set up instant
  notifications – and then nothing but a content-free wake-up to the stored
  address.
* It writes options only via `/blocklist` (`disallowed_keys`) and user meta
  only via `/push` (`commentator_push_endpoints`). Both are also possible via
  the admin and require the same capabilities.
* It creates no tables and sets no cookies.
* It does not return comment content; the core API remains responsible for
  that.

== Installation ==

1. Copy the `commentator-bridge` folder to `wp-content/plugins/`.
2. Activate the plugin in the WordPress admin.

The app detects the plugin automatically by the `commentator/v1` namespace in
the response from `/wp-json/`.

== Changelog ==

= 1.7.0 =
* `/push/test` and a section in the user profile: view stored addresses,
  remove them, send a test wake-up.
* Wake-ups for status changes at most once every 30 seconds per address.
* `COMMENTATOR_BRIDGE_DISABLE_PUSH`, filters
  `commentator_bridge_push_allow_local` and
  `commentator_bridge_push_endpoint_allowed`.

= 1.6.0 =
* Status changes also wake the app (`transition_comment_status`), unless they
  come from the app itself.

= 1.5.0 =
* New endpoint `/push` for instant notifications via UnifiedPush. For new
  comments, a content-free wake-up is sent to the stored addresses.

= 1.4.0 =
* New endpoint `/team`: the blog's roles that may write or moderate, and
  their members.

= 1.3.0 =
* `/status` additionally reports the newest comment regardless of status
  (`latest_any_comment_id`, `latest_any_comment_date_gmt`). Without this
  field, the app does not notice new comments on blogs with automatic
  approval.

= 1.2.0 =
* New endpoints `/empty` (empty spam or trash) and `/blocklist` (maintain the
  `disallowed_keys` blocklist).

= 1.1.0 =
* `/summary` reports approved plus pending comments under `all` instead of
  `total_comments`, which included spam. The number thus matches the list the
  REST API returns for `status=all`.

= 1.0.0 =
* Initial version with the `status` and `summary` endpoints.
