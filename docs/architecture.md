# Architecture

This document records the analysis from phase 1 and all the technology and
architecture decisions that followed from it. It is the reference for *why*
things are built the way they are.

Analysis as of: **2026-09-20**

---

## 1. Goal and scope

Commentator is a specialised tool for the daily moderation of WordPress
comments – not a WordPress reader and not a replacement for the admin.

Constraints that shape the architecture:

* The architecture supports multiple WordPress instances. Version 1 served only
  one blog, but was designed for more from the start – multi-blog support came
  later without reworking the data layer.
* There is **no server of our own**. Everything the app can do, it has to do
  against the WordPress REST API or locally.
* Data minimisation and traceability of network connections take precedence
  over convenience features.

---

## 2. Toolchain determined (checked on 2026-09-20)

All versions were determined directly from the Maven metadata of Google Maven
and Maven Central and from the official release notes, not from tutorials.

| Component | Chosen | Latest found | Rationale |
|---|---|---|---|
| Android Gradle Plugin | 9.4.1 | 9.5.0-alpha06 | Latest stable version; alphas are unsuitable for a production project. |
| Gradle | 9.7.1 | 9.7.1 | AGP 9.4 requires at least 9.6.0; 9.7.1 is the current stable release. |
| JDK (build) | 21 | 26 | AGP 9.4 requires at least JDK 17, Gradle 9.7 supports 17–26. JDK 21 is the widely tested LTS middle ground. |
| Kotlin | 2.3.21 | 2.4.20 | **Deliberately not the latest version:** KSP is currently only available up to the 2.3.x line (KSP 2.3.12 is built against kotlin-stdlib 2.3.20). Room and Hilt need KSP. As soon as KSP for Kotlin 2.4 is released, this can be raised. |
| KSP | 2.3.12 | 2.3.12 | Replaces kapt completely; kapt is not used. |
| compileSdk / targetSdk | 37 | 37 | API 37 is the highest level supported by AGP 9.4. Since the SDK minor releases, the SDK package is called `platforms;android-37.0`. |
| minSdk | 26 | – | Android 8.0. Notification channels are mandatory from 26 and usable without compatibility paths; covers practically the entire active device base. |
| Build Tools | 37.0.0 | 37.0.0 | AGP 9.4 requires at least 36.0.0. |
| Compose BOM | 2026.09.00 | 2026.09.00 | Keeps all Compose artifacts consistent. |
| compose-material3 | 1.5.0-alpha29 | 1.5.0-alpha29 | **The only deliberate exception to the stable rule**, and the only Compose artifact with its own version. Rationale below. |
| Navigation Compose | 2.10.1 | 2.10.1 | Type-safe navigation via `@Serializable` routes. |
| Lifecycle | 2.11.0 | 2.12.0-alpha03 | Latest stable version. |
| Hilt | 2.60.1 | 2.60.1 | Explicitly preferred in the requirements; run with KSP. |
| Retrofit | 3.0.0 | 3.0.0 | With `converter-kotlinx-serialization` in the same version. |
| OkHttp | 5.5.0 | 5.5.0 | Required by Retrofit 3 anyway; provides MockWebServer for tests. |
| kotlinx.serialization | 1.11.0 | 1.12.0-RC | JSON without reflection and without a runtime code generator. |
| Coroutines | 1.11.0 | 1.11.0 | – |
| Room | 2.8.5 | 2.8.5 | Local cache, see section 8. |
| WorkManager | 2.11.2 | 2.12.0-rc01 | Periodic background check for new comments. |
| DataStore (Preferences) | 1.2.1 | 1.3.0-alpha11 | Replacement for SharedPreferences, asynchronous. |
| Coil | 3.6.3 | 3.6.3 | Avatars. See section 4 for the third-party trade-off. |
| material-icons-core | 1.7.8 | 1.7.8 | Frozen at this version. Material 3 no longer ships the icons, so it is included explicitly. |
| Robolectric | 4.17 | 4.17 | Compose UI tests without a device. |

### The exception: material3 as an alpha

For the Android Gradle Plugin, the table above says "alphas are unsuitable for
a production project". `compose-material3` deviates from that, and the
reasoning should stay traceable.

