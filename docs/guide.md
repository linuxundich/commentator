# Commentator – Guide

This is the detailed guide. For an overview, see the
[README](../README.md).

A native Android app for moderating WordPress comments.

Commentator is not a WordPress reader and not a replacement for the admin
backend. It is a tool for exactly one job: reviewing, approving, rejecting and
replying to the day's comments – quickly, from your phone, without detouring
through the web interface. Across several blogs, too.

---

## Who is this for?

For people who run one or more WordPress blogs of their own, get comments
there regularly, and don't want to moderate them through `wp-admin` on a phone
screen. The app requires no account with any service, no app store sign-in and
no server run by the developer – just your own WordPress installation.

---

## Features

### Reviewing comments

* Fetches comments via the official WordPress REST API
* Clear list showing author, time, text, the related post and status
* Filters: All, Pending, Unanswered, Approved, Spam, Trash; the last one
  chosen is remembered
* “Unanswered” shows approved comments from readers that don't have a reply
  from the team yet. WordPress has no such state; the app derives it from the
  pages it has loaded and therefore shows no count
* Optional avatars (off by default, see Privacy)
* Switchable app icon (green or blue)
* Pull-to-refresh and refresh from the top bar
* Paged loading, even for blogs with many comments
* Local cache: the list appears instantly, even without a connection

### Multiple blogs

* Any number of WordPress blogs in one app
* Switcher in the top bar once there is a second blog; shows each blog's icon,
  name, address and number of pending comments
* Separate settings per blog: whether and about what it notifies, its roles
  and colors, its text snippets
* A blog can be removed on its own; the others remain untouched
* Tapping a notification switches to the blog it belongs to

### Moderating

* Approve
* Revert to “pending”
* Mark as spam
* Move to trash
* Delete permanently (with confirmation)
* Edit the comment text, if the account is allowed to
* Every reversible action immediately offers “Undo”
* Approve and mark as spam directly from the notification, too

### Replying

* Write a reply directly from the detail view
* Published as a real WordPress comment, linked to the original comment
* Replying to a comment that is still pending approves it first
  (“Approve and reply”), as in the WordPress backend – otherwise the reply
  would stay invisible on the site
* Reply directly from the notification as well, with text input inside the
  notification itself
* Confirmation on success, an understandable error message when something
  goes wrong

### Notifications

* Alerts about new comments awaiting moderation, even when the app is closed
* Tapping opens the comment in question directly
* Buttons in the notification: “Reply” (“Approve and reply” for pending
  comments), “Approve” and “Spam”. If an action fails, the notification stays
  and states the reason
* Notifications clean up after themselves: once a comment is moderated,
  answered or opened in the app, its notification disappears; if it was
  moderated on the web, the next check catches up on that
* The blog's icon appears as an image in every notification
* Optional instant notifications via UnifiedPush (for example with ntfy),
  without Google; see below
* Separate notification channels, each controllable through Android's system
  settings
* No comment is ever reported twice
* By default the app does not notify about your own posts; this can be turned
  on in the **Your own account** dialog
* Configurable check interval

### Miscellaneous

* Material 3, Dynamic Color, light and dark mode following the system setting
* German and English
* No ads, no analytics, no telemetry

---

## Requirements

### Device

* Android 8.0 (API 26) or newer

### WordPress

* WordPress 5.6 or newer (for Application Passwords)
* **HTTPS is mandatory.** Without a secure connection WordPress doesn't offer
  Application Passwords, and the app never allows cleartext traffic.
* The REST API must be reachable (`https://<blog>/wp-json/`). Some security
  plugins block it.
* The account used needs the `moderate_comments` capability (Editor or
  Administrator role). Without it the app still sets up the connection but
  locks all moderation actions.

### Optional: the `commentator-bridge` plugin

Not required. It makes the regular check for new comments considerably
lighter by returning three numbers instead of a full comment list. It also
adds endpoints for the team, for “Empty spam” and “Empty trash” in a single
request, and for the disallowed-keys list. From version 1.5 it enables instant
notifications via UnifiedPush. The app works fully without the plugin.

### Push infrastructure

