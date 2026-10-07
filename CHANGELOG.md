# Changelog

All notable changes to this project are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Version numbers follow [Semantic Versioning](https://semver.org/).

Unreleased states deliberately get no version numbers. Everything unreleased
is listed under `[Unreleased]`.

## [Unreleased]

### Added

**Android app**

- Blogs can be renamed: a "Rename" row at the top of the blog's settings
  opens a dialog. The custom name is used everywhere (title bar, blog
  switcher, notifications) and survives refreshes from WordPress; "Reset"
  restores the name WordPress reports.

- "Empty spam" and "Empty trash" now sit in a labelled bar above the list of
  the Spam and Trash filters, with the number of comments in it ("12 comments
  in spam"), instead of an unlabelled bin icon in the top bar that only
  appeared in those two filters.

- Debug build: redirection of a blog to the local test environment, enabled
  only via the system property `debug.commentator.demo`. For screenshots and
  videos that show a real blog address without touching the real blog. Along
  with it `docker/scripts/seed-screenshots.sh` with made-up comments, and the
  first screenshots plus a demo video under `docs/screenshots/`.

- "Test" button under "Notify instantly": the plugin sends a test wake-up
  call, and if it arrives, a notification confirms it ("Instant notifications
  work"). The row says whether the push server accepted it – if the
  confirmation still doesn't show up, the UnifiedPush app is to blame. On the
  emulator it arrived 0.6 s after the tap. If the admin has disabled instant
  notifications on the blog, the app says so.

- Once instant notifications are set up for every notifying blog, the app
  only checks every six hours (or less often, if configured that way). The
  check remains as a safety net for lost wake-up calls. Only an address
  registered with the plugin counts.
- "Notify instantly" names the server of the push address. For the public
  ntfy.sh, it adds how to switch to your own ntfy server: set it as the
  default server there, then turn instant notifications off and on again.

- Setup of a WordPress connection via the authorization flow for Application
  Passwords, with manual entry as a fallback.
- Comment inbox with author, time, text, associated post and status.
- Filters for All, Pending, Approved, Spam and Trash.
- Paginated loading based on the `X-WP-TotalPages` header.
- Pull-to-refresh and refresh via the top app bar.
- Moderation actions: approve, unapprove, mark as spam, move to trash, delete
  permanently (with confirmation).
- Editing the comment text, where the API allows it for the account.
- Undo for all reversible moderation actions.
- Detail view with full text, author, website, optional email address, date,
  post, status and existing replies. The reply thread is indented and set off
  by a vertical line so replies don't look like standalone comments. It is
  fetched from the server separately on opening and is therefore independent
  of the list filter.
- Replies to comments; the reply is published as a real WordPress comment
  referencing the original.
- "Empty spam" and "Empty trash" in the top app bar, with confirmation before
  permanent deletion. One request with the plugin, one per comment without
  it. With many entries the work is done in batches; the message then says
  how many are left.
- "Block sender" in the detail view: adds the address to WordPress'
  blocklist `disallowed_keys`, so future comments go straight to the trash.
  Appears only if the plugin is present and the account may change site-wide
  options – an editor may moderate but not change options.
- "About this app" screen with version, build number and the commit the build
  was made from, plus a short summary of data processing and licence.
- Blog icon next to its name, in the inbox top app bar and in the settings. It
  comes from the blog's own REST index; no connection to a third party is
  made.
- Text snippets for recurring replies. Created, edited and deleted in the
  settings; above the reply field a bar inserts them with one tap. A snippet
  is appended to the existing text instead of replacing it. The texts are
  stored only on the device.
- Per-app language: the app appears in the system settings under "App
  languages" and can be set to German or English there independently of the
  system locale (Android 13 and later). The list of languages is generated at
  build time from the existing resources so a new translation cannot be
  forgotten.
- Number of comments on each filter, e.g. "Pending · 3". Two requests with
  the plugin, five without, each evaluating only the `X-WP-Total` header. The
  numbers update after every moderation. If a number cannot be determined,
  only the name is shown.
- Author context in the detail view: "Commenting for the first time" or the
  number of previously approved comments from this address. Costs one
  request, of which only the `X-WP-Total` header is evaluated. If it fails,
  the hint is omitted.
- Hints about anomalies in comments: number of links, and texts that appear
  more than once. Purely local from the cache, without an additional request.
  The app only flags and never classifies anything as spam on its own.
- Background check for new comments via WorkManager, with a configurable
  interval.
- Notifications with three separate channels, each of which is actually used:
  new comments, replies to existing comments, and sync notices.
  Deduplication via an identifier derived from the comment ID.
- Deep link `commentator://comment/<instanceId>/<commentId>` from the
  notification straight into the detail view.
- Local Room cache for comments, post titles, counts per filter and sync
  state.
- The team's role badges show up immediately. Team membership is now stored
  as well: previously the assignment only arrived after refreshing, so team
  comments briefly appeared without a badge on startup, and collapsed roles
  only collapsed afterwards. Without a connection the last known state now
  applies instead of nobody counting as team – this also affected the
  background check, which would otherwise have reported precisely the muted
  roles.
- The inbox opens with the last selected filter. Across blogs, like the
  filter itself: it stays put when switching blogs anyway, and stored per
  blog it would jump when switching. It is read before anything is loaded –
  otherwise the list would start at the inbox and switch right after.
- The inbox shows the last known state immediately on opening: the filter bar
  numbers come from the cache before the server has responded, and a filter
  that was empty last time shows the empty state instead of placeholder cards
  for seconds. For this the count per filter is stored too: an empty table
  alone cannot distinguish "there is nothing here" from "nothing has been
  loaded here yet", a recorded 0 can. It still refreshes on every start – the
  stored state only bridges the wait, it does not replace the request.
- Visible distinction between cached and freshly loaded state, including an
  offline banner with the time of the last refresh.
- Settings for notifications, check interval, avatars and display of email
  addresses, with a link to the Android system settings.
- Material 3 with Dynamic Color, light and dark mode following the system
  setting.
- German and English UI.
- Custom app icon as an adaptive icon: a speech bubble with a W inside. Two
  colour variants, switchable in the settings - Android green and WordPress
  blue -, each implemented via an `activity-alias`. With a monochrome variant
  for themed icons on Android 13 and later; the notification icon uses the
  same silhouette. No third-party word or figurative mark is used.

- Play Store icon as a 512 × 512 px 32-bit PNG under `store/play/`, checked
  against the Play Store app icon specifications.
- Notification actions: "Reply", "Approve" and "Spam". Replies are written
  directly in the notification via `RemoteInput`; for a pending comment the
  button reads "Approve and reply". "Approve" appears only on pending
  comments, "Spam" not on spam. The buttons appear only for accounts with
  `moderate_comments`. The non-exported `NotificationActionReceiver` hands
  off to the `NotificationActionWorker` – expedited WorkManager work, at most
  one running action per comment (`ExistingWorkPolicy.KEEP`) – which uses the
  same use cases as the UI. On failure there is deliberately no retry: a
  moderation silently made up later could overwrite a decision taken on the
  web in the meantime. Instead the notification stays and names the reason; a
  failed reply remains in it with its text so nothing written is lost. After
  a direct reply, Android 15 and later keeps the notification
  (`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`) and ignores a plain `cancel()`;
  it is therefore replaced by a short "Done" confirmation that disappears
  after two seconds via `setTimeoutAfter`.
- Notifications clean up after themselves. When a comment is moderated,
  replied to or opened in the app, its notification disappears (interface
  `CommentAlerts`, implemented by `CommentNotifier`). If it was moderated on
  the web, the background check reconciles open notifications before
  reporting anything new – with exactly one request
  (`include=<ids>&status=any`) and only if any notifications are open at all.
  Removed are those that are deleted, spam or in the trash, and those reported
  as pending and approved since. Those already reported as approved stay: they
  may still be waiting for a reply. The notification carries the status at
  the time of reporting in its extras. A summary notification that has become
  empty disappears too.
- "Unanswered" filter between "Pending" and "Approved": approved comments
  from readers with no approved reply from the team or your own account below
  them. The team's own comments don't appear there. WordPress doesn't know
  this state; the app computes it with a Room query over the cache. Fetching
  works as for "Approved", plus one request for the replies to the loaded
  comments (`status=approve&parent=<ids>`). The filter therefore only covers
  the loaded pages and shows no number – the server cannot count it. Modelled
  on the filter of the same name in the WordPress app.
- The blog icon appears as a large image in every comment notification and in
  the summary notification. It comes via Coil from the same image cache as in
  the top app bar and settings (`SiteIconLoader`) and originates from the
  blog itself; no connection to a third party is made.
- Instant notifications via UnifiedPush, switchable per blog in its settings
  under "Notify instantly". The app registers per blog with a UnifiedPush app
  on the phone, such as ntfy, and registers the received address with the
  plugin (`POST /commentator/v1/push`). On disabling, on removing the blog, on
  unregistration by the UnifiedPush app and on replacing the address, it is
  withdrawn there again. Every incoming message triggers a one-off expedited
  check of all blogs (`CommentSyncWorker`, unique as
  "commentator-push-sync"); the message itself is deliberately empty and
  unencrypted. The regular check keeps running and catches lost wake-up
  calls. No Google, no Firebase. The setting states its status: needs plugin
  1.5 or later, needs a UnifiedPush app, being set up, active via the chosen
  app, or the reason for an error. New dependency
  `org.unifiedpush.android:connector` 3.3.5, which brings in Google Tink. This
  replaces the backlog item "Real push via FCM" with a solution without
  Google.

**Fixed after the first run on a device**

- The detail view loaded a commenter's avatar without respecting the "Show
  avatars" setting. This caused a request to Gravatar even though it had been
  declined. The list view was not affected.
- A just-published reply showed "0 min. ago". Under one minute it now shows
  "Just now".
- `seed.sh` attached the test comments to WordPress' default post "Hello
  world!" instead of the posts it had created.
- The background service derived "first run" from a database field that the
  comment source wrote as a side effect - a contract not stated in the
  interface. `NewCommentSource.hasBaseline` now makes it explicit. This
  revealed: if the check ran for the first time while nothing was pending, no
  baseline was created at all, and the first real notification never came.
- The label column of the detail view was 96 dp, about twice as wide as
  needed, and left a gap between label and value.
- The blue icon variant reached only 3.3:1 contrast on the dark background.
  It is now coloured inversely - white bubble on WordPress blue - and reaches
  5.6:1.
- The mark is centred optically instead of geometrically in all icon layers.
  The bubble body takes up almost the entire area, which made the drawing,
  aligned to the frame, appear to have slipped upwards.

**WordPress plugin**

- Version 1.8.2: A wake-up for a new comment on linuxundich.de never reached
  the self-hosted ntfy, and nothing showed why. WordPress' "non-blocking"
  requests still wait for the connection and give up silently at the timeout.
  Wake-ups are now collected during the request and sent at shutdown, with a
  proper response (timeout 5 s); under PHP-FPM the visitor's response goes out
  first. The result of the last wake-up - time, server, HTTP status or error,
  duration - is stored and shown in the profile section.
- Version 1.8.1: Push servers reachable only over IPv6 were rejected as
  invalid. WordPress' own URL check (`wp_http_validate_url`, also used by
  `wp_safe_remote_post`) resolves host names over IPv4 only. The plugin now
  resolves A and AAAA records itself and still accepts only public addresses
  on HTTPS port 443; the check is repeated before every send. Found while
  setting up a self-hosted ntfy behind carrier-grade NAT.
- Version 1.8.0: English user interface with a German translation. All texts
  the plugin shows - the profile section, error messages of its REST routes,
  the plugin description - are now English in the code and translatable
  (text domain `commentator-bridge`, `languages/`). The bundled German
  translation is used automatically when the site or the user's profile is
  set to German.
- Version 1.7.0: `POST commentator/v1/push/test` sends a test wake-up call to
  your own addresses and reports how many the push server accepted. The user
  profile shows the registered addresses (server and end of the topic, not
  the full address) in the "Commentator: instant notifications" section, with
  "Remove" and "Send test wake-up". Wake-up calls for status changes go to
  an address at most every 30 seconds; new comments always wake. For admins
  without a settings page: `COMMENTATOR_BRIDGE_DISABLE_PUSH` disables instant
  notifications, the filters `commentator_bridge_push_allow_local` and
  `commentator_bridge_push_endpoint_allowed` allow push servers on the local
  network or restrict addresses, respectively.
- Version 1.6.0: a status change (`transition_comment_status`) also wakes the
  app, with the body "status" and `Urgency: normal`. A moderation done in the
  backend thus withdraws the notification immediately instead of at the next
  regular check; measured on the emulator at 0.6 s. Changes made from the app
  itself don't wake it – it has already withdrawn the notification; this is
  detected by its user agent `Commentator/`.
- Version 1.5.0: `commentator/v1/push` accepts push addresses (`POST`) and
  releases them (`DELETE`). On every new comment (`wp_insert_comment`) a
  non-blocking wake-up call with the body "new" – no name, no text, no
  identifier – goes to every registered address of all accounts with
  `moderate_comments`, with the headers `TTL: 3600` and `Urgency: high`.
  Comments arriving as spam or in the trash wake no one. Only public HTTPS
  addresses are accepted (`wp_http_validate_url`, sent via
  `wp_safe_remote_post`), at most five per account; the oldest drops out.
  They are stored in the user meta `commentator_push_endpoints`. Without a
  registered address the plugin sends nothing outbound.
- `commentator-bridge` with the read endpoints `commentator/v1/status` and
  `commentator/v1/summary`, both protected by `moderate_comments`.

**Development and testing**

- Room exports its schema to `android/app/schemas/` so later migrations can be
  verified automatically.
- `DebugSyncReceiver` triggers the background check immediately via `adb`.
  WorkManager does not bring periodic work forward; without the receiver,
  testing meant waiting up to 15 minutes. It exists only in the debug build
  and is protected by `android.permission.DUMP`.
- README: setting up an emulator without Android Studio, running the test
  environment with Podman instead of Docker, and access from the emulator via
  `adb reverse`.

- Local WordPress test environment via Docker Compose, including TLS proxy,
  certificate script and a script for reproducible test data.
- Unit tests for status logic, error mapping, DTO mapping, use cases and
  ViewModels.
- Network tests against MockWebServer for query parameters, pagination,
  authentication header and complete error handling.
- Documentation: architecture decisions, API endpoints used, data
  processing.

### Fixed

**Android app**

- A reply published a moment ago could read "In 0 min." when the phone's
  clock ran a few seconds behind the blog's. Times up to five minutes in the
  future now count as "Just now".

- Comments deleted or reclassified on the web stayed in the cache if they were
  older than the loaded first page. They then kept appearing under
  "Unanswered", and "Text appears more than once" counted them. Now the app
  cross-checks such entries with one request after refreshing, at most every
  ten minutes per blog and filter.

- A wake-up call while the app process was stopped had no effect: processing
  was tied to the lifecycle of the push service, which the UnifiedPush
  connector stops immediately after delivery. Now the check is scheduled
  directly, and the test wake-up confirmation runs independently of the
  service.

- A reply to a pending comment remained invisible on the website: the parent
  comment stayed "pending", and WordPress doesn't show replies below it –
  verified against a local WordPress installation. The app now approves a
  pending comment first and then replies, like the WordPress backend. In this
  case the button in the detail view reads "Approve and reply". The order is
  intentional: if approval fails, nothing has happened yet, and a second
  attempt creates no duplicate reply.
- The check interval selection sat in a single-line row. The fifth option got
  only 39 dp of width, its text wrapped vertically and tore a tall empty area
  into the settings; it was no longer usable either. The chips now wrap.
- A `commentator-bridge` plugin installed later was never noticed. Bridge
  detection, moderation permission and blog name came exclusively from the
  moment of sign-in and were never read again. They are now re-evaluated when
  refreshing the inbox and when opening the settings.

**WordPress plugin**

- Version 1.4.0: `commentator/v1/team` lists the blog's roles and their
  members. Without this endpoint the app cannot detect the team –
  `wp/v2/users` with `context=edit` requires `list_users`, which an editor
  doesn't have. Only roles that may write posts or moderate are returned;
  subscribers are not included.
- Version 1.3.0: `commentator/v1/status` additionally reports the newest
  comment regardless of status. Without this field the app could not take the
  shortcut on blogs with automatic approval. Older versions remain usable; the
  app then queries the core API as usual.
- The newest comment is determined by ID instead of date. The app compares
  IDs; a backdated comment – not unusual with imports – would otherwise have
  been the "newest" and wrongly made the check stop early.
- Two new endpoints in version 1.2.0: `/empty` empties spam or trash in
  batches, `/blocklist` maintains `disallowed_keys`. Both only do what is
  possible in the backend anyway and check the same permissions – `/empty`
  `moderate_comments`, `/blocklist` `manage_options`.
- `commentator/v1/summary` reported the number `total_comments` under `all`,
  which counts spam. With `status=all`, however, the REST API lists only
  approved and pending comments – so the number didn't match the list it
  describes. Now approved plus pending. Plugin version 1.1.0.

### Changed

**Android app**

- Settings screens: consistent spacing from one place in the code. Everything
  starts at the same 16 dp edge, including the labels of text buttons, rows
  have even vertical spacing, and "Test" lines up with the switch above it.
  "Notify about" is a radio list with a one-line explanation per option
  instead of three chips with a paragraph below.

- Role badge and status indicator sit side by side on one line, with the same
  height and shape. Previously they were offset one above the other and had
  different heights.
- The role badge now sits below the name instead of next to it. Next to badge
  and status indicator barely 80 dp were left for the name – "Christoph
  Langner" became "Christop…".

- The project's first database migration: the comment table gains the
  author's user ID. Deliberately a migration rather than a rebuild – with the
  cache the notification baseline would be lost, and the next run would bring
  a flood covering all existing comments.

- Comments from your own team are visually set apart, distinguished by role.
  Each role has its own tint from a coordinated palette – all deliberately
  pale so that a comment from your own editorial team doesn't look like a
  warning. The badge names the role, so the distinction doesn't rely on colour
  alone. Which roles count as team can be chosen in the settings; the
  defaults are Administrator and Editor. Your own account always counts.
- A settings dialog per role: whether it counts as team, which tint it gets
  and whether it is tinted at all, whether its comments appear individually
  in the list and whether it notifies on the device. The four default roles –
  Administrator, Editor, Author, Contributor – are available even without the
  bridge plugin.
- Roles that should not appear individually in the list are collapsed there
  instead of hidden: consecutive comments appear as one row that states count
  and roles and can be tapped. That the team commented thus stays visible –
  disappearing entirely would be worse than any filtering, you wouldn't even
  know something was missing. The comment a reply refers to always stays
  visible; collapsed, it would lack exactly the context it was loaded for.
- Notifications can be turned off per role. Muted comments are still
  recorded, otherwise the background service would fetch them from the blog
  again on every run.
- By default the app does not notify about your own comments: whoever just
  replied knows about it, and the notification would only come with the next
  background check. This can be turned on in the "Your own account" dialog.
  Your own comments are always governed by this entry, even if the plugin
  knows your actual role – otherwise the switch would be ineffective precisely
  there, and your own replies could only be muted together with those of all
  other administrators.
- Swipe gestures in the list: right to approve, left to mark as spam. Both
  are reversible, and the buttons on the card remain – a gesture is never the
  only way to an action. Permanent deletion is deliberately not included.
  From the first movement, the area under the card shows in colour what the
  gesture would trigger. Icon and label fade in as soon as they fit fully
  beside the card – a clipped icon or a word cut off mid-word says less than
  none at all. Once dragged far enough for release to trigger the action,
  there is a short haptic pulse and the indicator grows slightly.
- Blog icon and blog name now sit on one line. Previously it was not the text
  that was centred but its box – which includes line spacing and room for
  descenders that a blog name often doesn't have. Measured on the device: an
  offset of 2 px became 1 px.
- The blog icon is shown even if the blog has no WordPress site icon set.
  Many blogs ship their icon with the theme and only add it to the page head
  as `<link rel="icon">` – it is then invisible to the REST API, even though
  it shows up everywhere in the browser. The search happens only if no site
  icon is set, and only an image on the same host as the blog is used: an
  icon from a content delivery network would be a connection to a third
  party. `.ico` and `.svg` are excluded because Android cannot draw them.
- The inbox list is shown as a conversation thread: replies appear indented
  below the comment they refer to, and threads with new comments come first.
  Since with the "Pending" filter the preceding comment has usually long been
  approved and wouldn't appear there at all, it is fetched separately and
  shown dimmed as context – without buttons, because it doesn't belong to the
  filter. Can be turned off in the settings; the list then stays purely
  chronological. Search results always remain flat.
- Comment search via the top app bar. The search runs on the server via the
  WordPress API's `search` parameter and within the selected filter – so the
  filter bar keeps its meaning. Searching locally would be faster but would
  only search what happens to be in the cache. The search starts only once
  the input settles, and from two characters; otherwise every keystroke would
  be a request of its own. Results come from the cache like the list, so a
  moderation takes effect in the results immediately too.
- Multiple blogs in one app. The data layer was designed for this from the
  start – every row carries an `instanceId`, credentials are stored per blog,
  the HTTP client is created per blog –, so the extension needed no data
  migration.
  - Switcher in the inbox top app bar. It appears only from the second blog
    on; with one, an arrow above a single-entry list would be a promise
    without substance. The sheet shows each blog's icon, name and address –
    two blogs can share a name, the address always tells them apart – and the
    number of pending comments from the cache. A blog that has never been
    loaded shows no number rather than a wrong zero.
  - List of all configured blogs in the settings, with a way to add more. The
    same setup as for the first blog, just with a back button – behind the
    first there is nothing.
  - Separate settings screen per blog: address, account, plugin detection,
    whether the blog notifies and via what, its roles and colours, its text
    snippets, and removal of exactly this blog. Everything together on one
    screen would have left open, for every row, which blog it applies to.
  - Notifications can be turned off per blog, with their own scope. A side
    project may stay quiet while the main blog notifies. The main switch and
    the check interval still apply to all.
  - The background check goes through all blogs in one pass instead of
    scheduling separate work per blog: the device wakes once instead of n
    times. Errors are handled per blog – a blog with rejected credentials or
    no connection doesn't hold up the notifications of the others.
  - Roles, colours and text snippets apply per blog: the roles of an
    editorial team are not those of a client project, and neither is the
    tone. What was configured across blogs before the switch still applies –
    the old keys are read, and from then on writes are per blog.
  - Signing in again to an already configured blog updates its entry instead
    of creating a second one. A second entry would not only appear twice in
    the list, it would also report every existing comment as new once more.
  - Notifications carry the blog as a tag alongside the identifier derived
    from the comment ID. IDs are only unique within a blog; without this
    second dimension, comment 5 of one blog would have replaced the
    notification for comment 5 of the other. With multiple blogs the subtext
    also names the blog; with one it stays as it was.
  - Tapping a notification switches to the blog concerned. Otherwise the
    opened comment would sit on top of another blog's inbox.
- The UI follows Material 3 Expressive.
  - **Motion** as spring physics instead of fixed durations. A spring knows
    the velocity with which a movement arrives and continues from it; a curve
    over 200 ms always starts at zero, even if the finger was just pushing
    the element. Split into spatial (position and size, overshoots slightly)
    and effects (opacity and colour, doesn't overshoot - an overshooting
    opacity would have to exceed 100 % and would only flicker). The scheme
    comes from the theme, not from constants in the components, so no place
    sticks to its own timing.
  - **Filter bar as a connected group** of `ToggleButton`s: round on the
    outside, almost straight on the inside, and thus recognisable as one
    thing instead of five separate chips. Each toggle has three shapes -
    resting, pressed, selected - and morphs between them instead of swapping
    them. The selection thus carries its shape and not just its colour;
    someone who has trouble telling colours apart sees nothing in a tinted
    chip, but does in a round one.
  - **Shape scale** with a wider range: restrained where many elements sit
    next to each other, round where a surface stands on its own. The comment
    list deliberately stays dense - it is the workspace, and every millimetre
    of radius costs usable width there.
  - **Emphasised typography**: more weight on titles, headings and labels so
    the structure is recognisable when skimming. The comment text itself is
    left untouched - it is read, not skimmed.
  - **Loading indicator** as a sequence of morphing shapes instead of a
    spinning circle.
  - Expanding and collapsing grouped team comments is animated; it previously
    switched without transition, and you couldn't see where the new cards
    came from. The arrow rotates instead of being swapped for a second
    glyph.
  - For this, `compose-material3` is the only Compose artifact pinned to its
    own version (1.5.0-alpha29) instead of the BOM. In the stable 1.4.0 line
    the entire Expressive API is Kotlin-`internal` and not callable from app
    code; the new components are missing there entirely. Rationale and cost
    are in `docs/architecture.md`.
- Label columns adapt to the measured text width instead of a fixed value. At
  200 % system font size, "Post" previously became "Po / st" – a break in
  the middle of the word. A test pins this down for the detail view.
- Label and value are read out together by screen readers ("Post Hello
  world!") instead of as two stops, the first of which means nothing on its
  own.
- The filter chips announce the number spelled out to screen readers ("All,
  7 comments"); on screen it still reads "All · 7". The middle dot separates
  for the eye; read aloud it means nothing.
- The blog row in the settings shows the label above the value, like the rows
  below it.
- Addresses that appear unlinked in the text now count as links too.
  Previously only the markup was considered – but WordPress doesn't link
  every address, so a comment with a spelled-out URL went unnoticed. A linked
  address still counts only once.
- When the list was first built, two loading indicators ran at the same time:
  the pull-to-refresh one and the list's. Instead of a circle in empty space
  there are now placeholder cards shaped like the later content; the list no
  longer jumps when the data arrives.
- The actions on a comment card were four labelled buttons on two rows. Now a
  highlighted primary action carries the label – approve, or reply for
  already approved comments –, the others sit next to it as icons. The same
  screen thus fits three comments instead of two and a half.
- The inbox top app bar moves away when scrolling up and comes back
  immediately when scrolling back.
- The status bar icon filled only about half of its area and appeared as a
  barely recognisable dot next to other apps' icons. It now fills a good 90
  percent, as intended for status bar icons, and shows the same bubble shape
  as the app icon.
- App icon reworked along the lines of common messengers: an almost square
  bubble body with a large corner radius instead of an elongated one, and a
  short attached tail instead of a thin spike. The mark now keeps about 5
  units of distance from the safe zone instead of 1.8.

- Notifications can be configured in three levels: only what awaits
  moderation, every new comment (default), or additionally spam and trash.
  Previously only the moderation queue was checked – on blogs that approve
  comments automatically, a notification therefore never arrived. What a spam
  filter has sorted out stays excluded by default; the third level makes
  misclassifications visible.
- Commenters without a Gravatar get a custom placeholder icon instead of
  Gravatar's silhouette. For this the app requests `d=404` instead of
  `d=mm`. Avatar URLs of other services are left untouched.
- Switching between filters no longer costs network requests as long as the
  state is younger than two minutes. Previously every tap on a filter
  triggered up to nine requests – list, post titles, permission check and
  counts – even though none of that can change from merely switching.
  Measured: previously 7 to 9 requests per switch, now 2 on the first visit
  to a filter and 0 on every subsequent one. An explicit refresh still
  fetches everything.
- Images are cached on disk. Blog icon and avatars were previously reloaded
  after every restart.
- Version and build number come from Git instead of hard-coded values: the
  build number from the commit count, the version name from the latest tag
  or from the base version and commit. Without Git, fallback values apply so
  a build from a source archive doesn't fail.

### Security

- Credentials are stored encrypted with AES-256-GCM; the key lives in the
  Android Keystore and never leaves it.
- `androidx.security:security-crypto` is deliberately not used: the library
  has been fully deprecated since 1.1.0-beta01 (June 2025).
- Cleartext traffic is blocked at three levels: network security config,
  manifest and a custom interceptor.
- HTTP logging exists only in debug builds and redacts the `Authorization`
  header there; in release builds the library is not part of the app.
- Credential types don't reveal their value via `toString()`, which is
  covered by unit tests.
- `FLAG_SECURE` in the step for manually entering the Application Password,
  encapsulated in `ScreenshotProtection` and covered by tests - including
  that the flag is removed again on leaving.
- `allowBackup="false"` and empty extraction rules prevent credentials from
  ending up in cloud backups or on a new device.
- The Application Password is not passed on as a navigation argument, so it
  doesn't end up in the back stack or in saved state.
- The app itself requests three permissions. Three more are contributed by
  `androidx.work` during manifest merging; all six are listed individually in
  `docs/privacy.md`.
- The signing configuration for release builds reads only Gradle properties
  from outside the project. Neither keystore nor passwords are in the
  repository, and without them an unsigned release is produced instead of a
  build error.
- Backup rules also apply on Android 11 and older (`fullBackupContent`), not
  only via `dataExtractionRules` on Android 12 and later.
- The runtime permission for notifications is only requested where it exists
  (Android 13 and later). An unchecked request on older versions would have
  suppressed notifications there completely.

### Notes

- No analytics, no crash reports, no telemetry, no ads.
- No Firebase or Google Play Services dependency. Detection of new comments
  sits behind the `NewCommentSource` interface. The optional instant
  notifications via UnifiedPush only trigger this check earlier and therefore
  required no change to the notification logic.