Material 3 Expressive is **not usable** in the stable **1.4.0** line. It is
present there: `MaterialExpressiveTheme`, `MotionScheme`,
`MaterialTheme.motionScheme` and even `ExperimentalMaterial3ExpressiveApi` are
in the artifact. But they are all Kotlin `internal` and cannot be called from
app code. Inspecting with `javap` is misleading here: Kotlin's `internal` is
`public` in bytecode and only marked in the metadata - only the compiler tells
you. The components `ButtonGroup`, `ToggleButton`, `LoadingIndicator`,
`MaterialShapes` and `FloatingToolbar` are missing entirely in 1.4.0.

Without the alpha, only half would have been achievable: shape and typography
public, motion only as a re-creation with hand-tuned springs - and the bundled
components would have stayed on the standard scheme, because every Material
component reads its motion scheme from the theme and not from the caller.

What the decision costs:

* The alpha line changes signatures between releases. `MaterialShapes` and
  `LoadingIndicator` have already been demoted back to experimental once in
  the 1.5.0 series. Upgrading is therefore not something that happens on the
  side.
* The BOM is overridden for exactly one artifact. The remaining Compose
  artifacts stay consistent via the BOM; `material3` is pinned to its own
  version in `libs.versions.toml`.
* Everything Expressive sits behind `@OptIn(ExperimentalMaterial3ExpressiveApi)`.

As soon as 1.5.0 is stable, the special entry goes away and `material3` comes
from the BOM again.

### Deliberately *not* used

* **androidx.security:security-crypto / EncryptedSharedPreferences** –
  fully deprecated since 1.1.0-beta01 (June 2025). Google explicitly points to
  using the Android Keystore directly. We follow that (see section 6) and do
  not take on an outdated dependency.
* **kapt** – replaced by KSP, significantly faster and officially recommended.
* **XML layouts, Fragments, AppCompat activities** – not needed; the app is
  entirely Compose-based with a single `ComponentActivity`.
* **Accompanist** – the features that used to live there (pull-to-refresh,
  SwipeRefresh, permission helpers) have arrived in Material 3 or the
  platform. We use `PullToRefreshBox` from Material 3.
* **material-icons-extended** – brings several thousand vectors, of which the
  app would need a handful. Instead, the core set is used and the choice of
  icons adapted to it.
* **Mocking libraries** (Mockito, MockK) – the tests use hand-written test
  doubles. At this code size that is clearer, and as a side effect it enforces
  interfaces that the production code can make good use of too.
* **Firebase / Google Play Services** – see section 9. The app contains no
  proprietary cloud SDKs.
* **Analytics, crash or telemetry SDKs** – none.

### Third-party dependencies and their justification

The requirement is: before adding any library, check whether there is an
official Jetpack solution. Result:

| Dependency | Official alternative? | Decision |
|---|---|---|
| Retrofit + OkHttp | `HttpURLConnection`, Ktor (JetBrains, not Google) | Retrofit. Jetpack has no HTTP client; Retrofit/OkHttp is the de facto standard, is used in Google's own samples and is named in the requirements. |
| kotlinx.serialization | No Jetpack JSON solution | kotlinx.serialization, by JetBrains, compiler plugin instead of reflection. |
| Hilt | Manual DI | Hilt – preferred in the requirements and maintained by Google. |
| Coil | No Jetpack image loader | Coil 3. The alternative would be loading Gravatar images ourselves, including caching – unnecessary custom development. Avatars can also be switched off in the settings. |
| UnifiedPush Connector (`org.unifiedpush.android:connector` 3.3.5), pulls in Google Tink transitively | FCM (proprietary, see section 9) | UnifiedPush. Open standard for push without Google; the connector handles registration with the UnifiedPush app on the phone and receiving the messages. It needs Tink for Web Push encryption, which is not used here – the message has no content. The feature is optional and can be switched off per blog. |

---

## 3. Layers

```
UI (Compose, Material 3)
 │   Screens, components, navigation. Knows only UI state and events.
 ▼
ViewModel
 │   Holds StateFlow<UiState>, translates events into use case calls.
 ▼
Use Cases / Repository (domain)
 │   Business rules, status changes, cache/network coordination.
 ▼
Data
 │   WordPressApiClient (Retrofit), Room cache, CredentialStore
 ▼
WordPress REST API
```

Binding rules:

1. **The UI knows no DTOs.** Retrofit DTOs live in `data.remote.dto` and are
   mapped to domain models there. If WordPress changes a field, only the
   mapping is affected – the requirement "decouple API changes from the UI".
2. **The UI knows no HTTP errors.** All errors are translated early into a
   sealed error hierarchy (`AppError`), which the UI resolves into texts.
3. **Repositories emit a `Flow` from the cache**, not the result of a single
   network call. This way the UI updates automatically after a moderation
   action.
