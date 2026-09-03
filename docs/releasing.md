# Installing it, and how the tablet updates itself

The short version: **you sideload the APK by hand exactly once.** After that the app
notices new releases and installs them itself.

The repository is public, so the app needs no credentials and there is nothing to
configure on the tablet.

---

## Part 1 — one-time setup (on your computer, ~5 minutes)

### 1. Create the signing key

```bash
git clone https://github.com/Pranav-PA/speed-400
cd speed-400
./scripts/setup-signing.sh
```

It writes `release.jks` and prints four values. Add them at
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the long single line the script prints |
| `KEYSTORE_PASSWORD` | the password you chose |
| `KEY_PASSWORD` | the same password |
| `KEY_ALIAS` | `speed400garage` |

> **Back `release.jks` up somewhere you will still have in five years.**
>
> Android refuses to install an update signed with a different key than the installed
> app. Lose this key and the only route to a new build is to uninstall the old one
> first — which deletes the database. For a ten-year record (§3 P6) that is the single
> worst thing that can happen here, and it is entirely preventable. The keystore is
> gitignored; it must stay that way.

Requires `keytool`, which ships with any JDK. `sudo apt install default-jdk` if you
don't have one.

### 2. Cut the first release

```bash
git tag v0.1.0 -m "Phase 0 — handbook ground truth and the logbook scaffold"
git push origin v0.1.0
```

Watch it at **Actions** in the repo. It runs the tests, builds a signed APK, verifies
the signature, computes the SHA-256, and publishes the APK plus `update.json` to a
GitHub Release. Takes about three minutes.

If the run fails with *"KEYSTORE_BASE64 secret is not set"*, step 1 isn't finished.

---

## Part 2 — on the tablet (~2 minutes, once)

### 3. Download the APK

Open **`https://github.com/Pranav-PA/speed-400/releases/latest`** in the tablet's
browser and tap `speed400garage-0.1.0.apk` under **Assets**.

### 4. Install it

Open the download. Android will say the browser isn't allowed to install apps — tap
**Settings**, enable it for the browser, come back, **Install**.

*(That grant is for the browser. Step 6 grants the same thing to the app itself, which
is what makes future updates one-tap.)*

### 5. Open it

You should see the dashboard: **Triumph Speed 400**, the handbook part number
`3850838-IN`, and four counters — 52 components, 69 facts, 0 events, 0 inbox.

Tap **Maintenance** to see all 52 intervals with their 🟢 badges and page numbers, and
**Quick Specs** for the 69 page-cited specifications. That is Phase 0's entire surface;
logging fuel and expenses is Phase 1.

### 6. Let the app install updates

**Settings → Check now.** It will say "Up to date" (you just installed it).

The first time a real update arrives, tapping **Install** offers to send you to
Android's *Install unknown apps* screen. Allow it for Speed 400 Garage — one time,
and after that updates are Download → Install without leaving the app.

---

## After that: shipping a new version

```bash
git tag v0.2.0 -m "Fuel logging and the economy engine"
git push origin v0.2.0
```

That is the whole ritual. Next time you open the app it says *"Version 0.2.0 is
available"*. Tap **Download**, then **Install**. Your data carries across untouched —
it is an update, not a reinstall.

The tag's message becomes the release notes shown in the dialog, so write it for
yourself six months later.

### Version numbering

Tags must be `vMAJOR.MINOR.PATCH`. CI derives `versionCode` as
`major*10000 + minor*100 + patch`, so it always increases as long as minor and patch
stay under 100. A local debug build stays at versionCode 1 and can never look newer
than a release.

---

## What the update check actually sends

This is the one place the app talks to the network without being asked a question, so
it is worth being precise (§12):

- An HTTPS GET to `github.com` for `update.json`, and if a newer version exists, a
  second GET for the APK.
- **No** odometer readings, spend figures, service history, documents, identifiers,
  analytics or crash reports. There is no analytics SDK in the project at all.

Turn **auto-check** off in Settings and the app makes no network calls whatsoever
until the Phase 4 assistant exists.

## What protects you from a bad download

- `update.json` carries the APK's SHA-256; the app verifies it before the file is ever
  offered to the installer, and deletes it on a mismatch.
- Android verifies the signature independently: an APK not signed with your key will
  not install over your app, whatever this code does.
- The download lands in app-private storage, and the FileProvider exposes only the
  `updates/` directory — never the database or attachments.

## If something goes wrong

| What you see | What it means |
|---|---|
| CI: "KEYSTORE_BASE64 secret is not set" | Step 1 not finished. |
| CI: "Version 'x' is not MAJOR.MINOR.PATCH" | Tag as `v0.2.0`, not `v0.2` or `release-2`. |
| App: "No published release yet" | No tag pushed, or CI is still running. |
| App: "Latest release has no update.json" | The release was made by hand, not by CI. Push a tag. |
| App: checksum failure | Genuinely corrupt or tampered download. The file is discarded; try again. |
| Install: "App not installed" | Almost always a signing key mismatch — you are trying to install a differently-signed build over an existing one. |

## Running it on a computer instead

You don't need to; the tablet is the target. But for development:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:testDebugUnitTest    # the seed-integrity tests
./gradlew :app:installDebug         # onto a connected device or emulator
```

A debug build is signed with the debug key, so it cannot be installed over a release
build and cannot receive updates. Keep them on separate devices, or uninstall one
before installing the other.
