# Lokale WordPress-Testumgebung

Reproduzierbare Umgebung für Entwicklung und Integrationstests. Sie ist
vollständig getrennt von jedem produktiven Blog.

## Voraussetzungen

* Docker mit Compose-Plugin (`docker compose version`)
* `openssl` für das Testzertifikat

## Start

```bash
cd docker
./scripts/generate-cert.sh     # einmalig: selbstsigniertes Zertifikat
docker compose up -d
./scripts/seed.sh              # Installation und Testdaten
```

Am Ende gibt `seed.sh` ein Application Password aus. Damit und mit der
angezeigten Blog-Adresse lässt sich die App einrichten.

## Beenden

```bash
docker compose down            # Container stoppen, Daten behalten
docker compose down -v         # Container stoppen und alle Daten verwerfen
```

## Warum HTTPS auch lokal?

WordPress bietet Application Passwords nur über gesicherte Verbindungen an,
und die App lässt Klartextverkehr grundsätzlich nicht zu. Deshalb steht vor
WordPress ein nginx mit selbstsigniertem Zertifikat auf Port 8443.

Damit ein Android-Gerät dieser Verbindung traut, muss `certs/server.crt` dort
als Benutzerzertifikat installiert werden. Debug-Builds von Commentator
vertrauen Benutzerzertifikaten, Release-Builds ausdrücklich nicht.

## Zugriff je nach Umgebung

| Zugriff von | Adresse |
|---|---|
| Entwicklungsrechner | `https://localhost:8443` |
| Android-Emulator | `https://localhost:8443` nach `adb reverse tcp:8443 tcp:8443`, sonst `https://10.0.2.2:8443` |
| Echtes Gerät im LAN | `https://<IP des Rechners>:8443` |

Am einfachsten für den Emulator: `adb reverse tcp:8443 tcp:8443` leitet den
Port des Emulators auf den Rechner um. Dann gilt `https://localhost:8443`
überall, und WordPress braucht keine eigene Adresse.

Ohne Umleitung, und für ein echtes Gerät, muss die Adresse vor dem ersten
Start gesetzt werden, damit WordPress sie in seine Konfiguration übernimmt:

```bash
WP_SITE_URL=https://10.0.2.2:8443 docker compose up -d
WP_SITE_URL=https://10.0.2.2:8443 ./scripts/seed.sh
```

Für ein echtes Gerät zusätzlich die eigene IP ins Zertifikat aufnehmen:

```bash
EXTRA_SAN=IP:192.168.1.42 ./scripts/generate-cert.sh
```

## Testdaten

`seed.sh` legt an:

* drei Beiträge
* drei ausstehende Kommentare
* einen genehmigten Kommentar
* einen Spam-Kommentar
* einen Kommentar im Papierkorb
* einen Benutzer `moderator` mit der Rolle Editor (besitzt `moderate_comments`)

Die Kommentartexte und Zeitstempel sind fest vorgegeben, damit Tests
wiederholbar sind. Mehrfaches Ausführen legt nichts doppelt an.

## Screenshots und Demo-Video

Die Bilder und das Video in `docs/screenshots/` entstehen im Emulator gegen
diese Umgebung, mit erfundenen Kommentaren unter Namen und Logo von
linuxundich.de:

```bash
WP_SITE_URL=https://linuxundich.de docker compose up -d
docker compose restart proxy          # nach dem Neuerzeugen von WordPress
./scripts/seed.sh
./scripts/seed-screenshots.sh

adb reverse tcp:8443 tcp:8443
adb shell setprop debug.commentator.demo linuxundich.de=localhost:8443
```

Die Systemeigenschaft schaltet im **Debug-Build** eine Umleitung ein: Die App
zeigt `linuxundich.de`, spricht aber mit dieser Umgebung. Im Release-Build
gibt es sie nicht. Das Logo lädt die App vom echten Blog; ein
Must-Use-Plugin (`screenshots/demo-site-icon.php`) liefert dafür dessen
Adresse als Site-Icon.

`./scripts/seed-screenshots.sh --notify` legt danach einen weiteren offenen
Kommentar an – für die Benachrichtigung, nachdem die App ihren Ausgangszustand
festgehalten hat.

Gravatare erscheinen nur für Adressen, die wirklich einen haben. Die App fragt
mit `d=404` an und zeigt sonst ihren eigenen Platzhalter; die erfundenen Leser
bekommen deshalb keinen, das Teamkonto zeigt den des Betreibers.

Eine aufgeräumte Statusleiste gibt der Demo-Modus von SystemUI:

```bash
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e fully true
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
```
