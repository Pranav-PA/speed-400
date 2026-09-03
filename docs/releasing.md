# Releasing, and how the tablet updates itself

The short version: **you install the APK by hand exactly once.** After that the app
notices new releases and installs them itself.

---

## One-time setup

### 1. Create the signing key (once, on your machine, and never again)

```bash
./scripts/setup-signing.sh
```

It writes `release.jks` and prints four GitHub secrets to add at
`Settings → Secrets and variables → Actions`:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`.

> **Back `release.jks` up somewhere you will still have in five years.**
>
> Android refuses to install an update signed with a different key than the installed
> app. Lose this key and the only way to install a new build is to uninstall the old
> one first — which deletes the database. For a ten-year record (§3 P6) that is the
> single worst thing that can happen to this project, and it is entirely preventable.
> The keystore is gitignored; it must stay that way.

### 2. Cut the first release

```bash
git tag v0.1.0
git push origin v0.1.0
```

CI runs the tests, builds a signed APK, verifies the signature, computes its SHA-256,
and publishes both `speed400garage-0.1.0.apk` and `update.json` to a GitHub Release.

### 3. Install it on the tablet, by hand, this once

On the tablet, sign in to GitHub and open the release page:
`https://github.com/Pranav-PA/speed-400/releases/latest`

Download the `.apk` and open it. Android will ask you to allow installs from your
browser — that grant is for the browser, and is separate from the one below.

### 4. Give the app a token so it can see its own releases

The repository is **private**, so the app cannot read releases anonymously.

Create a token at `https://github.com/settings/personal-access-tokens/new`:

| Field | Value |
|---|---|
| Token name | `speed-400 tablet` |
| Repository access | **Only select repositories** → `Pranav-PA/speed-400` |
| Permissions | **Contents: Read-only** — nothing else |
| Expiration | your call; the app tells you plainly when it stops working |

Open the app → **Settings** → paste it → **Save** → **Check now**.

The token is stored in encrypted preferences on the tablet. It is never committed and
never compiled into the APK.

### 5. Let the app install packages

The first time you tap **Install**, the app will offer to send you to Android's
"Install unknown apps" screen. Allow it for Speed 400 Garage. One-time.

---

## After that: shipping a new version

```bash
git tag v0.2.0 -m "Fuel logging and the economy engine"
git push origin v0.2.0
```

That is the whole ritual. Next time you open the app on the tablet it says
*"Version 0.2.0 is available"*, you tap **Download** then **Install**, and your data
carries across untouched — it is an update, not a reinstall.

The tag annotation becomes the release notes shown in the dialog.

### Version numbering

Tags must be `vMAJOR.MINOR.PATCH`. CI derives `versionCode` as
`major*10000 + minor*100 + patch`, so it always increases as long as minor and patch
stay under 100. A local debug build stays at versionCode 1 and will therefore never
look newer than a release.

---

## What the update check actually sends

This is the one place the app talks to the network without being asked a question, so
it is worth being precise (§12):

- An HTTPS GET to `api.github.com` for the latest release, carrying your read-only
  token — and nothing else.
- If an update exists, a second GET for `update.json`, then one for the APK.
- **No** odometer readings, spend figures, service history, documents, identifiers,
  analytics or crash reports. There is no analytics SDK in the project at all.

Turn **auto-check** off in Settings and the app makes no network calls whatsoever
until the Phase 4 assistant exists.

## What protects you from a bad download

- `update.json` carries the APK's SHA-256; the app verifies it before the file is
  offered to the installer, and deletes it on a mismatch.
- Android verifies the signature independently: an APK not signed with your key will
  not install over your app, whatever this code does.
- The download lands in app-private storage. The FileProvider exposes only the
  `updates/` directory — never the database or attachments.

## If the check fails

| What you see | What it means |
|---|---|
| "Add a GitHub token in Settings" | Step 4 not done yet. |
| "GitHub rejected the token" | Expired, revoked, or missing Contents:Read on this repo. |
| "No published release yet" | No tag pushed, or CI has not finished. |
| "Release vX has no update.json" | The release was created by hand, not by CI. Push a tag instead. |
| Checksum failure | Genuinely corrupt or tampered download. The file is discarded; try again. |