**Not needed.** There is no Firebase, no relay server and no Google Play
Services. The reasoning behind this decision and the comparison with the
alternatives are in
[`docs/architecture.md`](architecture.md#9-notifications-about-new-comments).

If you want new comments reported within seconds rather than at the next
check, you can turn on **instant notifications via UnifiedPush** per blog.
This needs a UnifiedPush app on the phone, such as ntfy, and the plugin from
version 1.5 on the blog. For each new comment the plugin sends only an empty
wake-up call through the push server; the app then fetches the comments from
the blog itself as usual. All the push server learns is that, and when, a
comment was posted.

---

## Setup

### 1. Prepare WordPress

1. Make sure the blog is reachable over HTTPS.
2. Open `https://<blog>/wp-json/` in a browser. JSON must appear. If you get
   an error page instead, something is blocking the REST API.
3. Have an account with the Editor or Administrator role ready.

### 2. Optional: install the plugin

```bash
cp -r wordpress-plugin/commentator-bridge <wordpress>/wp-content/plugins/
```

Then activate it in the backend under “Plugins”. The app detects it
automatically and points this out in the settings.

If the plugin is installed later, the app notices the next time the inbox is
refreshed or the settings are opened. There is no need to sign in again.

### 3. Create credentials

The convenient way goes through the app itself – step 4 takes care of it.

To do it manually: in WordPress, under **Users → Profile → Application
Passwords**, enter a name such as “Commentator Android” and note down the
generated password. It is shown only once.

Your account password is not needed for this and does not belong in the app.

### 4. Set up the app

<img src="screenshots/01-einrichtung.png" width="200" alt="Adding a blog" align="right">

1. Launch the app.
2. Enter the blog address, for example `https://example.com`. The app checks
   whether a WordPress REST API is reachable there.
3. Choose **Authorize in WordPress**. The browser opens your installation's
   authorization page. Sign in there and confirm.
4. WordPress returns to the app, which checks the received Application
   Password right away and stores it encrypted.

If the installation doesn't offer the authorization flow, **Enter application
password manually** leads to the same result – using the password created in
step 3.

### 5. Add more blogs

**Settings → Blogs → Add another blog** walks through the same steps. Each
blog needs its own Application Password; the app keeps separate credentials
and a separate cache for each.

Once there is a second blog, the name in the inbox's top bar becomes a
switcher. Signing in again to a blog that is already set up replaces its
credentials – no second entry is created.

### 6. Set up notifications

On first launch the app asks for notification permission (Android 13 and
later). The app settings hold the main notification switch and the check
interval; fine-tuning per channel happens in Android's system settings, which
are linked from within the app.

Whether an individual blog notifies, and about what, is set in its own
settings under **Settings → Blogs → <blog name>**. A side project can stay
quiet while the main blog notifies.

The shortest possible interval is 15 minutes – WorkManager's lower limit for
periodic work. It applies to the pass over all blogs together: they are
checked in a single run so the device only wakes up once.

It gets faster with **Notify instantly** in the respective blog's settings.
This requires a UnifiedPush app on the phone (such as ntfy) and the
`commentator-bridge` plugin from version 1.5 on the blog. When it is switched
on, the app registers with the UnifiedPush app and stores the address it
receives with the plugin. The settings row then shows which app delivers the
notifications, or why it didn't work – for example because the plugin is too
old. The regular check keeps running and catches anything lost on the push
route. When the option is switched off or the blog is removed, the address is
withdrawn from the plugin again.

Important: the UnifiedPush app needs an exemption from battery optimization.
Without it, Android 15 and later deny it background service, and wake-up calls
only arrive the next time it is opened. ntfy points this out itself on first
launch. With the exemption, a notification arrived about half a second after
the comment in testing.

Out of the box this goes through the public ntfy.sh server. Your own ntfy
server works just as well: set it as the default server in the ntfy app, then
switch “Notify instantly” off and on again; the app obtains a new address and
names the server in the settings row. Two conditions: the server must be
publicly reachable over HTTPS, because the plugin rejects addresses on the
local network. And it must be reachable **from the blog's server** – a server
reachable only via IPv6 (typical behind carrier-grade NAT) is of no use if the
blog's hosting only does IPv4.

Whether the route works is shown by **Test** under “Notify instantly”: the
plugin sends a test wake-up call, and if it arrives, the notification
“Instant notifications work” appears. In the WordPress profile, the section
“Commentator: Sofortmeldung” (instant notifications) shows where the blog
sends wake-up calls, with “Entfernen” (remove) and the same test (from plugin
1.7.0).

Since plugin 1.6.0, moderating on the web also sends a wake-up call: the
notification for a comment handled there then disappears immediately. If
every notifying blog is connected this way, the app only checks every six
hours as a safety net.

The buttons in notifications only appear for accounts that are allowed to
moderate comments.

### 7. Build the app

```bash
cd android
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk    # path depends on your system
./gradlew assembleDebug
```

The APK is then at
`android/app/build/outputs/apk/debug/app-debug.apk`.

### 8. Install the app

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

## Development

### Tools

| Tool | Version |
|---|---|
| JDK | 21 (minimum 17) |
| Gradle | 9.7.1, via the bundled wrapper |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.3.21 |
| Android SDK Platform | 37 (`platforms;android-37.0`) |
| Build Tools | 37.0.0 |
| Android Studio | Quail 4 (2026.1.4) or newer, optional |

Android Studio is not required – the project builds entirely from the command
line.

On Arch Linux:

```bash
sudo pacman -S --needed jdk21-openjdk
yay -S --needed android-sdk-cmdline-tools-latest android-sdk-platform-tools \
                android-sdk-build-tools android-platform
```

`android/local.properties` must point to the SDK:

```properties
sdk.dir=/opt/android-sdk
```

This file does not belong in the repository and is listed in `.gitignore`.

### Build commands

```bash
cd android

./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (see below)
./gradlew testDebugUnitTest      # unit and UI tests on the JVM
./gradlew connectedDebugAndroidTest   # instrumentation tests, device required
./gradlew lint                   # Android Lint
./gradlew check                  # lint and tests together
```

### Versioning

Version name and build number are derived from Git at build time, not
maintained by hand:

| | |
|---|---|
| `versionCode` | Number of commits (`git rev-list --count HEAD`) |
| `versionName` | Name of the tag on `HEAD`, otherwise `<base version>-dev+<count>.g<commit>` |

The base version is set as `COMMENTATOR_VERSION` in `gradle.properties` and
applies as long as no tag is set. If the working tree has uncommitted
changes, `.dirty` is appended.

Without Git – for example when building from a source archive – fallback
values apply so the build doesn't fail for lack of the tool.

The **About this app** screen shows both, together with the commit the build
was made from. This lets a bug report be matched unambiguously to a specific
state of the code.

### Database schema

Room exports the schema to `android/app/schemas/`. These files belong in the
repository: only with them can later migrations be verified automatically.

### Release build

```bash
./gradlew assembleRelease
```

Release builds use R8 with code and resource shrinking.

The repository contains **neither a keystore nor passwords**. The signing
configuration reads four Gradle properties that must live outside the
project – in `~/.gradle/gradle.properties`:

```properties
COMMENTATOR_STORE_FILE=/home/<user>/.android/commentator-release.p12
COMMENTATOR_STORE_PASSWORD=<password>
COMMENTATOR_KEY_ALIAS=commentator
COMMENTATOR_KEY_PASSWORD=<password>
```

If even one of them is missing, the result is an **unsigned** release rather
than a build error. A third-party clone of the repository therefore still
builds, just without a signature.

A suitable key is created like this:

```bash
keytool -genkeypair \
  -keystore ~/.android/commentator-release.p12 \
  -storetype PKCS12 -alias commentator \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=<Name>, O=<Organization>, C=DE"
chmod 600 ~/.android/commentator-release.p12
```

> **The key cannot be replaced.** Android only allows an update if it is
> signed with the same key as the installed version. If the key is lost, the
> app can only be uninstalled and reinstalled – losing all local data. The
> keystore and password therefore belong in a backup and a password manager,
> not in the repository.

### Tests

| Level | Command | Content |
|---|---|---|
| Unit | `./gradlew testDebugUnitTest` | Mapping, status logic, error mapping, use cases, ViewModels |
| Network | same | MockWebServer: parameters, pagination, error codes, auth header |
| UI | same | Compose tests via Robolectric: list, filters, moderation, empty and error states |
| Integration | see below | Against a local WordPress installation |

There is no test path that points to a production blog. The integration tests
skip themselves as long as no environment variables are set, and refuse to
run if the given address is not `localhost` or `127.0.0.1`.

To run them against the local environment:

```bash
cd docker
./scripts/generate-cert.sh
docker compose up -d
./scripts/seed.sh          # prints an application password at the end

cd ../android
COMMENTATOR_IT_URL=https://localhost:8443 \
COMMENTATOR_IT_USER=moderator \
COMMENTATOR_IT_PASSWORD='<the printed password>' \
  ./gradlew :app:testDebugUnitTest --tests '*WordPressIntegrationTest'
```

This exercises the complete path against a real WordPress installation:
REST API detection, sign-in, permissions, fetching comments, status changes
including reverting them, replies and the plugin's endpoint. The tests clean
up after themselves.

### Local WordPress test environment

```bash
cd docker
./scripts/generate-cert.sh
docker compose up -d
./scripts/seed.sh
```

To stop it:

```bash
docker compose down        # data is kept
docker compose down -v     # discard everything
```

Details, including access from the emulator, are in
[`docker/README.md`](../docker/README.md).

Podman works instead of Docker as well. Its socket replaces the Docker
service, and `docker-compose` talks to it via `DOCKER_HOST`:

```bash
systemctl --user start podman.socket
export DOCKER_HOST=unix://$XDG_RUNTIME_DIR/podman/podman.sock
docker-compose up -d
```

### Emulator without Android Studio

This is how an emulator was set up on Arch Linux using only the command-line
tools. The SDK lives in `~/Android/Sdk`:

```bash
sdkmanager emulator platform-tools "platforms;android-37.0" "build-tools;37.0.0" \
           "system-images;android-37.0;google_apis_playstore;x86_64"

avdmanager create avd -n Pixel_10_API_37 \
  -k "system-images;android-37.0;google_apis_playstore;x86_64" -d pixel_10
```

API 37 corresponds to Android 17. Set `hw.ramSize=4096` in
`~/.android/avd/Pixel_10_API_37.avd/config.ini`, then start it:

```bash
emulator -avd Pixel_10_API_37 -gpu host
```

For the local test environment the emulator doesn't have to go through
`10.0.2.2`. It is simpler to forward the port:

```bash
adb reverse tcp:8443 tcp:8443
```

After that, `https://localhost:8443` works for both the host and the
emulator, and `WP_SITE_URL` doesn't need to be set.

The test certificate is copied to the emulator with
`adb push docker/certs/server.crt /sdcard/Download/` and installed there under
**Settings → Security & privacy → More security settings → Encryption &
credentials → Install a certificate → CA certificate**.

### Triggering the background check immediately

WorkManager doesn't run periodic work early; waiting for the next check can
take up to 15 minutes. The debug build therefore includes a receiver that
triggers it immediately:

```bash
adb shell am broadcast \
  -n de.christophlangner.commentator.debug/de.christophlangner.commentator.notification.DebugSyncReceiver
```

`DebugSyncReceiver` exists only in the debug build and is protected by
`android.permission.DUMP`, so it can only be reached via `adb`, not by other
apps.

---

## Architecture

Described in detail in [`docs/architecture.md`](architecture.md). In short:

```
UI (Compose, Material 3)
 ▼
ViewModel (StateFlow)
 ▼
Use Cases / Repository
 ▼
WordPress API client (Retrofit) + Room cache + Keystore
 ▼
WordPress REST API
```

The key decisions:

* **Room is the single source for what is displayed.** Network responses are
  written to the database, and the UI observes the database. That is why
  offline is not a special case.
* **The UI knows neither DTOs nor HTTP status codes.** The data layer
  translates both into domain models and a closed error hierarchy.
* **The WordPress instance is never implicit.** Every database row carries an
  `instanceId`, the HTTP client is created per instance, and credentials are
  stored per instance. Because it was like that from the start, multi-blog
  support needed no data migration.
* **Detection of new comments sits behind an interface.** It works by
  polling. The optional instant notification via UnifiedPush is only a
  wake-up call that triggers the same check immediately – notifications, deep
  links and UI were left untouched by it.

Directories:

```
android/                The app
wordpress-plugin/       Optional commentator-bridge plugin
docker/                 Local WordPress test environment
docs/                   Architecture, API usage, privacy
```

---

## Security

* **No WordPress password in the app.** Only Application Passwords are used.
  With the authorization flow, the account password is only entered in the
  browser; the app never sees it. An Application Password can be revoked
  individually in the WordPress profile.
* **Encrypted storage.** The Application Password is stored as
  `AES-256-GCM` ciphertext in DataStore. The key lives in the Android
  Keystore and never leaves it. `EncryptedSharedPreferences` is deliberately
  not used – the library has been deprecated since mid-2025, and Google
  recommends exactly this approach instead.
* **HTTPS only**, enforced via the network security config, the manifest and
  a dedicated interceptor.
* **No credentials in logs.** HTTP logging exists only in the debug build and
  redacts the `Authorization` header there. The credential types don't reveal
  their value via `toString()`; two unit tests guard this.
* **No credentials in screenshots.** The step for entering the Application
  Password manually sets `FLAG_SECURE`; there Android blocks screenshots and
  shows no preview in the recent-apps overview. The rest of the setup flow and
  the whole app remain capturable.
* **No credentials in backups.** `allowBackup="false"` and empty extraction
  rules.
* **No credentials in the repository.** Neither keystores nor signing
  configuration nor `local.properties` are checked in.

Handling of comment data: it contains personal data of third parties, in
particular email addresses. It is stored only in the app-private database, is
not shared with anyone, and is deleted completely on sign-out. Display of
email addresses is turned off by default.

---

## Privacy

Fully described in [`docs/privacy.md`](privacy.md).

In short: the app only connects to the configured WordPress instance. The
only possible exception is avatar images, which come from Gravatar on a
standard installation – which is why avatar display is **off** by default.

Turning on the optional instant notifications additionally involves a push
server: for new comments the plugin sends an empty wake-up call there, and the
UnifiedPush app on the phone receives it. Even then, the app itself only
connects to your own blog.

There are no analytics, no crash reports, no telemetry, no ads, no
advertising IDs and no developer server. The app itself requests three
permissions: `INTERNET`, `ACCESS_NETWORK_STATE` and `POST_NOTIFICATIONS`. The
final APK contains three more, contributed by `androidx.work` during manifest
merging – `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`.
None of them grants access to user data; [`docs/privacy.md`](privacy.md)
lists each one individually.

---

## Known limitations

* **No offline moderation.** Without a connection, write actions are locked
  and marked as such. A queue would need conflict resolution for comments
  moderated elsewhere in the meantime; building that halfway would be worse
  than leaving it out.
* **Delayed notifications.** The check runs periodically, at most every 15
  minutes. Faster is only possible with the optional instant notifications
  via UnifiedPush, which require a UnifiedPush app on the phone and the
  plugin from 1.5 – the trade-offs are covered in the architecture
  documentation.
* **“Unanswered” has no count and covers only what is loaded.** The filter
  only applies to pages already loaded; WordPress cannot count this state.
* **The first run reports nothing.** The very first background run only
  records the initial state, so the entire existing backlog doesn't arrive as
  a flood of notifications.
* **No multi-select.** Bulk actions exist only as “Empty spam” and “Empty
  trash”; multi-select is in the backlog.
* **Threads only one level deep.** For a reply, the list shows what it
  directly refers to; the detail view shows the whole thread.
* **No unified inbox.** Multiple blogs are shown one at a time via the
  switcher, not in a combined list.
* **Trash behavior depends on WordPress.** If the trash is disabled in the
  installation, the trash action already deletes permanently. The app cannot
  detect this and explicitly warns about it before deleting permanently.
* **Comment editing is plain HTML.** There is no rich-text editor; you edit
  the rendered HTML text.
* **Instrumentation tests need a device.** Without a device or emulator only
  the JVM tests run, though they cover most of the code. The run deletes the
  app data on the device; a connection set up there has to be re-established
  afterwards.

---

## Icon and trademarks

The app icon is an original drawing: a speech bubble – the app is about
comments – with a W inside, in flat geometry. It uses **no third-party word
mark or logo** and can therefore be published without concern.

![App icon in various masks and sizes](assets/icon-preview.png)

There are two color variants, **switchable in the app settings under
“Appearance”**: Android green and WordPress blue. Behind this is one
`activity-alias` per variant pointing to the same activity, exactly one of
which is enabled at any time – Android has no direct way to change the
launcher icon at runtime. When switching, the launcher recreates the entry; an
icon you placed yourself may need to be placed again.

For a Play Store listing, the icon is also available as a 512 × 512 px PNG
under [`store/play/`](../store/play/) – Google Play requires a different
format there than Android does for the launcher icon. The check against both
specifications is documented in
[`store/play/README.md`](../store/play/README.md).

![Check against the specifications](assets/icon-play-compliance.png)

The source files are in
[`android/app/src/main/res/drawable/`](../android/app/src/main/res/drawable/),
including a monochrome variant for themed icons on Android 13 and later.

“WordPress” is a trademark of the WordPress Foundation. This project is not
affiliated with the WordPress Foundation or Automattic and is neither endorsed
nor reviewed by them.

---

## License

MIT – see [`LICENSE`](../LICENSE).
