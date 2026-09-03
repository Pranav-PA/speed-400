#!/usr/bin/env bash
# Creates the ONE signing key this app will ever use, and prints the GitHub secrets
# that CI needs. Run it once, on your own machine. It never uploads anything.
#
#   ./scripts/setup-signing.sh
#
# WHY THIS MATTERS: Android refuses to install an update signed with a different key
# than the installed app. If this keystore is lost or regenerated, the only way to
# install a new build is to uninstall the old one first — which deletes the database.
# Back up release.jks somewhere you will still have in five years.
set -euo pipefail

OUT="${1:-release.jks}"
ALIAS="speed400garage"

if [ -f "$OUT" ]; then
  echo "ERROR: $OUT already exists. Refusing to overwrite an existing signing key."
  echo "If you genuinely want a new one, move the old file aside first — and read"
  echo "docs/releasing.md, because a new key means uninstall-and-reinstall on the tablet."
  exit 1
fi

read -rsp "Choose a keystore password (you will need it again): " STORE_PASS; echo
read -rsp "Confirm: " STORE_PASS2; echo
[ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Passwords did not match."; exit 1; }
[ ${#STORE_PASS} -ge 8 ] || { echo "Use at least 8 characters."; exit 1; }

keytool -genkeypair -v \
  -keystore "$OUT" \
  -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 \
  -validity 10950 \
  -storepass "$STORE_PASS" -keypass "$STORE_PASS" \
  -dname "CN=Speed 400 Garage, OU=Personal, O=Pranav P Aradhya, L=Mysuru, C=IN"

echo
echo "Created $OUT (valid 30 years). BACK IT UP NOW — losing it means losing the"
echo "ability to update the app without wiping its data."
echo
echo "Add these four repository secrets at:"
echo "  https://github.com/Pranav-PA/speed-400/settings/secrets/actions"
echo
echo "  KEYSTORE_PASSWORD  = the password you just chose"
echo "  KEY_PASSWORD       = the same password"
echo "  KEY_ALIAS          = $ALIAS"
echo "  KEYSTORE_BASE64    = the single line below"
echo
base64 -w0 "$OUT" 2>/dev/null || base64 -i "$OUT" | tr -d '\n'
echo
echo
echo "With the gh CLI you can do it in one go instead:"
echo "  gh secret set KEYSTORE_BASE64 --repo Pranav-PA/speed-400 < <(base64 -w0 $OUT)"
echo "  gh secret set KEYSTORE_PASSWORD --repo Pranav-PA/speed-400"
echo "  gh secret set KEY_PASSWORD --repo Pranav-PA/speed-400"
echo "  gh secret set KEY_ALIAS --repo Pranav-PA/speed-400 --body $ALIAS"
