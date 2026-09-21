#!/usr/bin/env bash
# Richtet die Testumgebung ein und legt reproduzierbare Testdaten an.
#
# Mehrfaches Ausführen ist unschädlich: Bereits vorhandene Daten werden
# erkannt und nicht doppelt angelegt.
#
# Verwendung:
#   ./scripts/generate-cert.sh
#   docker compose up -d
#   ./scripts/seed.sh

set -euo pipefail

cd "$(dirname "$0")/.."

SITE_URL="${WP_SITE_URL:-https://localhost:8443}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-commentator-test}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@example.test}"
MODERATOR_USER="${MODERATOR_USER:-moderator}"
MODERATOR_PASSWORD="${MODERATOR_PASSWORD:-commentator-test}"

wp() {
    docker compose --profile tools run --rm -T wpcli wp --path=/var/www/html "$@"
}

echo "== Warte auf WordPress-Dateien =="
for _ in $(seq 1 60); do
    if wp core is-installed --quiet 2>/dev/null || wp core version >/dev/null 2>&1; then
        break
    fi
    sleep 2
done

echo "== WordPress installieren =="
if wp core is-installed 2>/dev/null; then
    echo "   bereits installiert"
else
    wp core install \
        --url="$SITE_URL" \
        --title="Commentator Testblog" \
        --admin_user="$ADMIN_USER" \
        --admin_password="$ADMIN_PASSWORD" \
        --admin_email="$ADMIN_EMAIL" \
        --skip-email
fi

echo "== Grundeinstellungen =="
# Jeder Kommentar landet in der Moderation - genau der Zustand, für den die
# App gebaut ist.
wp option update comment_moderation 1
wp option update comment_previously_approved 0
wp option update default_comment_status open
wp option update permalink_structure '/%postname%/'
wp rewrite flush --hard

echo "== Bridge-Plugin aktivieren =="
wp plugin activate commentator-bridge || echo "   Plugin nicht gefunden, wird übersprungen"

echo "== Moderator anlegen =="
if wp user get "$MODERATOR_USER" >/dev/null 2>&1; then
    echo "   existiert bereits"
else
    wp user create "$MODERATOR_USER" "moderator@example.test" \
        --role=editor \
        --user_pass="$MODERATOR_PASSWORD" \
        --display_name="Test-Moderatorin"
fi

echo "== Beiträge anlegen =="
declare -a POSTS=(
    "Linux auf dem Desktop|Ein Erfahrungsbericht nach zwölf Monaten ohne Windows."
    "Warum ich Kommentare moderiere|Über Spam, Tonfall und die Pflege einer Diskussion."
    "Kleine Werkzeuge, große Wirkung|Drei Kommandozeilenprogramme, die den Alltag ändern."
)

for entry in "${POSTS[@]}"; do
    title="${entry%%|*}"
    body="${entry#*|}"
    if [[ -z "$(wp post list --post_type=post --title="$title" --field=ID --format=ids)" ]]; then
        wp post create --post_type=post --post_status=publish \
            --post_title="$title" --post_content="$body"
    fi
done

# Nur die oben angelegten Beiträge verwenden. Die Liste aller Beiträge
# enthielte auch WordPress' Standardbeitrag "Hello world!", und die
# Testkommentare landeten dann dort statt an den passenden Texten.
POST_IDS=()
for entry in "${POSTS[@]}"; do
    title="${entry%%|*}"
    id=$(wp post list --post_type=post --post_status=publish --title="$title" --field=ID --format=ids | tr -d '\r')
    [[ -n "$id" ]] && POST_IDS+=("$id")
done

if [[ ${#POST_IDS[@]} -lt 3 ]]; then
    echo "Die angelegten Beiträge wurden nicht gefunden - Abbruch." >&2
    exit 1
fi

echo "== Testkommentare anlegen =="
existing=$(wp comment list --format=count --status=all 2>/dev/null || echo 0)
if [[ "$existing" -gt 2 ]]; then
    echo "   bereits $existing Kommentare vorhanden, es werden keine weiteren angelegt"
else
    create_comment() {
        local post_id="$1" author="$2" email="$3" status="$4" content="$5" date="$6"
        local parent="${7:-0}"
        wp comment create \
            --comment_post_ID="$post_id" \
            --comment_author="$author" \
            --comment_author_email="$email" \
            --comment_content="$content" \
            --comment_date="$date" \
            --comment_approved="$status" \
            --comment_parent="$parent" \
            --porcelain
    }

    # Status: 0 = ausstehend, 1 = genehmigt, spam, trash
    create_comment "${POST_IDS[0]}" "Max Mustermann" "max@example.test" 0 \
        "Sehr interessanter Artikel. Wie sieht es denn mit der Akkulaufzeit aus?" \
        "2026-09-19 08:15:00"
    create_comment "${POST_IDS[0]}" "Erika Beispiel" "erika@example.test" 0 \
        "Ich habe dazu noch eine Frage: Welche Distribution nutzt du inzwischen?" \
        "2026-09-19 09:42:00"
    jana=$(create_comment "${POST_IDS[1]}" "Jana Leser" "jana@example.test" 1 \
        "Danke für den Beitrag, das deckt sich mit meinen Erfahrungen." \
        "2026-09-18 17:05:00")
    # Eine offene Antwort auf einen bereits genehmigten Kommentar - der Fall,
    # um den es beim Gespraechsfaden geht: In der Liste "Offen" steht die
    # Antwort, der Kommentar davor fehlt dort und muss nachgeholt werden.
    create_comment "${POST_IDS[1]}" "Pia Nachfrage" "pia@example.test" 0 \
        "Wie meinst du das mit den Erfahrungen genau?" \
        "2026-09-19 14:20:00" "$jana"
    create_comment "${POST_IDS[1]}" "Billiger Kredit" "spam@example.test" spam \
        "Guenstige Kredite ohne Schufa, jetzt hier klicken!" \
        "2026-09-18 03:11:00"
    create_comment "${POST_IDS[2]}" "Alter Beitrag" "papierkorb@example.test" trash \
        "Dieser Kommentar liegt im Papierkorb." \
        "2026-09-17 12:00:00"
    create_comment "${POST_IDS[2]}" "Tom Technik" "tom@example.test" 0 \
        "Lässt sich das auch mit fish statt bash verwenden? Mit Code: <code>ls -la</code>" \
        "2026-09-19 11:30:00"
fi

echo
echo "== Application Password für die App =="
echo "Das folgende Passwort wird nur einmal angezeigt:"
wp user application-password create "$MODERATOR_USER" "Commentator Android" --porcelain

echo
echo "== Fertig =="
echo "Blog-Adresse für die App: $SITE_URL"
echo "Benutzername:             $MODERATOR_USER"
echo "WordPress-Backend:        $SITE_URL/wp-admin ($ADMIN_USER / $ADMIN_PASSWORD)"
echo
echo "Hinweis: Das Zertifikat ist selbstsigniert. Auf dem Gerät muss"
echo "docker/certs/server.crt als Benutzerzertifikat installiert sein."