4. **No domain code knows Android classes.** `domain` is pure Kotlin and thus
   testable without an emulator.

### Package structure

```
de.christophlangner.commentator
├── core/            Result and error types, time/HTML helpers
├── domain/
│   ├── model/       Comment, CommentStatus, WordPressInstance, ...
│   ├── repository/  Interfaces (the implementation lives in data/)
│   └── usecase/     ModerateCommentUseCase, ReplyToCommentUseCase, ...
├── data/
│   ├── remote/      Retrofit service, DTOs, interceptors, error mapping
│   ├── local/       Room (entities, DAOs, DB), DataStore
│   ├── account/     Keystore crypto, CredentialStore, InstanceStore
│   └── repository/  Implementations
├── notification/    Channels, WorkManager worker, deep links,
│                    actions in the notification
├── push/            Instant notification via UnifiedPush (InstantPush, PushSetup,
│                    CommentatorPushService)
├── ui/
│   ├── theme/       Material 3 theme, dynamic color, dark mode
│   ├── setup/       Setup/sign-in
│   ├── inbox/       Comment list + filters
│   ├── detail/      Comment detail + reply
│   ├── settings/    Settings
│   └── navigation/  Type-safe routes, deep link handling
└── di/              Hilt modules
```

---

## 4. Multiple WordPress instances

The app manages multiple blogs. The instance is never implicit.

```kotlin
data class WordPressInstance(
    val id: String,          // stable local UUID, not the blog URL
    val name: String,
    val siteUrl: HttpUrl,    // https enforced
    val username: String,
)
```

Consequences that are already implemented today:

* Every Room row carries an `instanceId`; the primary key of the comment table
  is `(instanceId, id)`. A second blog only adds rows; it does not force a
  migration.
* Credentials are stored **per instance** under their own key in the
  CredentialStore, not in a global "the password" entry.
* The Retrofit client is not built as a singleton with a fixed base URL, but
  created and cached per instance by a `WordPressClientFactory`. The base URL
  never comes from a constant.
* Repositories take the `instanceId` as a parameter. There is no "current
  instance" deep in the data layer – the selection happens once at the top
  (`ActiveInstanceProvider`) and is passed down.
* Notifications track the "last seen comment" state per instance.

Making these decisions this way from the start paid off: multi-blog support
came without data migration and without touching the data layer.

Building on that:

* Settings are split into **cross-blog** (`AppSettings`: master switch for
  notifications, check interval, avatars, email display, thread view) and
  **per blog** (`SiteSettings`: whether this blog notifies, about what, which
  roles count as team, their colours). If roles were global, an editorial team
  on one blog would also change the colours of another. Text snippets are
  per blog as well.
* Blog-specific values live in DataStore under a key with the identifier
  appended. If it is missing, the former cross-blog key applies: when there
  was only one blog, these values applied to it, and that is where they should
  stay. Writes always go to the blog-specific key; the old one is only read.
  This way the settings layer did not need a migration either.
* The background check is **one** WorkManager job that iterates over all
  blogs. The device wakes up once instead of n times, and the requests go over
  the same connection anyway. The price is that error handling happens by hand
  per blog: a blog with rejected credentials gets its notice and does not block
  the others; a network-related error causes the whole run to be retried, but
  only after all blogs have been processed. Aborting early would mean that one
  unreachable blog holds up the notifications of all the others.
* Notifications carry the blog as a **tag** (`tag`) alongside the number
  derived from the comment ID. Comment IDs are only unique within a blog;
  folding the blog's identifier into the number via a hash would only have
  made collisions less likely, not impossible – and a collision would mean
  one blog overwriting another's notification.
* Signing in again at an address that is already set up updates the existing
  entry and keeps its `id`. Caches and notification state hang off it: a second
  entry for the same blog would report every existing comment as new again.

What is still missing – the combined inbox across all blogs – is in
`BACKLOG.md`. Sorting across instance boundaries is the open question there,
not data storage.

---

## 5. WordPress integration

Only official endpoints are used:

| Purpose | Endpoint |
|---|---|
| API discovery, auth capabilities | `GET /wp-json/` |
| Check signed-in user | `GET /wp-json/wp/v2/users/me` |
| List comments | `GET /wp-json/wp/v2/comments` |
| Single comment | `GET /wp-json/wp/v2/comments/<id>` |
| Change status / edit | `POST /wp-json/wp/v2/comments/<id>` |
| Reply | `POST /wp-json/wp/v2/comments` |
| Trash / delete | `DELETE /wp-json/wp/v2/comments/<id>` |
| Post titles | `GET /wp-json/wp/v2/posts?include=…` |

