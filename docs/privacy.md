# Data processing and network connections

This document lists completely which data Commentator processes and where the
app connects to. It is deliberately written so that it can be checked against
the source code.

## Network connections

| Destination | When | Purpose | Can be turned off |
|---|---|---|---|
| The configured WordPress instance | When refreshing, on every moderation action and during the periodic background check | Reading and moderating comments | By signing out; the background check separately in the settings |
| `secure.gravatar.com` or the avatar address supplied by WordPress | Only when displaying avatars | Profile pictures of commenters | Yes – avatars are **turned off by default** |

The app makes no other connections. In particular:

* no analytics, no crash reports, no telemetry
* no ads and no advertising IDs
* no Firebase or Google Play Services libraries
* no update or license check
* no developer server

The avatar addresses are supplied by WordPress itself in the
`author_avatar_urls` field. On a standard installation they point to
Gravatar, a service run by Automattic. This is a connection to a third party
– which is why displaying avatars is off by default and has to be turned on
deliberately.

The blog icon, which appears in the top bar, in the settings and as a large
image in notifications, comes from the blog itself. Notifications take it
from the app's local image cache; this does not create a connection to a
third party.

### Optional: instant notifications via UnifiedPush

Turning on "Notify instantly" in a blog's settings brings in additional
parties. Even then, the app itself only connects to your own blog; the other
connections are made by the UnifiedPush app and the plugin:

| Party | What it learns |
|---|---|
| The UnifiedPush app on the phone (e.g. ntfy) | That Commentator wants to receive wake-ups per blog, and the blog's name, under which it shows the registration. It assigns an endpoint address for this and forwards incoming messages to the app. |
| That app's push server (e.g. `ntfy.sh` or your own ntfy) | That, and when, a comment was posted on the blog – nothing else. The message consists only of the word "new". |
| The plugin on the blog | The endpoint address. The app stores it there and withdraws it when the feature is turned off, when the blog is removed, or when the UnifiedPush app unregisters. |

For every new comment that does not arrive as spam or trash, the plugin sends
a wake-up without content to this address: no name, no text, no ID. The
message is deliberately unencrypted – there is nothing in it that encryption
could protect. The app then fetches the comments itself via the REST API as
usual. Google and Firebase are not involved at any point.

If you run the push server yourself, you also keep the timing of comments to
yourself. If instant notifications are off, none of this happens.

## Encryption

HTTPS only. Cleartext traffic is prevented on two levels:

* `network_security_config.xml` with `cleartextTrafficPermitted="false"` and
  `android:usesCleartextTraffic="false"` in the manifest
* an interceptor that aborts every request without HTTPS

The app rejects addresses beginning with `http://` as soon as they are
entered.

Debug builds additionally trust user-installed certificates
(`<debug-overrides>`), so that development against the local test
environment with a self-signed certificate is possible. The platform ignores
this block in release builds; cleartext remains forbidden in the debug build
as well.

## Credentials

| | |
|---|---|
| Method | Application Password (WordPress core feature since 5.6) |
| Account password | Is **never** passed to the app; it is entered in the browser |
| Storage | `Base64(IV ‖ AES-256-GCM(JSON))` in DataStore |
| Key | Android Keystore, alias `commentator_credentials_v1`, never leaves the Keystore |
| Transmission | Only as an `Authorization` header to the configured instance |
| Revocation | At any time in the WordPress profile, without changing the account password |

Measures against accidental disclosure:

* `ApplicationPassword` and `InstanceCredentials` override `toString()` and
  do not reveal the value. Two unit tests ensure this.
* HTTP logging exists only in the debug build – the library is not even part
  of the application in release builds – and redacts `Authorization` there.
* The step for manually entering the Application Password sets
  `FLAG_SECURE`. This keeps an entered password out of both screenshots and
  the app overview. Deliberately only this one step: address entry and the
  browser route show nothing worth protecting, and an app that generally
  cannot be captured would be annoying for no gain.
* `android:allowBackup="false"` and empty extraction rules: nothing is
  included in a cloud backup and nothing is transferred to a new device.
* The Application Password is read from the deep-link intent and immediately
  passed on through a transient channel. It is deliberately **not** passed as
  a navigation argument, because there it would end up in the back stack and
  in the saved state.

## Locally stored data

