# Speed 400 Garage

A personal Android **tablet** app that is the long-term digital memory of one
motorcycle: a 2024 Triumph Speed 400.

> I should never have to remember anything about my bike.

Built to the spec in [`docs/plan.md`](docs/plan.md). This tree is at the end of
**Phase 0** — ground truth is established and the app is scaffolded around it. The
logbook (Phase 1) is not built yet.

## What Phase 0 delivered

Phase 0's done-when criterion was: *the app opens, knows the bike exists, and every
interval in it traces to a page number in the handbook.*

- **The owner's handbook, downloaded and verified** — part `3850838-IN`, 229 pages,
  India market, first-party Triumph source. See
  [`docs/phase0-handbook-findings.md`](docs/phase0-handbook-findings.md), which also
  lists **six places where the real document contradicted the plan**.
- **`app/src/main/assets/seed/components.json`** — 52 components; 47 carry a handbook
  page citation, 5 are honestly marked condition-based with no interval.
- **`app/src/main/assets/seed/facts.json`** — 69 page-cited specifications, 43 of them
  safety-critical, including the eleven torque figures the plan assumed didn't exist.
- **The §6 schema** as Room entities — UUID primary keys, row-level `updated_at`,
  one event log with line items, component actions, attachments and odometer readings
  hanging off it.
- **Three screens** — Dashboard, Maintenance (the component catalogue with its
  provenance badges) and Quick Specs (the offline, no-AI specification lookup from
  §10.3).
- **Tests that enforce the plan's rules**, not just the code's behaviour: no interval
  without a citation, no unverified row asserting an interval anyway, no fact shipped
  pre-verified, and spot checks on the values most likely to be read off the wrong
  model column.

## Design rules this codebase actually enforces

| Plan | How it shows up in the code |
|---|---|
| §3 P2 — one event log, many views | `EventWriteDao.writeEvent` writes an event and all its facets in one transaction. A service visit is one row, not seven. |
| §3 P3 — the odometer is the spine | `odometer_reading` holds observed readings only. Projections are computed at read time and never written. |
| §3 P4 — provenance is first-class | `Provenance` + `ProvenanceBadge`, rendered next to every interval and specification. An unknown source falls to ⚪, never up to 🟢. |
| §3 P5 — never invent a safety number | `SafetyRule.isCitable` requires both a manual source and a page number. `SeedIntegrityTest` fails the build if a safety-critical fact lacks either. |
| §3 P6 — the data outlives the device | No `fallbackToDestructiveMigration`. Room schemas are exported to `app/schemas/` and committed. |
| §4.1 — tablet only | No `WindowSizeClass` anywhere. A permanent navigation rail and a fixed two-pane `ListDetailPane` *are* the layout. |
| §4.2 — money lives in one place | Every total is a `SUM(line_item.amount)` over a filter. Amounts are integer paise. |
| §12 — nothing phones home | Android auto-backup and device-transfer are both disabled; no analytics or crash SDK. |

## Installing it on the tablet

You sideload the APK **once**. After that the app updates itself: it notices new
GitHub Releases, verifies the download's checksum, and hands it to Android's installer
— your data carries across untouched, because an update is not a reinstall.

Shipping a new version is one command:

```bash
git tag v0.2.0 -m "Fuel logging and the economy engine"
git push origin v0.2.0
```

Full setup — signing key, the read-only GitHub token the app needs to see releases of
a private repo, and what the update check does and does not send — is in
[`docs/releasing.md`](docs/releasing.md).

## Building

Needs JDK 17+ and an Android SDK with platform 35.

```bash
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew :app:assembleDebug     # build
./gradlew :app:testDebugUnitTest # the seed-integrity and provenance tests
```

`local.properties`, the signing keystore, the GitHub token and the Gemini API key are
all gitignored or stored encrypted on-device, and must stay that way (§14).

> **The signing keystore is the one irreplaceable file in this project.** Android
> refuses to install an update signed with a different key, so losing it means the only
> route to a new build is uninstall-and-reinstall — which destroys a ten-year record.
> `scripts/setup-signing.sh` creates it once; back it up somewhere durable.

## Next

**Phase 0 is not quite closed.** Two things are outstanding, both of which need Pranav
rather than a build:

1. **Verify the seeded facts** — Appendix B Prompt 5, against `docs/handbook/`. Every
   row currently reads `verified_on: null` because the values were read from the PDF's
   text layer, not confirmed by eye on the rendered page (§10.3).
2. **Answer the §16 questions**, listed at the end of the findings doc — the dealer
   service schedule, backfill history, DIY vs dealer, and the warranty period, which
   the handbook does not state.

Then **Phase 1 — the logbook** (Appendix B Prompt 1). Its engines (§9) get unit tests
before any UI.