Details, parameters and error cases are in [`api.md`](api.md).

Important quirks the implementation takes into account:

* Moderation requires `context=edit`. Without the `edit` context, WordPress
  reliably returns neither `status` nor `author_email`.
* Non-public statuses (`hold`, `spam`, `trash`) are only visible with the
  `moderate_comments` capability.
* Pagination works via the `X-WP-Total` and `X-WP-TotalPages` headers, not via
  the body.
* `DELETE` moves to the trash; `DELETE?force=true` deletes permanently. If the
  trash is disabled in WordPress, the first call already deletes permanently –
  the app points this out.
* Comment content arrives as rendered HTML (`content.rendered`). The app does
  not render it in a WebView, but converts it into an `AnnotatedString` (no
  JavaScript, no remote content).

---

## 6. Authentication and handling of credentials

### Method: Application Passwords (WordPress 5.6 and later)

Alternatives examined:

| Method | Assessment |
|---|---|
| Username + real password (Basic Auth) | **Ruled out.** The requirements forbid it, and an account password cannot be revoked individually. |
| Cookie + nonce | Only intended for code running inside WordPress. Unusable for native apps. |
| OAuth 2.0 | Not part of WordPress core; requires a plugin and a client registration. Additional server dependency with no added value. |
| JWT plugins | Third-party plugins of varying quality, not a core standard. |
| **Application Passwords** | **Chosen.** Part of core since 5.6, individually revocable, separate per application, no extra plugin, with an authorization flow for native apps. |

### Flow

1. The app requests `GET /wp-json/` and reads
   `authentication['application-passwords'].endpoints.authorization`.
   If the key is missing, Application Passwords are not available
   (typically because HTTPS is missing) – the app explains this specifically.
2. The app opens this endpoint in a **Custom Tab**, with
   `app_name`, `app_id` (fixed UUID of the application) and
   `success_url=commentator://auth-callback`.
   WordPress explicitly allows app schemes at this point; only `http://` is
   rejected.
3. The user signs in to WordPress in their browser and confirms. WordPress
   redirects back to `success_url` and appends `site_url`, `user_login` and
   `password`.
4. The app receives the Application Password, verifies it immediately with
   `GET /wp/v2/users/me?context=edit` and stores it encrypted.

The user's actual account password is **never** passed to the app – it is
entered exclusively in the browser.

As a fallback, an Application Password generated in the WP profile can be
entered manually. This is needed for installations where the authorization
endpoint is disabled.

### Storage

Since `EncryptedSharedPreferences` is deprecated, the app does the encryption
itself:

* A key `commentator_credentials_v1` in the **Android Keystore**
  (`AES/GCM/NoPadding`, 256 bits, `setUserAuthenticationRequired(false)`,
  `setRandomizedEncryptionRequired(true)`), created when first needed.
  The key never leaves the Keystore; on devices with StrongBox/TEE it lives in
  hardware.
* The Application Password is encrypted with a random IV, `IV || Ciphertext`
  is Base64-encoded and stored in DataStore.
* Storage key: `credential_<instanceId>` – so multi-instance support is given
  here too.
* On sign-out, both the DataStore entry and the Keystore key are deleted.

### Further safeguards

* **HTTPS only.** The network configuration forbids cleartext traffic
  (`cleartextTrafficPermitted="false"`), and the app rejects `http://` URLs
  already at input.
* **No credential logging.** The OkHttp logging interceptor only runs in debug
  builds and redacts `Authorization`. In release builds it is completely
  absent.
* The `Authorization` object exists as its own type whose `toString()` is
  overridden, so that it does not accidentally end up in logs or exception
  messages.
* `android:allowBackup="false"` – the encrypted credential record is not
  exported via cloud backups.
* `FLAG_SECURE` is set in the step for manually entering the Application
  Password, so that it does not end up in screenshots or the app overview.
  Encapsulated in `ScreenshotProtection` so that the promise is testable -
  including the reverse direction, that the flag disappears again on leaving.

### Expired or revoked credentials

If WordPress responds with **401** or with the error code
`rest_cannot_*`/`invalid_username`, the app marks the session as invalid, does
not discard the stored credentials immediately (the user should see the
reason), but blocks all write actions and offers to authorize again. A **403**
is distinguished from this: the credentials are valid, but the role is not
sufficient.

---

## 7. Error handling

