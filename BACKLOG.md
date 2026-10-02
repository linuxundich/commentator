# Backlog

Priorities:

| | |
|---|---|
| **P0** | required for the MVP |
| **P1** | important, shortly after the MVP |
| **P2** | later |
| **P3** | optional, only if convenient |

Completed items stay checked so the path remains traceable.

---

## MVP

- [x] **P0** Establish and verify the WordPress connection (`/wp-json/`)
- [x] **P0** Authentication via Application Passwords
- [x] **P0** Authorization flow in the browser with return via deep link
- [x] **P0** Manual entry of an Application Password as a fallback
- [x] **P0** Encrypted storage of credentials via the Android Keystore
- [x] **P0** Fetch comments (`context=edit`)
- [x] **P0** Comment overview with author, time, text, post, status
- [x] **P0** Comment filters: All, Pending, Approved, Spam, Trash
- [x] **P0** Pagination via `X-WP-TotalPages`
- [x] **P0** Pull-to-refresh
- [x] **P0** Approve comment
- [x] **P0** Unapprove comment
- [x] **P0** Mark comment as spam
- [x] **P0** Move comment to trash
- [x] **P0** Delete comment permanently, with confirmation
- [x] **P0** Write and publish replies
- [x] **P0** Detail view
- [x] **P0** Error handling for all required cases
- [x] **P0** Undo for moderation actions
- [x] **P1** Edit comment text

---

## Notifications

- [x] **P0** Detection of new comments in the background (WorkManager)
- [x] **P0** Notification for new comments
- [x] **P0** Deep link from the notification into the detail view
- [x] **P0** Separate notification channels
- [x] **P0** No duplicate notification for the same comment
- [x] **P0** Runtime permission `POST_NOTIFICATIONS` on Android 13 and later
- [x] **P1** Configurable check interval
- [x] **P1** Link to the Android system settings
- [x] **P1** Persistent notice for rejected credentials

### Open

- [x] **P2** Moderation actions directly from the notification
      *Technical question: an action from the notification needs a
      BroadcastReceiver that writes to the API without a visible app. It
      remains to be clarified how errors are surfaced there – a silently
      failed moderation would be worse than no quick action at all.*
      *Answered: the receiver only hands off to expedited WorkManager work
      that uses the same use cases as the UI. If it fails, there is no silent
      retry – a moderation made up later could overwrite a decision taken on
      the web in the meantime. The notification stays and names the reason; a
      failed reply remains in it with its text.*
- [x] **P2** Real push via FCM as an additional `NewCommentSource`
      *Technical question: a delivery path from WordPress to FCM requires a
      service account key on the WordPress server and device registration in
      the plugin. This would create metadata at Google and a dependency on
      Play Services. The trade-off is described in `docs/architecture.md`;
      the interface is prepared.*
      *Replaced: implemented as instant notifications via UnifiedPush,
      without Google; FCM is dropped. The plugin (1.5 and later) sends an
      empty wake-up call to the registered address, and the app then checks
      immediately. A separate `NewCommentSource` was not needed.*
- [x] **P2** Notifications clean up after themselves
      *Moderated, replied to or opened in the app: the notification
      disappears immediately. Moderated on the web: the background check
      reconciles open notifications with one request (`include`,
      `status=any`). What was reported as approved stays – it may still be
      waiting for a reply.*
- [x] **P2** Blog icon in notifications
      *From the same image cache as in the top app bar; it originates from
      the blog itself, no connection to third parties is made.*
- [x] **P3** Notifications for automatically approved comments too
      *Implemented and made the default: on blogs that approve automatically,
      a notification never arrived before. A switch restricts notifications
      to the moderation queue again.*

---

## UX

- [x] **P0** Dark mode and light mode following the system setting
- [x] **P1** Dynamic Color on Android 12 and later
- [x] **P0** Loading states
- [x] **P0** Empty states with an explanation instead of a blank area
- [x] **P0** Error states with a retry option
- [x] **P0** Visible distinction between cached and fresh state
- [x] **P1** Status indication with icon and text, not just colour
- [x] **P1** German and English UI

### Open

- [x] **P1** Pass with TalkBack and correction of the reading order
      *The reading order was fine: a dump with `uiautomator` initially
      suggested the top app bar came last – but it only shows the tree
      structure. Compose communicates the order via `traversalBefore`, and
      nothing was set there: the screen reader then sorts by position on
      screen itself, and the top app bar is at the top. A hastily added
      `traversalIndex` correction was therefore removed again. Two real
      findings remained: label and value were read out separately, and the
      filter chips read out the middle dot in "Pending · 3". A test now
      checks that every interactive element has a name.*
