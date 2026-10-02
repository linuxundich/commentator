#!/usr/bin/env bash
# Ersetzt die Testdaten durch vorzeigbare für die Screenshots im README.
# Läuft nach seed.sh.
#
# Der Blog trägt Namen und Logo von linuxundich.de; die Kommentare und ihre
# Verfasser sind erfunden, es erscheint nichts von echten Lesern. Das
# Teamkonto ist der Betreiber selbst, mit seinem Gravatar.
#
# Damit die App die Adresse https://linuxundich.de zeigt, aber mit dieser
# Testumgebung spricht, braucht es zweierlei:
#
#   WP_SITE_URL=https://linuxundich.de docker compose up -d
#   adb shell setprop debug.commentator.demo linuxundich.de=localhost:8443
#
# Letzteres wirkt nur im Debug-Build (DemoHostInterceptor). Die Zeitpunkte liegen relativ zu jetzt, damit die App
# „vor 12 Min.“ statt eines alten Datums zeigt.
#
# Ein Kommentar - der von „Lena Krüger“ - wird bewusst nicht angelegt. Er
# ist für die Benachrichtigung gedacht und kommt erst, nachdem die App ihren
# Ausgangszustand festgehalten hat:
#
#   ./scripts/seed-screenshots.sh            # Blog vorbereiten
#   ./scripts/seed-screenshots.sh --notify   # danach: Lenas Kommentar
#
# Verwendung:
#   docker compose up -d && ./scripts/seed.sh && ./scripts/seed-screenshots.sh

set -euo pipefail

cd "$(dirname "$0")/.."

wp() {
    docker compose --profile tools run --rm -T wpcli wp --path=/var/www/html "$@" 2>/dev/null
}

# Zeitpunkt vor N Minuten, in der Zeitzone, die WordPress für comment_date erwartet.
vor() {
    date -u -d "-$1 minutes" '+%Y-%m-%d %H:%M:%S'
}

post_id() {
    wp post list --post_type=post --post_status=publish --title="$1" --field=ID --format=ids | tr -d '\r'
}

kommentar() {
    local post="$1" autor="$2" mail="$3" status="$4" text="$5" minuten="$6" eltern="${7:-0}" nutzer="${8:-0}"
    wp comment create \
        --comment_post_ID="$post" \
        --comment_author="$autor" \
        --comment_author_email="$mail" \
        --comment_content="$text" \
        --comment_date_gmt="$(vor "$minuten")" \
        --comment_date="$(vor "$minuten")" \
        --comment_approved="$status" \
        --comment_parent="$eltern" \
        --user_id="$nutzer" \
        --porcelain | tr -d '\r'
}

DEMO_NAME="${DEMO_NAME:-Linux und Ich}"
DEMO_DESC="${DEMO_DESC:-Blog über Ubuntu, Linux, Android und IT}"
DEMO_ICON="${DEMO_ICON:-https://linuxundich.de/wp-content/themes/lui-theme/assets/images/favicon/apple-touch-icon.png}"
TEAM_NAME="${TEAM_NAME:-Christoph Langner}"
TEAM_MAIL="${TEAM_MAIL:-mail@christoph-langner.de}"

P_BTRFS="Btrfs-Snapshots mit Snapper einrichten"
P_GNOME="GNOME 51: Die wichtigsten Neuerungen"
P_NTFY="ntfy: Benachrichtigungen ohne Google"

if [[ "${1:-}" == "--notify" ]]; then
    kommentar "$(post_id "$P_BTRFS")" "Lena Krüger" "lena@example.test" 0 \
        "Danke für die Anleitung! Funktioniert das auch, wenn /home auf einem eigenen Subvolume liegt?" 0
    exit 0
fi

echo "== Blog umbenennen =="
wp option update blogname "$DEMO_NAME"
wp option update blogdescription "$DEMO_DESC"
wp user update moderator --display_name="$TEAM_NAME" --user_email="$TEAM_MAIL" \
    --first_name="${TEAM_NAME%% *}" --last_name="${TEAM_NAME#* }" --skip-email

# Erfundene Leser haben keinen Gravatar; WordPress erzeugt ihnen dann eins.
wp option update show_avatars 1
wp option update avatar_default wavatar
MOD=$(wp user get moderator --field=ID | tr -d '\r')