Central, sealed hierarchy `AppError` in `core/error`:

| Case | Detection | Presentation to the user |
|---|---|---|
| No connection | `UnknownHostException`, `ConnectException`, connectivity status | "No internet connection." + note about the cache |
| Timeout | `SocketTimeoutException` | "The server did not respond in time." |
| TLS problem | `SSLException` | "The secure connection failed." |
| 401 | HTTP code | "The credentials are invalid or have been revoked." + action "Sign in again" |
| 403 | HTTP code | "This account is not allowed to moderate comments." |
| 404 | HTTP code | Context-dependent: comment deleted or REST API not reachable |
| 429 | HTTP code, `Retry-After` | "Too many requests. Try again in n seconds." |
| 5xx | HTTP code | "The server reports a problem." |
| Invalid configuration | `/wp-json/` returns no JSON or no `namespaces` entry | "No WordPress REST API was found at this address." |
| Unknown | Everything else | Generic text + code for follow-up questions |

Rules:

* Stack traces never reach the UI. The text comes from `strings.xml`.
* WordPress error codes (`code`/`message` in the JSON body) are evaluated and
  feed into the mapping, but the raw message is not shown unfiltered.
* Technical details go through `Timber`-style logging in debug builds only. No
  custom logger is built: `android.util.Log`, encapsulated in `core/AppLog`,
  which does nothing in release builds.

---

## 8. Offline behaviour

### Reading

Room is the **single source of truth** for the comment list. The flow:

1. The UI observes a `Flow` from Room and shows the cache immediately.
2. In parallel, the refresh runs against the API.
3. Results are written to Room, and the UI updates as a result.

Cached are: comments (including status), post titles, blog configuration, the
count per filter, team membership and the time of the last successful
synchronisation.

The count per filter (`filter_counts`) is not stored there just for the filter
bar. An empty comment table is ambiguous — it means either "this filter is
empty" or "this filter has never been fetched", and for the UI that is the
difference between the empty state and the loading indicator. Without the
stored number, it had to assume the second interpretation and show placeholder
cards at startup until the server confirmed what the app already knew. A
stored 0 is an answer; a missing entry means "never fetched".

The team (`team_members`, `team_roles`) is stored there for the same reason.
More depends on it than the role badge: without a known team nobody counts as
a member, collapsed roles expand, and the background check reports precisely
the roles that are muted. If fetching fails, the stored state therefore applies
instead of an empty team — unlike with comments, "nothing known" is not a
harmless answer here. The selection of which roles count as team determines the
stored mapping; a change therefore discards it as well (`invalidate`).

The number only shortens the wait; it does not replace fetching: after a
restart, a refresh still happens every time the screen is opened. That is also
why the time of the last refresh per filter deliberately stays in memory — it
decides whether a fetch is skipped, and that decision should not survive a
restart.

The distinction between local and fresh state is **visible**: a bar above the
list shows the time of the last synchronisation and marks the offline state.
There is no silent cache.

A special case is the reply thread in the detail view: the list only ever loads
one status, so an approved reply to a pending comment would never get into the
cache that way. That is why `fetchComment` additionally fetches the thread with
`GET /wp/v2/comments?parent=<id>&status=all`. If this follow-up fetch fails,
the comment itself is kept — only the thread is missing then.

### Writing

Deliberate decision: **offline moderation is not offered in version 1**,
rather than building it half-finished.

Rationale: an offline queue for moderation needs a conflict strategy (the
comment was moderated or deleted on the web in the meantime), otherwise the app
overwrites other people's decisions. That is considerably more work than it
looks from the outside, and the requirements explicitly allow blocking write
actions while offline.

Implementation: without a connection, moderation and reply actions are
disabled and explained ("Offline – moderation is unavailable"). Nothing is
silently lost, because nothing is silently accepted. The queue including
conflict resolution is in the backlog as P2.

---

## 9. Notifications about new comments

### Comparison of options

| Option | Load on WordPress | Latency | External dependency | Assessment |
|---|---|---|---|---|
| 1. Polling against `wp/v2/comments` | Medium – every check is a full comment query | 15 min | none | Works everywhere, but unnecessarily expensive. |
| 2. WordPress webhooks | Low | Seconds | **Requires a reachable endpoint**, which a phone does not have | Not usable without a server of our own. |
| 3. Plugin as push bridge | Low | Seconds | Plugin + push service | Sensible, but only together with option 4 or a relay of our own. |
| 4. Firebase Cloud Messaging | Low | Seconds | **Google account, Play Services, Firebase project, service account key on the WP server** | Real push, but proprietary and with data flowing to Google. |
| 5. Combination | Low | Seconds to minutes | depending on the stage | – |
| 6. UnifiedPush | Low | Seconds | UnifiedPush app on the phone (e.g. ntfy) and its push server; plugin 1.5 or later | Real push without Google. The push server only learns that and when a comment was posted. |

