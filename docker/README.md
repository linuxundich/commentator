# Local WordPress test environment

Reproducible environment for development and integration tests. It is
completely separate from any production blog.

## Requirements

* Docker with the Compose plugin (`docker compose version`)
* `openssl` for the test certificate

## Start

```bash
cd docker
./scripts/generate-cert.sh     # once: self-signed certificate
docker compose up -d
./scripts/seed.sh              # installation and test data
```

At the end, `seed.sh` prints an Application Password. With it and the blog
address shown, the app can be set up.

## Stop

```bash
docker compose down            # stop containers, keep data
docker compose down -v         # stop containers and discard all data
```

## Why HTTPS even locally?

WordPress only offers Application Passwords over secure connections, and the
app never allows cleartext traffic. That is why an nginx with a self-signed
certificate on port 8443 sits in front of WordPress.

For an Android device to trust this connection, `certs/server.crt` must be
installed there as a user certificate. Debug builds of Commentator trust user
certificates, release builds explicitly do not.

## Access depending on the environment

| Access from | Address |
|---|---|
| Development machine | `https://localhost:8443` |
| Android emulator | `https://localhost:8443` after `adb reverse tcp:8443 tcp:8443`, otherwise `https://10.0.2.2:8443` |
| Real device on the LAN | `https://<IP of the machine>:8443` |

Easiest for the emulator: `adb reverse tcp:8443 tcp:8443` forwards the
emulator's port to the machine. Then `https://localhost:8443` works
everywhere, and WordPress does not need an address of its own.

Without forwarding, and for a real device, the address has to be set before
the first start so that WordPress takes it into its configuration:

```bash
WP_SITE_URL=https://10.0.2.2:8443 docker compose up -d
WP_SITE_URL=https://10.0.2.2:8443 ./scripts/seed.sh
```

For a real device, also add your own IP to the certificate:

```bash
EXTRA_SAN=IP:192.168.1.42 ./scripts/generate-cert.sh
```

## Test data

`seed.sh` creates:

* three posts
* three pending comments
* one approved comment
* one spam comment
* one comment in the trash
* a user `moderator` with the Editor role (has `moderate_comments`)

The comment texts and timestamps are fixed so that tests are repeatable.
Running the script multiple times does not create duplicates.

## Screenshots and demo video

The images and the video in `docs/screenshots/` are produced in the emulator
against this environment, with made-up comments under the name and logo of
linuxundich.de:

```bash
WP_SITE_URL=https://linuxundich.de docker compose up -d
docker compose restart proxy          # after recreating WordPress
./scripts/seed.sh
./scripts/seed-screenshots.sh

adb reverse tcp:8443 tcp:8443
adb shell setprop debug.commentator.demo linuxundich.de=localhost:8443
```

In the **debug build**, the system property turns on a redirect: the app
shows `linuxundich.de` but talks to this environment. It does not exist in
the release build. The app loads the logo from the real blog; a must-use
plugin (`screenshots/demo-site-icon.php`) supplies its address as the site
icon for this.

`./scripts/seed-screenshots.sh --notify` then creates another pending comment
– for the notification, after the app has recorded its initial state.

Gravatars only appear for addresses that actually have one. The app requests
them with `d=404` and otherwise shows its own placeholder; the made-up readers
therefore get none, while the team account shows the operator's.

SystemUI's demo mode provides a tidy status bar:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
```