echo "== Bisherige Kommentare und Beiträge entfernen =="
ids=$(wp comment list --status=all --format=ids | tr -d '\r')
if [[ -n "$ids" ]]; then wp comment delete $ids --force >/dev/null; fi
for status in spam trash; do
    ids=$(wp comment list --status="$status" --format=ids | tr -d '\r')
    if [[ -n "$ids" ]]; then wp comment delete $ids --force >/dev/null; fi
done
ids=$(wp post list --post_type=post --post_status=any --field=ID --format=ids | tr -d '\r')
if [[ -n "$ids" ]]; then wp post delete $ids --force >/dev/null; fi

echo "== Beiträge anlegen =="
for titel in "$P_BTRFS" "$P_GNOME" "$P_NTFY"; do
    wp post create --post_type=post --post_status=publish --post_title="$titel" \
        --post_content="Beispielbeitrag für die Screenshots." >/dev/null
done
BTRFS=$(post_id "$P_BTRFS")
GNOME=$(post_id "$P_GNOME")
NTFY=$(post_id "$P_NTFY")

echo "== Site-Icon =="
# Über ein Must-Use-Plugin, das die Adresse aus einer Option liefert. So
# lädt die App das Logo vom echten Blog, ohne dass es hier liegen muss.
container=$(docker compose ps -q wordpress)
docker exec "$container" mkdir -p /var/www/html/wp-content/mu-plugins
docker cp screenshots/demo-site-icon.php "$container:/var/www/html/wp-content/mu-plugins/demo-site-icon.php"
wp option update commentator_demo_site_icon "$DEMO_ICON"
wp option delete site_icon >/dev/null || true

echo "== Kommentare anlegen =="
# Freigegeben und vom Team beantwortet.
tobias=$(kommentar "$NTFY" "Tobias Brandt" "tobias@example.test" 1 \
    "Endlich eine Lösung ohne Firebase. Läuft bei mir seit einer Woche stabil." 1500)
kommentar "$NTFY" "$TEAM_NAME" "$TEAM_MAIL" 1 \
    "Freut mich! Danke für die Rückmeldung." 1440 "$tobias" "$MOD" >/dev/null

# Freigegeben, aber noch ohne Antwort - für den Filter „Unbeantwortet“.
kommentar "$BTRFS" "Jonas Weber" "jonas@example.test" 1 \
    "Snapper nutze ich seit Jahren, bei Updates ist das unschlagbar." 4300 >/dev/null
kommentar "$GNOME" "Kai Schmitt" "kai@example.test" 1 \
    "Ist GNOME 51 unter Arch schon in den Paketquellen?" 300 >/dev/null

# Offen. Jonas ist bekannt, Mira antwortet auf einen freigegebenen Kommentar
# (Gesprächsfaden), die „Agentur“ trägt die Spam-Merkmale.
kommentar "$GNOME" "Jonas Weber" "jonas@example.test" 0 \
    "Die neue Schnelleinstellung für Bluetooth finde ich klasse. Lässt sich die alte Ansicht zurückholen?" 35 >/dev/null
kommentar "$NTFY" "Mira Hoffmann" "mira@example.test" 0 \
    "Bei mir läuft ntfy im Docker-Container hinter Nginx. Braucht es dafür besondere Einstellungen für WebSockets?" 70 "$tobias" >/dev/null
kommentar "$BTRFS" "SEO Agentur Profi" "info@example.test" 0 \
    'Top-Rankings garantiert! <a href="https://example.test/a">Jetzt buchen</a>, <a href="https://example.test/b">Preise</a>, <a href="https://example.test/c">Referenzen</a>' 120 >/dev/null

# Spam und Papierkorb, damit die Zahlen an den Filtern nicht leer sind.
kommentar "$GNOME" "Billig Uhren" "uhren@example.test" spam \
    "Luxusuhren zum halben Preis, nur heute!" 900 >/dev/null
kommentar "$BTRFS" "Testeintrag" "test@example.test" trash \
    "Versehentlich doppelt abgeschickt." 2000 >/dev/null

echo "== Fertig =="
wp comment list --status=all --fields=comment_ID,comment_author,comment_approved --format=table