### Decision

The choice is a **combination of 1 and 3 without third-party infrastructure**:

* A very small WordPress plugin (`commentator-bridge`) provides an endpoint
  `GET /commentator/v1/status`, which at its core returns three values: the
  number of pending comments, and the ID and timestamp of the newest comment
  (later versions add the newest comment of every status, see
  `docs/api.md`).
  That is **one** indexed query instead of a full comment listing – so the
  WordPress installation is precisely *not* burdened unnecessarily.
* The app checks this endpoint periodically via **WorkManager** (default
  interval 15 minutes, configurable, only when a connection is available).
  Only if the values have changed are the new comments loaded.
* If the plugin is not installed, the app automatically falls back to an
  economical core query:
  `wp/v2/comments?status=hold&per_page=1&_fields=id,date_gmt&context=edit`.
  So the app works fully without the plugin, just somewhat more expensively.

**Why not FCM:** FCM requires Google Play Services on the device, a Firebase
project and a service account key on the WordPress server. Metadata about every
new comment would then flow through Google's servers. The requirements
explicitly demand avoiding proprietary cloud services when there is an
equivalent local solution, and not sending data to third parties. For
moderating a blog, a latency of minutes is functionally equivalent to a latency
of seconds.

**But the architecture remains push-capable:** detection of new comments sits
behind the `NewCommentSource` interface. Today there is exactly one
implementation (`PollingNewCommentSource`). Originally, the plan was to add a
second source alongside it for real push.

### Addendum: instant notification via UnifiedPush

The wish for notifications within seconds remained, only the route via Google
did not. Option 6 is therefore implemented as an **optional addition** to 1 and
3, switchable per blog in its settings under "Notify instantly":

* The app registers per blog with a UnifiedPush app on the phone (the
  UnifiedPush instance is the `instanceId`), such as ntfy, and receives an
  endpoint address. It stores it with the plugin via
  `POST /commentator/v1/push`. When switching off, when removing the blog and
  when unregistered by the UnifiedPush app, `DELETE /commentator/v1/push`
  withdraws it; likewise for a replaced address.
* The plugin hooks into `wp_insert_comment` (not `comment_post`, which only fires for the comment form) and sends to every stored
  address of all accounts with `moderate_comments` a non-blocking `POST` with
  the body "new" – no name, no text, no identifier. Comments that arrive as
  spam or trash wake nobody.
* The message is **deliberately contentless and unencrypted**; the connector
  delivers it with `decrypted=false`. Encryption would protect nothing, because
  there is nothing in it. Each message triggers a one-off, expedited check of
  all blogs (`CommentSyncWorker`, unique as "commentator-push-sync" with
  `KEEP`, so that a burst of wake-ups does not trigger just as many checks).
  The app then fetches the comments itself via the REST API as usual.
* The regular check continues unchanged and catches up on whatever was lost on
  the push route. The wake-up only shortens the wait.

This made the planned `FcmNewCommentSource` unnecessary: a wake-up that
triggers the same check earlier does not need a source of its own.
Notifications, deduplication, deep links and UI remained untouched.

What the push server learns: that and when a comment was posted on a blog,
nothing more. Anyone running their own ntfy keeps even that to themselves. The
plugin only accepts public HTTPS addresses and sends via
`wp_safe_remote_post`, so that the blog cannot be abused as a stepping stone
into its own network.

The code lives in `push/`. `PushSetup` is the interface through which the blog
settings control instant notification – as an interface, so that the screen
can be tested without UnifiedPush and without a device. `InstantPush`
implements it with the connector and coordinates with the plugin;
`CommentatorPushService` receives the connector's events. The setting shows its
state: needs plugin 1.5 or later, needs a UnifiedPush app, being set up, active
via the selected app, or error – such as a plugin that is too old,
recognisable by a 404 on `/push`.

### Actions in the notification

Every comment notification carries up to three buttons: "Reply" (for a pending
comment "Approve and reply", with text input in the notification via
`RemoteInput`), "Approve" (only for pending comments) and "Spam" (not for
spam). They only appear for accounts with `moderate_comments`.

