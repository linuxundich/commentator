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
| Android-Emulator | `https://10.0.2.2:8443` |
| Echtes Gerät im LAN | `https://<IP des Rechners>:8443` |

Für den Emulator oder ein echtes Gerät muss die Adresse vor dem ersten Start
gesetzt werden, damit WordPress sie in seine Konfiguration übernimmt:

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
