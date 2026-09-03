# Phase 0 — ground truth, and where the plan was wrong

The handbook is downloaded, verified and transcribed. This file records what came out
of it, and — more usefully — the six places where having the real document contradicted
something `plan.md` assumed.

## The document

| | |
|---|---|
| Title | Owner's Handbook — Speed 400, Scrambler 400 X, Scrambler 400 XC, Thruxton 400 and Tracker 400 |
| Part number | `3850838-IN` |
| Market | **India** |
| PDF created | 2026-01-06 |
| Pages | 229 |
| Size / md5 | 10,811,884 bytes · `93c29b101d5e246695d48a3c49fb18b2` |
| Source | `https://api.triumphtechnicalinformation.com/handbooks/documents/698361316236d0957f547eca/pdf` |

The first-party Triumph endpoint in the plan's Appendix resolved with no auth wall and
returned the genuine document — 229 pages with a full extractable text layer. The
72 KB single-raster failure the plan warned about did not recur. The Team-BHP mirror
returns HTTP 403 and was not needed.

**Printed page numbers are identical to PDF page indices in this file**, so every page
citation in `components.json` and `facts.json` works both ways round.

## Where the plan turned out to be wrong

### 1. The handbook *does* contain torque figures

`plan.md` §10.5 and Appendix B Prompt 4 both state: *"the owner's handbook lacks torque
specs, so those refusals are correct."*

It does not lack them. **Page 202 carries a Torque Figures table** — eleven values
including engine oil drain plug (13 Nm), oil filter cover fixings (11 Nm), rear wheel
spindle nut (98 Nm) and spark plug (17 Nm). All eleven are seeded.

This changes Phase 4's behaviour: the assistant should now *answer* those eleven torque
questions from a cited page rather than refuse them, and refuse only torque values
outside that table. A workshop manual is still needed for anything else.

### 2. It is a newer, five-model, India-market handbook

The plan expected *"Speed 400 and Scrambler 400 X, UK English, 09/2023"*. What Triumph
serves today is a January 2026 India-market revision covering **five** models.

This introduces a failure mode the plan never anticipated: **most specification pages
have a per-model column, and reading the wrong one ships a wrong safety-critical
number.** Two that differ materially:

| Value | Speed 400 | Scrambler 400 X / XC |
|---|---|---|
| Drive chain free movement (p.134) | **20–25 mm** | 40–45 mm |
| Front carrier plate thickness (p.141) | **3.5 mm** | 3.74 mm |
| Front min. service thickness (p.141) | **4 mm** | 4.24 mm |

Everything seeded here was taken from the Speed 400 column only, and
`SeedIntegrityTest` pins the two chain/brake values specifically so a future re-extract
cannot silently drift onto a Scrambler row.

### 3. The service interval is 16,000 km, not the ~10,000 km the web sources implied

The Scheduled Maintenance Table (pp.116–119) is:

- **First service** — 600 miles (1,000 km) **or 6 months**, whichever comes first
- **Annual service** — every year
- **Mileage services** — 10,000 mi (**16,000 km**), 20,000 mi (32,000 km),
  30,000 mi (48,000 km), 40,000 mi (64,000 km)

Page 114 adds the rule that decides which schedule applies: under 16,000 km/year,
maintain annually plus mileage items as reached; over 16,000 km/year, maintain by
mileage plus annual items at their annual interval.

> **Open question for Pranav.** This is the factory schedule. Indian Triumph dealers
> commonly quote a *different*, denser schedule. If your service book says something
> other than 16,000 km, that is a `dealer`-sourced interval, not a `manual` one — it
> belongs in a second row with its own badge, and §3 P4 means it must not be allowed to
> overwrite the 🟢 value. Nothing dealer-sourced has been invented here.

### 4. There is no separate pillion tyre pressure

The plan's Prompt 0 asks for *"tyre pressures (solo and pillion)"*. The handbook gives
**one** figure per wheel for the Speed 400 (p.201): front 1.79 bar / 26.0 psi, rear
2.28 bar / 33.0 psi. No solo/pillion split exists for this model.

The app must therefore not present a pillion pressure field at all. Offering an empty
one invites it to be filled from a forum post, which is precisely the §3 P5 failure.

### 5. The handbook does not state the warranty period

§5.3's Warranty guard needs a warranty end date. Page 192 says only that the warranty
is *"unlimited mileage... commencing from the date of first registration or the date of
sale if the motorcycle remains unregistered"* and refers the owner to their **warranty
registration certificate** for the period.

So the duration is not seedable. It is recorded in `facts.json` as `NOT STATED` with
that page citation, and Phase 2 has to ask for it during backfill onboarding.

### 6. Five components are condition-based, with no interval at all

The handbook gives **no replacement interval** for the drive chain, sprockets, either
tyre, or the battery. It inspects them for wear instead. These five ship with
`interval_source: "unverified"` and *no* km or day figure, and a test asserts that an
unverified component never carries an interval — filling those gaps from general
knowledge is the exact thing §3 P5 prohibits.

## What was extracted

**`app/src/main/assets/seed/components.json`** — 52 components. 47 trace to a handbook
page; 5 are the condition-based items above. Includes the full daily-check list, the
2-year brake fluid and 4-year coolant time-only rules, the 300 km chain lubrication
interval, and the offset spark-plug check (16,000 km) vs. renewal (32,000 km).

**`app/src/main/assets/seed/facts.json`** — 69 facts, every one page-cited, 43 flagged
safety-critical. Engine and oil capacities, coolant, fuel, ignition, tyres, brakes,
transmission, electrical, all eleven torque figures, and the service intervals.

## What is NOT verified

Every fact row ships with `verified_on: null`.

The values were read out of the PDF's text layer page by page — which is a good deal
better than a model summarising the document, but it is not the same as eyes on the
rendered page. Two-column PDF text extraction can transpose a value between adjacent
columns, and this handbook has five model columns per table.

§10.3 is explicit that rows are promoted only by the owner's own eyes. **Run Appendix B
Prompt 5 next**, against `docs/handbook/` — until then the app renders safety-critical
values as "manual, unconfirmed".