The non-exported `NotificationActionReceiver` does not process anything
itself, but hands off to the `NotificationActionWorker`: expedited WorkManager
work, at most one running action per comment (`ExistingWorkPolicy.KEEP`), using
the same use cases as the UI.

**No retry on errors.** WorkManager could retry a failed action later – but a
silently retried moderation might overwrite a decision that has since been
made on the web. Instead, the notification stays and states the reason; a
failed reply is shown in it with its text, so that nothing written is lost.

**Completion after a direct reply.** From Android 15, the system holds on to a
notification after a direct reply
(`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`) and ignores a plain `cancel()`. The
notification is therefore replaced by a short "Done" confirmation, which
disappears after two seconds via `setTimeoutAfter`.

### Cleaning up handled notifications

A notification about a comment that was handled long ago is noise.

* **Handled in the app:** if a comment is moderated, replied to or opened in
  the detail view, its notification disappears. The `CommentAlerts` interface
  (implemented by `CommentNotifier`) keeps `ModerateCommentUseCase`,
  `ReplyToCommentUseCase` and `CommentDetailViewModel` free of Android classes
  for this.
* **Handled on the web:** the background check reconciles open notifications
  before reporting anything new – and only if any are open at all. A single
  request suffices: `GET wp/v2/comments?include=<ids>&status=any`. `any`
  includes spam and trash, `all` does not; if a comment is missing from the
  response, it has been permanently deleted.
* Removed are those that are deleted, spam or in the trash, and those that were
  reported as pending and have since been approved. Those reported as already
  approved stay: they may still be waiting for a reply. The notification
  carries the status at the time of reporting in its extras. A summary
  notification that has become empty disappears along with them.

Every comment notification and the summary notification carry the blog's icon
as a large image (`setLargeIcon`). `SiteIconLoader` loads it via Coil from the
same image cache as the header and settings, with `allowHardware(false)`:
notifications are drawn by another process, which cannot do anything with
hardware bitmaps. It comes from the blog itself; no connection to third parties
is made.

### Notification channels

The channels have remained unchanged by actions, cleanup and instant
notification.

| Channel | ID | Content | Default importance |
|---|---|---|---|
| New comments | `new_comments` | Comments awaiting moderation | `DEFAULT` |
| Replies | `moderation_events` | Comments replying to an existing comment | `LOW` |
| Synchronisation | `sync_status` | Persistent notices about problems, e.g. invalid credentials | `LOW` |

Each of these channels is actually used - a channel that appears in the system
settings but never reports anything is misleading for the user. The assignment
is decided by `Comment.isReply`. All channels can be controlled individually in
the Android system settings; the app links directly there. The runtime
permission `POST_NOTIFICATIONS` (Android 13 and later) is only requested in
context.

**The first run reports nothing.** On the very first run there is no point of
comparison - everything pending would be "new" and arrive as a flood. The first
run therefore only records the state. Whether such a baseline exists is
answered by `NewCommentSource.hasBaseline`; the background service deliberately
no longer reads a database field for this that the source writes on the side.
The state is recorded even when nothing is pending - otherwise the next run
would again count as the first and the first real notification would never
come.

**No duplicate notifications:** only what is newer than the per-instance stored
`lastNotifiedCommentId`/`lastNotifiedDate` is reported. In addition, IDs that
have already been reported are recorded in Room. The notification ID is derived
deterministically from the comment ID, so that reporting the same comment again
replaces the same notification instead of creating a second one.

### Deep links

Notifications open
`commentator://comment/<instanceId>/<commentId>` via a
`PendingIntent` with `FLAG_IMMUTABLE` on the single activity. The route is the
same one the app uses internally – there is no second path into the detail
view.

---

## 9a. Switchable app icon

Android offers no way to change the launcher icon at runtime. The usual
approach is therefore one `activity-alias` per variant, all pointing to the
same activity; exactly one is always enabled
(`PackageManager.setComponentEnabledSetting`).

Three things are crucial here and are therefore recorded in the code:

* **Order.** Enable the new variant first, then disable the old one. The other
  way round, there is a moment with no enabled launcher icon - the app then
  disappears from the home screen, permanently with some manufacturers.
* **`DONT_KILL_APP`.** Without this flag, Android kills the process
  immediately, in the middle of using the settings.
* **Namespace instead of package ID.** The alias class name follows the
  module's `namespace`, whereas the installed package ID follows the
  `applicationId` - in the debug build with the `.debug` suffix. Building the
  class name from the package ID points nowhere, and switching has no effect.
  A test captures this.