In the Room database (`commentator.db`, app-private directory):

* Comments: ID, author name, email address, website, avatar address,
  content, date, status, post
* Post titles
* Time of the last synchronization
* IDs of already reported comments (to avoid duplicate notifications,
  removed automatically after 30 days)

The displayed notifications themselves contain, in addition to the visible
text, the comment's status at the time of notification. The background check
needs it to clear notifications that have been dealt with.

In DataStore:

* Blog configuration: ID, display name, address, username, user ID,
  capability, whether the plugin was detected
* Settings
* the encrypted credentials record

**Personal data:** Comment data regularly contains personal data of third
parties, in particular email addresses. It is stored exclusively locally and
comes from your own WordPress installation. The app does not pass it on to
anyone. Displaying the email address in the detail view is turned off by
default – it is rarely needed for moderation.

When signing out, the following are deleted: all comments and post titles of
the instance, the synchronization state, the notification records, the blog
configuration and the credentials record. If instant notifications were
turned on, the app first withdraws its push address from the plugin and
unregisters from the UnifiedPush app. If no instance is configured
afterwards, the Keystore key is removed as well.

## Permissions

Requested by the app itself:

| Permission | Purpose |
|---|---|
| `INTERNET` | Access to the WordPress REST API |
| `ACCESS_NETWORK_STATE` | Offline detection and a condition for the background check |
| `POST_NOTIFICATIONS` | Notice of new comments (from Android 13, requested at runtime) |

Manifest merging adds three more from `androidx.work`. They are not in the
app's manifest, but end up in the final APK and should therefore be listed
here:

| Permission | Purpose |
|---|---|
| `WAKE_LOCK` | WorkManager keeps the device awake while a background check is running |
| `RECEIVE_BOOT_COMPLETED` | restores the scheduled check after a reboot; without it, the check would stall until the app is opened again |
| `FOREGROUND_SERVICE` | declared by WorkManager for expedited work. The app does not use this, but the library declares it across the board |

In addition, there is `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
– a permission WorkManager defines for itself so that its internal broadcast
receivers remain unreachable for other apps. It is signature-level and cannot
be obtained by third parties.

None of these permissions grants access to user data, and none requires
consent at runtime except `POST_NOTIFICATIONS`.

Not included: no storage access, no contacts, no location, no camera, no
microphone, no background location, no alarm clock, no exact alarms, no
querying of all installed apps.

The UnifiedPush connector does not request any permission. It merely
declares `<queries>` in the manifest for the three UnifiedPush actions
`LINK`, `REGISTER` and `UNREGISTER`. This lets the app see only apps that
offer themselves as UnifiedPush distributors – it has to find them in order
to register.

The debug build additionally contains the receiver `DebugSyncReceiver`, which
triggers the background check immediately. It is protected with
`android.permission.DUMP` and thus only reachable via `adb`; it is absent
from the release build.


## Data processing by the WordPress plugin

The optional plugin `commentator-bridge` does not change any WordPress
behavior, creates no tables and sets no cookies. Its endpoints are described
in [`api.md`](api.md#endpoints-of-the-optional-plugin).

Read access:

* `status` and `summary` return only numbers as well as the ID and timestamp
  of the newest comments – no comment content.
* `team` returns the roles that may write or moderate, and for each member
  the user ID and roles – no names, no email addresses.

Write access in two places:

* `/blocklist` changes the `disallowed_keys` option, i.e. the blocklist under
  Settings → Discussion. It may contain email addresses, names or IP
  addresses of commenters – just like an entry made via the admin. Requires
  `manage_options`.
* `/push` stores the push addresses of the signed-in account in its user meta
  `commentator_push_endpoints`, at most five; the oldest one is dropped. The
  address is an endpoint URL of the push server and contains no comment data.

`/empty` permanently deletes spam or trash, like the buttons of the same name
in the admin.

**Outbound**, the plugin only sends anything if at least one account with
`moderate_comments` has stored a push address. Then, for every new comment
that does not arrive as spam or trash, a non-blocking `POST` with the body
"new" goes to every stored address – without the comment's name, text or ID.
Only publicly reachable HTTPS addresses are accepted, over IPv4 or IPv6; the
check is repeated before every send and rejects destinations on the local
network.
Without a stored address, the plugin sends nothing.