- [x] **P1** Check with very large system font size
      *Gone through at 200 % in the emulator. One finding: the label column
      of the detail view was fixed at 64 dp and broke "Post" mid-word. Widths
      are now measured. List, settings and "About this app" held up.*
- [x] **P2** Swipe gestures for approve and spam in the list
      *The action is tied to the completed swipe, not to
      `confirmValueChange` – that is called several times during a gesture
      and triggered the moderation twice.*
- [ ] **P2** Adaptation to large screens (list and detail side by side)
- [ ] **P3** Haptic feedback for moderation actions

---

## Multi-blog

The data layer was designed for this from the start: every row carries an
`instanceId`, credentials are stored per instance, and the HTTP client is
created per instance. The UI has caught up.

- [x] **P2** List of configured instances and adding more
- [x] **P2** Switcher between blogs in the top app bar
      *Appears only from the second blog on. Adding is therefore also
      available in the settings, so it stays reachable with only one blog.*
- [x] **P2** Separate credentials per instance visible in the settings
      *As a separate screen per blog. Together with the blog-independent
      settings on one screen, it would have been unclear for every row which
      blog it applies to.*
- [x] **P2** Notifications controllable separately per blog
      *Decided on a single work item that goes through all instances: the
      device wakes once instead of n times. Error handling is done manually
      per instance instead – a blog with rejected credentials or no
      connection doesn't hold up the others, and the pass is retried only
      after all have been processed.*

### Open

- [ ] **P3** Combined inbox across all blogs
      *Technical question: sorting across instance boundaries requires a
      shared query; per-instance pagination and global sorting don't go
      together easily.*
- [ ] **P3** Number of pending comments in the switcher also for blogs that
      have never been loaded
      *The number currently comes from the cache; an unloaded blog therefore
      shows none. Fetching it from the server would mean one request per blog
      when opening the switcher – acceptable with the plugin via the status
      endpoint, not without it.*
- [ ] **P3** Reorder blogs in the list
      *Currently they appear in the order they were set up. From about four
      blogs on, sorting by importance will probably be noticeably missed.*

---

## Offline

- [x] **P0** Local cache for comments and post titles
- [x] **P0** Understandable notice instead of a network error
- [x] **P0** Write actions blocked and explained without a connection

### Open

- [ ] **P2** Queue for moderation actions without a connection
      *Technical question: what happens if a comment was moderated or deleted
      on the web in the meantime? Without a conflict strategy the app
      overwrites others' decisions. One option is to re-read the status on
      sending and ask on a mismatch instead of overwriting. Only with this
      strategy does the feature make sense.*
- [x] **P2** Comments deleted on the web don't disappear from the cache
      *Found while recording for the README (2026-10-02), fixed: after
      loading the first page, the app checks the older cached entries of the
      same filter with one request (`include`, `status=any`, up to 100).
      Whatever the blog no longer knows is removed, the rest takes on its
      current status. At most every ten minutes per blog and filter.*
- [ ] **P3** Cache limit and cleanup of old comments

---

## Moderation aids

Everything here serves one question: how do I reach a well-founded decision
faster? The items were checked against a real WordPress installation, and the
requests are verified.

The original items of this section are implemented. "Empty spam" and blocking
senders use two endpoints that the plugin ships from 1.2.0 on; emptying also
works without the plugin, then with one request per comment. The open items
at the end were added later.

- [x] **P1** Author context in the detail view
      *Shows whether someone is commenting for the first time or is already
      known - the question that comes first with almost every pending
      comment. The core API is sufficient for this, no plugin needed:*
      `GET /wp/v2/comments?author_email=<adresse>&status=approve&per_page=1`
      *The number is in `X-WP-Total`, the body isn't needed. Tapping the hint
      could show the previous comments.*
      *Privacy: the app fetches the address anyway with `context=edit`. The
      bare count is less sensitive than the address itself and should
      therefore be visible regardless of the "Show email addresses"
      setting.*
- [x] **P1** Flag spam signals without judging
      *Purely local from the already cached HTML, without additional
      requests: number of links in the comment (the best single heuristic),
      first-time commenter, text identical to another pending comment. A hint
      like "3 links" is enough - the app should flag, not decide.*
- [x] **P2** Block senders permanently
      *Add an address or domain to WordPress' `disallowed_keys` instead of
      marking the same sender as spam again every day.*
      *Needs the plugin: `wp/v2/settings` doesn't expose the moderation
      options - checked, the response contains not a single field on comment
      moderation or blocklists.*
