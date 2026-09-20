#!/usr/bin/env bash
# Erzeugt ein selbstsigniertes Zertifikat für die lokale Testumgebung.
#
# Die Alternativnamen decken die üblichen Zugriffswege ab:
#   localhost   - Aufrufe vom Entwicklungsrechner
#   10.0.2.2    - der Host aus Sicht des Android-Emulators
#   127.0.0.1   - direkte Aufrufe
#
# Für ein echtes Gerät im selben Netz die eigene IP als zusätzlichen Namen
# über EXTRA_SAN übergeben, zum Beispiel:
#   EXTRA_SAN=IP:192.168.1.42 ./generate-cert.sh

set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p certs

SAN="DNS:localhost,IP:127.0.0.1,IP:10.0.2.2"
if [[ -n "${EXTRA_SAN:-}" ]]; then
    SAN="$SAN,$EXTRA_SAN"
fi

openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout certs/server.key \
    -out certs/server.crt \
    -days 825 \
    -subj "/CN=localhost/O=Commentator Testumgebung" \
    -addext "subjectAltName=$SAN" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,digitalSignature,keyCertSign"

chmod 644 certs/server.crt
chmod 600 certs/server.key

echo
echo "Zertifikat erstellt: docker/certs/server.crt"
echo "Alternativnamen: $SAN"
echo
echo "Damit ein Android-Gerät der Verbindung traut, muss das Zertifikat dort"
echo "als Benutzerzertifikat installiert werden:"
echo "  adb push certs/server.crt /sdcard/Download/commentator-test.crt"
echo "  Einstellungen > Sicherheit > Verschlüsselung > Zertifikat installieren"
echo
echo "Debug-Builds von Commentator vertrauen Benutzerzertifikaten; Release-"
echo "Builds tun das ausdrücklich nicht."
