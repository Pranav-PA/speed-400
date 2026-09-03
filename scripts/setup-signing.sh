#!/usr/bin/env bash
# Creates the ONE signing key this app will ever use, and prints the GitHub secrets
# that CI needs. Run it once, on your own machine. It never uploads anything.
#
#   bash scripts/setup-signing.sh
#
# On Windows use Git Bash (right-click in the repo folder -> "Git Bash Here"), or run
# the PowerShell version instead: scripts/setup-signing.ps1
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

# keytool ships with any JDK. Android Studio bundles one, so look there before giving up.
if ! command -v keytool >/dev/null 2>&1; then
  for candidate in \
    "/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe" \
    "$LOCALAPPDATA/Programs/Android Studio/jbr/bin/keytool.exe" \
    "$JAVA_HOME/bin/keytool"; do
    if [ -x "$candidate" ]; then KEYTOOL="$candidate"; break; fi
  done
  if [ -z "${KEYTOOL:-}" ]; then
    echo "ERROR: keytool not found. It ships with any JDK."
    echo "  Linux/macOS:  sudo apt install default-jdk   /   brew install openjdk@17"
    echo "  Windows:      winget install EclipseAdoptium.Temurin.17.JDK"
    echo "  Or, with Android Studio installed, it is usually at:"
    echo "    C:\\Program Files\\Android\\Android Studio\\jbr\\bin\\keytool.exe"
    exit 1
  fi
else
  KEYTOOL="$(command -v keytool)"
fi
echo "Using keytool at $KEYTOOL"
echo

read -rsp "Choose a keystore password (you will need it again): " STORE_PASS; echo
read -rsp "Confirm: " STORE_PASS2; echo
[ "$STORE_PASS" = "$STORE_PASS2" ] || { echo "Passwords did not match."; exit 1; }
[ ${#STORE_PASS} -ge 8 ] || { echo "Use at least 8 characters."; exit 1; }

"$KEYTOOL" -genkeypair -v \
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
echo "  KEYSTORE_BASE64    = the contents of keystore-base64.txt (written below)"
echo

# The base64 of a 4096-bit keystore is a few thousand characters. Pasting that out of a
# terminal reliably is miserable, so it goes to a file you can open and select all.
{ base64 -w0 "$OUT" 2>/dev/null || base64 -i "$OUT" | tr -d '\n'; } > keystore-base64.txt
echo "Wrote keystore-base64.txt — open it, select all, and paste as KEYSTORE_BASE64."
echo "Delete that file once the secret is saved; it is as sensitive as the keystore."
echo
echo "With the gh CLI you can skip the copy-paste entirely:"
echo "  gh secret set KEYSTORE_BASE64 --repo Pranav-PA/speed-400 < keystore-base64.txt"
echo "  gh secret set KEYSTORE_PASSWORD --repo Pranav-PA/speed-400"
echo "  gh secret set KEY_PASSWORD --repo Pranav-PA/speed-400"
echo "  gh secret set KEY_ALIAS --repo Pranav-PA/speed-400 --body $ALIAS"