What counts for the display is the state in the PackageManager, not a stored
setting. That way the two cannot diverge, for example when the app data is
cleared but the system state remains.

Known limitation: `android:icon` on `<application>` stays unchanged. The system
settings and the share sheet therefore continue to show the default variant.

---

## 10. Scope of the WordPress plugin

The `commentator-bridge` plugin is optional and changes **no** WordPress
behaviour. It only adds routes under `commentator/v1`, all with the same
authentication as the core API:

| Route | Capability | Purpose |
|---|---|---|
| `GET /status` | `moderate_comments` | Compact state for the regular check |
| `GET /summary` | `moderate_comments` | Comment count per status in one call, so the filter bar doesn't need five queries |
| `GET /team` | `moderate_comments` | Roles allowed to write or moderate, and their members |
| `POST /empty` | `moderate_comments` | Permanently empty spam or trash in batches (since 1.2.0) |
| `GET`, `POST`, `DELETE /blocklist` | `manage_options` | Read and maintain the `disallowed_keys` blocklist (since 1.2.0) |
| `POST`, `DELETE /push` | `moderate_comments` | Store and withdraw the push address for instant notification (since 1.5.0) |
| `POST /push/test` | `moderate_comments` | Test wake-up to your own addresses, waits for the push server (since 1.7.0) |

Writes happen in only two places, and both correspond to what is possible in
the backend anyway: `/blocklist` changes the `disallowed_keys` option – the
same list as under Settings → Discussion –, `/push` the user meta
`commentator_push_endpoints` of the signed-in account. `/empty` deletes what
the "Empty Spam" or "Empty Trash" button deletes in the backend.

Since 1.5.0, the plugin hooks into `wp_insert_comment` and **optionally sends
outward**: if a push address is stored for an account with
`moderate_comments`, a contentless wake-up goes there for every new comment
that does not arrive as spam or trash (section 9); since 1.6.0 also for every
status change that does not come from the app. Without a stored address it
sends nothing. It creates no tables and sets no cookies.

---

## 10a. Seams for tests

In four places there is deliberately an interface where a concrete class would
have sufficed. All four arose from a testing problem and decoupled the
production code as a side effect:

| Interface | Instead of | Why |
|---|---|---|
| `CredentialSource` | `CredentialStore` | Otherwise the HTTP interceptor depends on DataStore and the Android Keystore and cannot be tested without a device. |
| `WordPressApiProvider` | `WordPressClientFactory` | The repository and background check only need "give me the client for this instance", not the whole client creation. |
| `SiteUrl` | private method in the repository | This is where the decision is made that only HTTPS is allowed. A security rule belongs in a testable place. |
| `InboxScreenContent` / `CommentDetailBody` | Screens with ViewModel | The UI can be fed a state and checked for events, without Hilt and without a device. |

### Pitfalls with Compose tests under Robolectric

Two settings are mandatory, otherwise the tests are worthless rather than red:

* `@Config(qualifiers = "de-rDE-w411dp-h891dp")` – without a screen size,
  Robolectric measures 0 × 0, and in Compose nothing then counts as visible.
  And without a locale, the test runs against the English texts from
  `values-en`, while the app's default language is German.
* `graphicsMode=NATIVE` in `robolectric.properties`.

---

## 11. Test strategy

| Level | Tools | Content |
|---|---|---|
| Unit (JVM) | JUnit 4, kotlinx-coroutines-test, Turbine | DTO mapping, status logic, error mapping, use cases, ViewModels |
| Network (JVM) | MockWebServer | Pagination, header evaluation, error codes, auth header |
| UI | Compose UI test + Robolectric | List, filters, detail, reply, moderation action, error and empty states |
| Integration | Docker Compose WordPress | Real end-to-end run against a local installation |

Integration tests run exclusively against the local Docker instance. There is
no test path pointing to a production blog; the test configuration contains
only `localhost` addresses.

---

## 12. Known risks

* **Kotlin 2.3 instead of 2.4** because of KSP – raise it once KSP is released
  for 2.4.
* **Robolectric + Compose** is sensitive to version jumps. If UI tests break
  there, the same tests run unchanged as instrumentation tests on a device.
* **Trash behaviour** depends on the installation's `EMPTY_TRASH_DAYS`. The
  app cannot read this and therefore explicitly points it out before permanent
  deletion.
* **Gravatar** is a connection to a third party (Automattic). Avatars can
  therefore be switched off; see `docs/privacy.md`.