- [x] **P2** "Empty spam" and "Empty trash"
      *The common bulk case doesn't need multi-select but a button. The core
      API has no bulk endpoint, it would be N individual deletions; an
      endpoint in the plugin turns that into one request. A sensible reason
      to use the plugin for something it really does better than the core
      API.*
- [x] **P2** Text snippets for recurring replies
      *Not an API topic, purely local storage. Otherwise you type "Thanks for
      pointing that out, it's fixed." for the twentieth time.*
- [x] **P2** "Approve and reply"
      *Checked against a local WordPress: without approval the parent comment
      stayed pending, and the reply was invisible on the website. Approve
      first, then reply – if approval fails, nothing has happened, and a
      second attempt creates no duplicate reply.*

### Open

- [ ] **P2** Keep reply drafts
- [ ] **P2** Go to the next pending comment after an action
- [ ] **P3** When blocking, mark all previous comments from the sender as
      spam

---

## Search and filters

- [x] **P1** Comment search via the API's `search` parameter
      *Works within the selected filter, debounced and from two characters.
      The results go into the cache and are observed from there – so a
      moderation takes effect in the results immediately too, without a
      second path for list states.*
- [x] **P2** "Unanswered" filter
      *Approved reader comments without an approved reply from the team.
      WordPress doesn't know this state; the app computes it from the cache
      and additionally fetches the replies to the loaded comments
      (`parent=<ids>`). Hence no number on the chip and only over the loaded
      pages. Modelled on the WordPress app.*
- [ ] **P2** Filter by post
- [ ] **P2** Filter by author
- [ ] **P3** Local full-text search in the cache via Room FTS
      *Technical question: a local search only finds what is cached. It
      remains to be clarified how the difference from the server search is
      made visible, so nobody mistakes an incomplete result for a complete
      one.*

---

## Other

- [x] **P2** Comment thread instead of a purely chronological list
      *The structure is a pure function over `parentId`; the repository
      fetches the comments that were replied to with one request (`include`,
      `status=any`) – without them the thread would almost always stay empty
      in the "Pending" filter. Only one level deep: the immediate context.
      Whoever wants to see the whole thread opens the comment.*
- [ ] **P2** Bulk moderation with multi-select
- [x] **P1** Show comment counts per filter - "Pending · 3" instead of
      "Pending"
      *Contrary to what was originally noted here, this doesn't depend on the
      plugin: four requests with `per_page=1` deliver all numbers from
      `X-WP-Total`, without significant data volume. The plugin's `summary`
      endpoint turns this into one request instead of four - an improvement,
      not a prerequisite.*
- [ ] **P3** Statistics on comment volume and spam share
- [ ] **P3** Open comment in the browser
- [ ] **P3** Widget with the number of pending comments

---

## Technical debt and maintenance

- [ ] **P1** Raise Kotlin to 2.4 as soon as KSP supports it
      *Checked on 2026-09-21: Kotlin is at 2.4.20, KSP at 2.3.12 – and its
      POM depends on `kotlin-stdlib` 2.3.20. So there is still no official
      pairing for the 2.4 line. Room and Hilt need KSP.*
      *Switched over on a trial basis anyway: the build succeeds, KSP
      generates Hilt and Room code, 267 unit tests, 8 device tests and the
      release build are green. The switch was nevertheless reverted – KSP
      would analyse the sources with a compiler from the 2.3 line while
      compilation uses 2.4. Today this goes unnoticed; a language feature
      from 2.4 in an annotated class could silently go wrong. Once KSP
      catches up, it is one line in `libs.versions.toml`.*
- [x] **P1** Run instrumentation tests on a device or emulator
      *Run on a Pixel 10 emulator (API 37) and on a device.*
- [x] **P2** Prepare Room migrations as soon as the schema changes
      *The first one came with the author's user ID. Schema 2 is exported,
      the migration is registered and verified against the schema on the
      device – the exported JSON files are provided as assets of the
      instrumentation tests for this, not in the APK.*
- [ ] **P2** Replace `material-icons-core` once a maintained successor
      exists
      *The package is frozen at 1.7.8.*
- [ ] **P2** Check whether `android.newDsl` and the Variant API migration for
      AGP 10 require changes
- [ ] **P3** Keep an eye on new APIs in API 37
      *Reviewed on 2026-09-21: the additions concern almost exclusively areas
      unrelated to this app (App Functions, Health Connect, HPKE, Ranging,
      Photo Picker, serial ports). At most
      `Notification.createSemanticStyleAnnotation` and the handoff APIs in
      `Activity` would be obvious candidates; neither brings any discernible
      benefit here at present.*
- [ ] **P3** Signing configuration and reproducible release build
- [ ] **P3** Continuous integration for build, lint and tests
