# Speed 400 Garage — Product & Implementation Plan

A personal Android app that becomes the long-term digital memory of one motorcycle:
a 2024 Triumph Speed 400.

**Status:** planning document. No code written yet.
**Author:** drafted for Pranav P Aradhya (Mysuru, India), single-user, single-bike.
**Last updated:** 2026-09-03 (rev 3 — self-contained; carries its own kickoff prompts)

> **Starting a fresh session?** This document is designed to be the only thing you
> upload. Read §1–§18 for the product, then go to **Appendix B — Session prompts** and
> paste the Phase 0 prompt. Everything a new session needs to know is in here; no prior
> conversation is required.

---

## 1. The premise

> I should never have to remember anything about my bike.

Everything that follows is a consequence of that one sentence. If a fact about the
motorcycle exists — what it cost, when it was serviced, what oil it takes, how many
kilometres since the chain was cleaned, where the insurance PDF is — the app knows it,
and can produce it in under five seconds without a network connection.

That framing sets a high bar in two directions:

1. **Capture must be effortless**, or the memory will have holes. An app that knows
   nothing because logging was tedious is worse than a notebook.
2. **Recall must be trustworthy.** A remembered number that is wrong is worse than no
   number at all — especially for tyre pressures and torque specs.

Most of the design decisions below are in service of one or the other.

---

## 2. Context and constraints

| Dimension | Value |
|---|---|
| Users | One. Me. No accounts, no login, no sharing, no cloud identity. |
| Vehicles | One (Speed 400). Model the schema for N, build the UI for 1. |
| Device | **Android tablet only.** No phone build. The phone is a camera that feeds the tablet (§4.1). |
| Connectivity | Must be fully usable offline. Network only for the AI assistant. |
| AI provider | **Google Gemini** (existing API key). See §10 — this choice has real privacy consequences. |
| Knowledge source | Official Triumph owner's handbook PDF, user-imported. See §10.2. |
| Region | India — ₹, litres, km, PUC/RC/insurance, roadside fuel pumps |
| Distribution | Self-signed APK, sideloaded. No Play Store. |
| Bike telemetry | **None available.** The Speed 400 has no accessible OBD/Bluetooth data port for a phone app. Every odometer reading is entered by a human or read by the camera. This is the single biggest constraint on the whole design. |

---

## 3. Design principles

These are the rules I want to be able to point at when a feature decision gets
ambiguous later.

### P1 — The friction budget

Every capture flow has a hard tap budget. If a flow exceeds it, the flow is wrong,
not the user.

| Action | Budget |
|---|---|
| Log a fuel fill | 3 taps + 2 numbers |
| Log an arbitrary expense | 3 taps + 1 number |
| Import a photographed bill from the gallery | 2 taps to Inbox, then confirm OCR |
| Update the odometer | 1 tap + 1 number |
| Mark a reminder done | 1 tap, from the notification |

The corollary: **capture and structure are separate acts.** On a tablet-only app the
split is forced rather than chosen — the tablet is not at the petrol pump. So the
capture half happens on whatever camera is in my pocket, and the app's job is to make
the *structuring* half effortless: excellent gallery import, good OCR, sensible
defaults. See §4.1 for what this costs and §5.2 for the mechanism.

The risk this introduces: entries become **batched rather than immediate**, and
batched things get forgotten. That's why the Inbox count sits on the dashboard and
why the staleness nudges (§8.3) matter more here than they would in a phone app.

### P2 — One event log, many views

The brief lists "fuel tracking", "expense tracking", "service history", "parts
history", "ride history" and "a Bike Timeline" as six features. They are not six
features. They are **one append-only event log and five saved views over it.**

A single real-world happening produces a single event. A service visit is one event
that carries: a date, an odometer reading, a workshop, several money line items
(labour, oil, filter, consumables), several component resets (engine oil replaced →
oil interval restarts), and an attached invoice PDF. If those were separate records,
I would enter the same visit four times and the analytics would triple-count it.

This is the most important structural decision in the plan.

### P3 — The odometer is the spine

Almost every fact about a motorcycle is anchored to a `(date, odometer)` pair.
Fuel economy, service intervals, component wear, cost-per-km, warranty windows —
all of them. So:

- Every event that plausibly has an odometer reading captures one.
- The app maintains a **running estimate of today's odometer**, extrapolated from
  recent readings, so that km-based reminders can be projected onto a calendar.
- The freshness of that estimate is shown honestly, and the app asks for a reading
  when it goes stale.

### P4 — Provenance is a first-class field

Every number the app shows has a source, and the source is visible:

| Badge | Meaning |
|---|---|
| 🟢 **Manual** | From the Speed 400 owner's/service manual, with a page citation |
| 🔵 **My records** | Computed from my own logged data |
| 🟡 **Estimate** | Derived/projected by the app (e.g. today's odometer) |
| ⚪ **General** | Unverified general knowledge or community rule-of-thumb |

This applies to the AI assistant *and* to the rest of the app. A maintenance
interval sourced from a forum post must not look identical to one from the manual.

**Concrete evidence this matters:** while researching this plan, one bike-spec
aggregator listed the Speed 400 as a *349cc* engine. It is not — it is the 398cc
TR-series single. Web aggregators, dealer sites and forums routinely disagree with
each other. Grounding on the actual owner's manual is not pedantry; it is the only
way this app is worth trusting.

### P5 — Never invent a safety-critical number

Tyre pressures, torque specs, fluid capacities and grades, brake specifications,
valve clearances, electrical ratings, load limits. For these, the app either
produces a cited value from the manual or says it doesn't know. There is no
middle option, and no "approximately". A hallucinated torque spec can strip a
caliper bolt; a hallucinated tyre pressure can end badly at speed.

### P6 — The data outlives the device

This is meant to be a ten-year record on a tablet that will be replaced two or three
times.
Backup and restore is a **Phase 1** feature, not a "later" feature. If the device
dies and the data is gone, the entire premise collapses.

---

## 4. Where I'd change the brief

Taking the request to think for myself rather than transcribe the feature list.

### 4.1 Tablet-only — decided, and here's the bill

**Decision: tablet only. No phone build.** This is settled, and it's a good trade —
but it isn't free, and the plan is better for naming the cost up front.

**What it buys.** Everything that makes this app worth having is review-shaped:
reading service history, filing documents, reconciling spend, reading charts, talking
to the assistant, scrolling the timeline. All of that is better on a tablet and worse
on a phone. And critically, it **resolves the sync question** — one device of record,
no merge conflicts, no last-write-wins, no per-row vector clocks. A whole category of
complexity disappears from Phase 0.

**What it costs.** The tablet will not be at the petrol pump. Two things break:

1. **In-app camera capture at the point of sale is gone.** The original friction
   budget assumed I could shoot a bill and walk away. I can't — not with a tablet.
2. **The odometer, which is the spine of the entire data model (P3), is read off the
   dashboard of a motorcycle** that the tablet is nowhere near.

**How we pay for it.** The phone stops being a second client and becomes *a camera
that feeds the tablet*. That's a much smaller ask than a phone app:

> At the pump, photograph the cluster (odometer) and the bill with the phone's normal
> camera app. Google Photos backup carries it to the tablet within minutes, with zero
> effort and no code. That evening, the app's Inbox imports from the gallery, OCRs it,
> and pre-fills the entry.

So the engineering requirement is not "build a phone app", it's **"build an excellent
image import path"** — gallery picker, share-sheet target, drag-and-drop. Roughly two
days of work instead of a second client.

**What actually dies.** The "show your documents at a traffic stop" mode from §7.6.
I am not handing a police officer a tablet, and I won't be carrying one. DigiLocker /
mParivahan on the phone is the real answer to that use case and this app should not
pretend otherwise. Demoted — the documents feature stays, the roadside mode goes.

**What gets better.** With no phone layout to compromise for, every screen can be
genuinely tablet-native: permanent list-detail panes, landscape-first, side-by-side
charts, the PDF manual open beside the assistant in split view, drag-and-drop import,
and keyboard shortcuts for fast batch entry if a keyboard is attached. No
`WindowSizeClass` branching, no responsive compromises. Design for one screen size
class and design it properly.

> **Revisit trigger:** if after three months the Inbox is chronically backed up, that's
> evidence the batching cost is real, and a minimal phone capture companion goes on the
> Phase 5 list. Don't pre-build it.

### 4.2 Fuel and expenses are the same thing, entered once

A fuel fill is an expense. If "fuel tracking" and "expense tracking" are separate
modules, I will either enter every fill twice or my total spend will be wrong.

> **Recommendation:** money exists in exactly one place — `line_item` rows attached to
> events. A fuel event carries a line item of category `fuel`. The Fuel screen is a
> filtered view; the Expenses screen is a grouped view; the totals can never disagree
> because there is only one set of numbers.

### 4.3 Parts and accessories are mostly a *current state* question

The brief asks for "parts and accessories/modifications history". History is the easy
half and it falls out of the event log for free. The half that's actually useful is
the question history can't answer directly:

> *What is fitted to this bike right now?*

Which exhaust, which tyres and how old, which sprocket ratio, what's the current
chain, which accessories are installed vs. sitting in a box in the cupboard.

> **Recommendation:** a derived **Build Sheet** — current fitted state, computed from
> fit/remove events — alongside the history. Also track whether each modification
> affects the warranty, because that is a real and expensive thing to forget.

### 4.4 GPS ride tracking is the lowest value-per-effort item on the list

It's the most technically expensive feature here — foreground services, battery
optimisation whitelisting, location permissions, map tiles, route storage, offline
maps — and it's the one furthest from "I shouldn't have to remember anything". Google
Maps Timeline already records where I rode; Strava-likes already do this better.

There is also a correctness trap: **GPS distance is not odometer distance.** If ride
distances silently feed the odometer, the fuel-economy maths gets quietly corrupted.

> **Recommendation:** Phase 1–3 ship a *manual* ride log (date, route name, start/end
> odometer, notes, photos, companions). That covers the memory use-case — "when did we
> do that Coorg run and what did it cost?" — at ~2% of the effort. Revisit live GPS
> tracking in Phase 5, and if it lands, treat GPS as decoration and keep the odometer
> as the source of truth.

### 4.5 Analytics should answer questions, not fill a screen with charts

It's easy to build twelve charts nobody reads. Each chart should exist because it
answers a question I'd actually ask. If I can't write the question down, the chart
doesn't ship. See §11.

### 4.6 Three different "cost per km" numbers, never conflated

The brief asks for both "fuel-cost-per-km" and "cost per kilometre". These differ by
almost an order of magnitude and mixing them makes the whole analytics section
meaningless:

| Metric | Includes | Typical shape |
|---|---|---|
| **Fuel ₹/km** | Petrol only | small, stable, good for trip budgeting |
| **Running ₹/km** | Fuel + consumables + service + repairs | the honest "cost to ride" number |
| **True ₹/km** | Running + insurance + accessories + depreciation on purchase price | the "what this hobby actually costs" number |

> **Recommendation:** show all three, always labelled, never a bare "cost per km".

### 4.7 "No accounts" is right, and it hands me a problem to solve

No accounts means no server, which means no automatic backup. That's the correct
trade for a personal app — but it must be paid for deliberately (P6) with a
first-class export/restore, not ignored. See §14 (backup).

The *sync* half of this problem is now moot: the tablet-only decision (§4.1) means
there is exactly one device of record. Worth keeping UUID primary keys and row-level
`updated_at` anyway — they cost nothing today and preserve the option if a phone
companion ever lands in Phase 5.

---

## 5. What's missing from the brief

Features I'd add, roughly in order of how much I think they matter.

### 5.1 Odometer projection ⭐ (enables everything else)

Because there is no telemetry (§2), the app only knows the odometer when I tell it.
But every km-based reminder needs to know *today's* odometer to be useful. "Chain
lube due at 12,400 km" is useless if I don't know where I am now.

So: maintain a rolling km/day rate (exponentially weighted toward recent readings)
and project. This turns every km-based interval into a **date**, which is the only
form a notification can act on:

```
est_odo(today)   = last_reading + km_per_day × days_since_reading
km_remaining     = due_odo − est_odo(today)
projected_due    = today + (km_remaining / km_per_day) days
effective_due    = min(projected_due, time_based_due)
```

Two consequences worth designing for:

- **Freshness is displayed, always.** "≈ 11,240 km (estimated, last read 4 days ago)".
  Past ~14 days the estimate is marked stale and projections are downgraded.
- **A virtuous loop:** every fuel fill captures an odometer reading for free. Log fuel
  regularly and the entire reminder system stays accurate with zero extra effort. Which
  is another reason the fuel flow must be frictionless.

### 5.2 The Capture Inbox ⭐

A staging queue for photos that haven't become records yet. Given the tablet-only
decision (§4.1), this is the single most important feature in the app — it is the only
bridge between where data is created (roadside, on a phone) and where it's entered
(at home, on a tablet). If the Inbox is bad, the app has no data and nothing else
matters.

**Three ways in, all of which must work well:**

1. **Gallery import** — the primary path. A picker filtered to recent images, showing
   what's already been imported so nothing is done twice. Multi-select, because a
   month of bills gets reconciled in one sitting.
2. **Share-sheet target** — the app registers for `image/*` and `application/pdf`, so
   sharing from Google Photos, Drive, Gmail or WhatsApp lands straight in the Inbox.
   This is how an emailed insurance PDF or a WhatsApped service invoice gets in.
3. **Drag and drop** — Android tablets support it, and dropping a PDF onto the
   documents screen is the natural gesture. Cheap to add, feels native.

**What happens to an item:** OCR extracts candidates (odometer, amount, litres, rate,
date, vendor), the item is classified into a likely type, and it waits as a card
showing the photo beside a pre-filled form. Confirm, correct, or defer. Items retain
their original photo timestamp, not the import time — so a bill photographed on the
3rd and imported on the 11th is dated the 3rd.

**Why this matters beyond convenience:** it means the app degrades gracefully. Worst
case — I never reconcile anything — it's still a chronological, searchable shoebox of
dated bike photos, which is better than what most owners have. The structured data is
an upgrade on top of that floor, not a precondition for the app being useful.

### 5.3 Warranty guard ⭐

The Speed 400 ships with a 2-year warranty in India (extendable). Warranty terms
generally require scheduled services to be done on time at an authorised centre, with
proof. Missing one can cost far more than the service.

Nobody remembers this. The app should:

- Know which services are warranty-mandatory and their deadline windows
- Warn *hard* and early (60/30/7 days) — a different, louder notification class
- Refuse to mark such a service complete without an attached invoice
- Show warranty expiry on the dashboard in its final 90 days
- Flag modifications that may affect warranty coverage on the Build Sheet

### 5.4 Fault / niggle log ⭐

*"There's a rattle from the left side around 4,000 rpm — started 3rd August."*

That's not an expense, not a service, not a ride. It's an **open issue**. Bikes
accumulate niggles, and by the time the service appointment comes around I've
forgotten three of the four things I meant to mention.

- Log a symptom with date, odometer, conditions, optional audio/video/photo
- Stays **open** until closed by a service event or by me
- The service prep screen hands me the open list to read out at the counter
- Historical value is enormous: "has this happened before?" becomes answerable, and
  it makes the AI assistant dramatically more useful

### 5.5 Trip readiness check ⭐

The payoff feature — it only exists *because* everything else is tracked. One screen
that answers: **"Can I ride to Coorg tomorrow?"**

```
✅  Insurance valid until 12 Mar 2027
⚠️  PUC expires in 9 days — renew before you go
✅  Next service due in 3,100 km
⚠️  Chain lubed 780 km ago (interval 500 km)
❓  Tyre pressure last checked 22 days ago
✅  Fuel range ≈ 340 km from a full tank
📄  Documents available offline
```

Cheap to build once the data model exists, and genuinely delightful. This is the
answer to "so what?" for the whole app.

### 5.6 Tyres deserve their own model

Tyres are the one component where age matters as much as wear, where the front and
rear wear at different rates and get replaced at different times, and where the
consequence of getting it wrong is a crash. Track per-corner: fitment date, fitment
odometer, brand/model/size, DOT manufacture date, pressure check log, tread notes,
and km run. Ageing warning independent of km.

### 5.7 Workshop & vendor directory

Which service centre, which mechanic, phone number, address, what work each has done,
what it cost, and my own quality rating. Answers "who did the chain last time and were
they any good?" and "who do I call from the road?".

### 5.8 Consumables inventory

Small but real: a spare oil filter on the shelf, half a can of chain lube, a spare
bulb. Prevents double-buying and answers "do I need to order anything before the
weekend?".

### 5.9 Exportable history document

A generated PDF of the complete, chronological ownership record: services, parts,
mileage, spend. Useful at resale — a full documented history is worth actual money —
and as a human-readable backup that outlives the app itself.

### 5.10 Backfill onboarding

The bike already exists and has history. If the app starts empty, the first six months
of analytics are useless and motivation dies. The first-run flow must invite me to
enter purchase date, purchase price, purchase odometer, current odometer, and to
photograph whatever old bills I still have. Even three remembered data points make the
timeline feel real from day one.

### 5.11 Small things worth having

- **Fuel station + fuel grade on each fill.** Riders swear one pump gives better
  mileage. With enough data that becomes a testable claim rather than folklore.
- **Home-screen widget:** current odometer, next thing due, one-tap fuel log. Lower
  value on a tablet than it would be on a phone — tablet home screens get used less —
  so this drops to Phase 5 and may not be worth building at all.
- **Anomaly detection on entry** — see §9.2. Bad data is worse than no data.
- **Full-text search over everything**, including my own notes.
- **Pre-service prep screen:** open faults + due items + last service reference,
  assembled into something I can read out at the counter.

---

## 6. Data model

Sketch, not final schema. The point is to show that the six "features" collapse into
one spine.

### 6.1 The spine

```
Bike (1 row, but modelled as N)
 └── Event  ──┬── LineItem     (money — the ONLY place money lives)
              ├── Attachment   (photos, PDFs, audio)
              ├── OdometerReading
              └── ComponentAction  (what this event did to which component)
```

**Event** — every timeline entry is one of these:

| Field | Notes |
|---|---|
| `id`, `bike_id` | |
| `type` | `fuel` · `service` · `repair` · `part` · `accessory` · `document` · `ride` · `fault` · `note` · `odo_reading` · `purchase` |
| `occurred_at` | date + optional time |
| `odometer_km` | nullable, but prompted for on every type that plausibly has one |
| `title`, `notes` | |
| `vendor_id` | workshop / petrol pump / shop, nullable |
| `location` | optional |
| `created_at`, `updated_at` | |

**LineItem** — `event_id`, `category`, `description`, `qty`, `unit_price`, `amount`,
`is_estimate`. Categories: `fuel`, `labour`, `parts`, `consumables`, `accessories`,
`insurance`, `puc`, `rto`, `washing`, `parking`, `tolls`, `gear`, `other`.

> **Invariant:** no total is ever computed by summing events. Every money figure in
> the app is a `SUM(line_item.amount)` over a filter. This makes double-counting
> structurally impossible.

**FuelEntry** — a facet of a `fuel` event, not a separate thing:
`event_id`, `litres`, `price_per_litre`, `amount`, `fill_type`
(`full` | `partial` | `first`), `missed_previous` (bool), `station_id`,
`fuel_grade`, `is_computed_litres`.

**Component** — `key` (`engine_oil`, `oil_filter`, `air_filter`, `chain`,
`sprockets`, `brake_pads_front`, `brake_pads_rear`, `brake_fluid`, `coolant`,
`spark_plug`, `tyre_front`, `tyre_rear`, `battery`, `clutch_cable`, `valve_clearance`,
`chain_lube`, `tyre_pressure_check`, …), display name, `interval_km`, `interval_days`,
`interval_source` (`manual` | `dealer` | `community` | `mine`), `manual_page_ref`,
`action_kind` (`replace` | `service` | `check` | `adjust`), `is_warranty_relevant`.

**ComponentAction** — `event_id`, `component_key`, `action` (`replaced` | `serviced` |
`checked` | `adjusted` | `topped_up`), `part_used`, `notes`. This is what resets an
interval, and it's why a service visit is one event rather than seven.

**Document** — `event_id`, `doc_type` (`insurance` | `puc` | `rc` | `licence` |
`warranty` | `invoice` | `service_plan` | `rsa` | `loan` | `other`), `issuer`,
`number`, `issued_on`, `expires_on`, `secondary_expires_on`, `amount`, `file_uri`.

> `secondary_expires_on` exists because Indian new-vehicle insurance commonly bundles
> a multi-year third-party cover with an annually-renewed own-damage cover. One expiry
> field would silently produce a wrong reminder for the one that matters. *(Verify the
> exact structure against my own policy document during Phase 2.)*

**Reminder** — `component_key` or `document_id`, `rule_type` (`km` | `time` |
`whichever_first`), computed `due_odo` / `due_date`, `severity`, `snoozed_until`,
`last_notified_at`.

**Others:** `Vendor`, `Ride`, `Fault`, `InventoryItem`, `Attachment`, `CaptureInbox`,
`Fact` (see §10), `Setting`.

### 6.2 Why this shape

- A service visit = 1 Event + N LineItems + N ComponentActions + 1 Attachment.
  Entered once, appears correctly in Timeline, Expenses, Service History, Maintenance
  state and Analytics.
- The Timeline is `SELECT * FROM event ORDER BY occurred_at DESC`. It cannot drift out
  of sync with the other screens, because it *is* the other screens.
- Adding a second bike later is a `WHERE bike_id = ?`.

---

## 7. Screens & experience

### 7.1 Home / Dashboard

Ordered by what I actually need when I open the app:

1. **Quick actions** — a persistent row, always reachable:
   `⛽ Fuel` · `₹ Expense` · `📷 Capture` · `🔢 Odo`. Never more than one tap away.
2. **Bike card** — photo, registration, estimated odometer with freshness, days owned.
3. **Due next** — the three most urgent items across services, documents and
   components. Each shows both distances: *"in 640 km · ~11 days"*. Overdue in red.
4. **Pulse** — this month's spend, rolling mileage (km/L), fuel ₹/km, km ridden.
   Four numbers, each with a small trend arrow against the previous period.
5. **Recent activity** — last five timeline events.
6. **Nudges** — data-quality prompts: *"No odometer reading in 16 days — projections
   are getting stale."*, *"3 items waiting in your Inbox."*

On tablet this becomes a three-pane layout: nav rail · dashboard column · detail pane,
so tapping a due item opens it beside the dashboard rather than navigating away.

### 7.2 Fuel

The single most-used screen. India-specific insight that most fuel trackers get
backwards: **at an Indian pump you transact in rupees, not litres.** You say
"₹500 ka" or "full tank" — the pump displays the rate and the amount. Litres are the
derived quantity.

So the entry form is:

```
Odometer      [ 11,240 ]  ← camera OCR offered
Amount ₹      [    500 ]
Rate ₹/L      [ 106.42 ]  ← defaulted from last fill, editable
Fill type     ( Full ) ( Partial )
                                        → 4.70 L computed, shown live
Station       [ HP, Hunsur Road ▾ ]     ← last-used default
```

Two numbers and a toggle. Everything else is defaulted or derived. Litres can be
entered directly instead if the bill shows them.

The list below shows each fill with its computed tank mileage, the rolling average,
and a clear marker on any tank excluded from the mileage calculation and why.

### 7.3 Expenses

Grouped by month with category chips. Two views: *by category* (where does the money
go) and *chronological*. Filters by category, date range, vendor, amount. Every row
opens its parent event, so an expense is never an orphan.

### 7.4 Maintenance

The health screen. A card per component:

```
Engine oil                              🟢 Manual
Last replaced   8,200 km · 14 Feb 2026 · Triumph Mysuru
Interval        16,000 km / 12 months (whichever first)
Now             ≈ 11,240 km
Remaining       4,960 km  ·  ~86 days     [████████░░ 31% used]
                                          [ Log service ]
```

Sorted by urgency. Split into *Due soon* / *Healthy* / *Not tracked*. Each card shows
its interval **provenance badge** — a manual-sourced interval must not look like a
number I made up. Supports both workshop work and DIY, with a "who did it" field,
because DIY changes what the record needs (part used, my own notes) versus a workshop
visit (invoice, service advisor).

Frequent light-touch items (chain lube ~500 km, tyre pressure ~weekly) are
`action_kind = check` and log with a single tap — no form.

**Note on intervals:** the component catalogue ships with intervals marked
`source = unverified` and a setup task to confirm every one against the owner's
manual. The headline figures I have from public sources — first service at 1,000 km,
then 16,000 km / 12 months, 2-year warranty — are corroborated but *not* manual-sourced,
and the detailed per-item schedule (valve clearance, brake fluid, coolant, spark plug,
oil grade and capacity) must come from the manual before the app asserts any of it.

### 7.5 Service history

Reverse-chronological service events, each expanding to the full record: workshop,
odometer, line items, components touched, the invoice, and my own notes on how it
went. A per-visit total and a running lifetime service total.

### 7.6 Documents

Grid of cards with a prominent expiry state (valid / expiring / expired). Each stores
the file, the metadata, the premium paid (which flows into expenses automatically),
and renewal history.

**Cut: the roadside "show at a checkpoint" mode.** It was in the first draft of this
plan and the tablet-only decision kills it (§4.1) — I won't be carrying the tablet, and
handing one to a traffic officer isn't a thing. In India the legally-accepted digital
route is **DigiLocker / mParivahan on the phone**, and that's the right tool for that
job. This app's documents feature is for the *complete* file set — warranty card,
service plan, old invoices, past policies, purchase paperwork — which is an archive
problem, not a roadside problem. Being clear about the boundary matters so I don't
rely on the wrong app at the wrong moment.

**What the archive does need:** expiry tracking that feeds reminders (§8), the premium
amount flowing into expenses, renewal chains (this year's policy linked to last
year's), and export so the whole set can be handed over at resale.

### 7.7 Timeline

Everything, chronologically, with type filters and full-text search. The "what
happened to this bike" view. Jump-to-date, and a distance axis so I can see the
spacing between events in kilometres as well as time.

### 7.8 Analytics

See §11 — deliberately small.

### 7.9 Assistant

See §10.

### 7.10 Build sheet

Current fitted state, derived. What's on the bike, when it went on, what it cost,
what it replaced, whether it's warranty-relevant, and what's in the box in the
cupboard.

### 7.11 Design language

The bike is a modern classic and I already write in an instrument-panel idiom. Lean
into it: dark-first theme, monospaced tabular numerals for all readings (odometer,
mileage, money), a restrained accent drawn from the bike's own colour, generous
touch targets sized for use with gloves on, and high-contrast type that survives
direct sunlight at a petrol pump. Material 3 with a personality, not a Material 3
demo.

---

## 8. The reminder engine

The part that delivers "I don't have to remember anything". It has to be right, or I
will stop trusting it — and a distrusted reminder system is worse than none.

### 8.1 Rule types

| Type | Example | Logic |
|---|---|---|
| Time only | PUC expiry, insurance renewal | fixed date |
| Distance only | chain lube every 500 km | `due_odo = last_odo + interval` |
| Whichever first | engine oil: 16,000 km **or** 12 months | `min(projected_km_due, time_due)` |
| Age | tyres, battery, brake fluid | date of fitment + years |
| Conditional | warranty-mandatory service | hard deadline, escalated severity |

Distance rules are projected onto dates using §5.1 so that a notification can fire on
a day. All four are then a single sorted list of `(due_date, severity)`.

### 8.2 Notification policy

Under-notifying breaks the promise; over-notifying trains me to swipe them away, which
also breaks the promise. So:

- **Documents:** 30 / 7 / 1 days before expiry, then daily once expired.
- **Warranty-mandatory service:** 60 / 30 / 7 days, in a separate high-priority channel.
- **Routine service:** at 1,000 km remaining, then 300 km remaining.
- **Light checks** (chain lube, tyre pressure): a single weekly digest, never
  individually. These are the ones that would otherwise cause notification fatigue.
- **Actionable from the shade:** `Done` · `Snooze 7d` · `Log it`. Marking done from
  the notification writes a real event and resets the interval.
- One daily recompute via `WorkManager`, plus recompute on every write.

### 8.3 The staleness problem

Distance-based reminders silently rot if I stop logging. Mitigations:

1. Every fuel fill refreshes the odometer for free (§5.1).
2. If no reading in 14 days, the dashboard shows a nudge and projections are labelled
   stale rather than shown as confident.
3. If no reading in 30 days, a single low-priority notification asks for one.
4. The estimate is never silently written to the database as if it were a real
   reading. Estimates and observations are different kinds of fact (P4).

---

## 9. The fuel & money engine

Two calculations that most tracker apps get subtly wrong. Both deserve unit tests
before they deserve UI.

### 9.1 Fuel economy — full-to-full only

A single tank's mileage is only meaningful between two **full** fills. Partial fills
in between must be accumulated, not treated as data points.

```
For each full fill Fi with previous full fill F(i-1):
    km     = odo(Fi) − odo(F(i−1))
    litres = Σ litres of every fill after F(i−1) up to and including Fi
    kmpl   = km / litres

Rules:
  · The first fill establishes a baseline and yields no kmpl.
  · A fill flagged missed_previous breaks the chain; the next full fill
    starts a new baseline instead of producing a wrong number.
  · A partial fill never produces a kmpl on its own.
```

The headline number on the dashboard is a **rolling average over the last 5 full-tank
spans**, not the most recent tank — single-tank figures are noisy enough (traffic,
pillion, weather, how full "full" was) to be misleading on their own. The per-tank
series still gets plotted, because its variance is itself informative.

### 9.2 Entry-time validation

Bad data poisons every chart downstream, and it's far easier to catch at entry than to
find six months later. On save, check:

- Odometer went **backwards** → block, ask to correct.
- Odometer jumped implausibly (> ~1,500 km since last reading) → confirm.
- Litres > 13 L (tank capacity) → confirm, likely a typo or a jerrycan.
- Computed kmpl deviates > 40% from the rolling average → ask *why*, offering:
  "I missed logging a fill" · "partial fill" · "typo" · "genuinely different riding".
  This one question is what keeps the mileage chart honest for ten years.
- Rate ₹/L wildly off the last known → confirm.

Every one of these is a *question*, not a rejection. The app should never refuse data
it doesn't understand; it should record it and flag it.

### 9.3 The three cost-per-km numbers

Per §4.6, computed over an explicit window and always labelled:

```
fuel_per_km    = Σ fuel amount            / Δodo
running_per_km = Σ (fuel+consumables
                    +service+repairs)     / Δodo
true_per_km    = Σ everything
                 + depreciation estimate  / total odo since purchase
```

Depreciation is an **estimate** (P4, 🟡) driven by a purchase price and a
user-adjustable current-value guess. It is by far the largest component of true cost
in early ownership and omitting it would make the number a comfortable lie.

---

## 10. The AI assistant

The most interesting part, and the part most likely to be built badly. The key insight
is that the brief describes **two completely different kinds of question** that need
two completely different mechanisms:

| | Question about **the model** | Question about **my bike** |
|---|---|---|
| Example | "What's the rear tyre pressure?" | "How much have I spent on fuel this year?" |
| Example | "What does the amber engine light mean?" | "When did I last replace the chain?" |
| Truth lives in | The owner's / service manual | My SQLite database |
| Mechanism | **RAG with citations** | **Tool calls returning SQL results** |
| Failure mode | Confident wrong spec → mechanical damage | Confident wrong number → bad decisions |

Conflating these — in particular, RAG-ing over my own records — is the classic mistake
and it produces answers that are approximately right, which for money and maintenance
is the same as wrong.

### 10.1 Architecture

```
              ┌──────────────────────────────┐
   question → │  Router (LLM w/ tool schema) │
              └──────┬────────────────┬──────┘
                     │                │
      ┌──────────────▼──┐      ┌──────▼─────────────────┐
      │ Knowledge tools │      │ Record tools           │
      │ spec_lookup()   │      │ sum_expenses()         │
      │  → chunk + page │      │ last_event()           │
      │ fact_lookup()   │      │ km_since()             │
      │  → curated fact │      │ fuel_economy()         │
      └────────┬────────┘      │ current_odometer()     │
               │               │ due_items()            │
       manual corpus           │ service_history()      │
       (local vectors +        │ find_documents()       │
        FTS, page-cited)       │ search_notes()         │
                               └──────┬─────────────────┘
                                      │  typed SQL, deterministic
              ┌───────────────────────▼──────┐
              │ Composer + numeric grounding │  ← on-device for
              │ check → answer with badges   │    record answers (§10.6)
              └──────────────────────────────┘
```

Only the **Router** step crosses the network. Everything below it runs on the tablet —
which, per §10.6, is what keeps my financial history out of Google's training data.

Many real questions need both sides. *"Should I change my oil?"* =
`spec_lookup("engine oil interval")` (🟢 manual) + `last_event("engine_oil")` (🔵
records) + `current_odometer()` (🟡 estimate) → a synthesised answer whose every
component is attributed.

### 10.2 The knowledge corpus — and where the manual comes from

**The manual exists and is freely available.** Triumph publishes the official
*Owner's Handbook — Speed 400 and Scrambler 400 X* (UK English, Sept 2023) as a PDF,
and it's mirrored in several places. Confirmed reachable sources:

| Source | Notes |
|---|---|
| Team-BHP forum attachment | Direct PDF of the official handbook — `…owners-handbook-uk-english-09-2023.pdf` |
| ManualsLib — *Triumph Speed 400 Owner's Handbook* | Free PDF, ~5 MB, also a 2023-model edition |
| World of Triumph — Owners Handbooks | Official-channel handbook downloads |

Full URLs are in the Appendix.

> ⚠️ **I could not download it in this session** — the sandbox this plan was written in
> has a restrictive network egress policy and blocked every one of those hosts (403 at
> the proxy). This is a sandbox limitation, not a sourcing problem: the file is public
> and takes about thirty seconds to fetch on a normal connection. **Phase 0 task:**
> download it, and — since I don't have a physical manual — treat this PDF as the
> authoritative source for every 🟢 badge in the app.

Note also that this is the **owner's handbook**, not the workshop/service manual. It
will cover tyre pressures, fluid specs, service intervals, warning lights and routine
checks — which is 90% of what the assistant needs. It will *not* cover torque specs
and teardown procedures. So the safety rule (§10.5) will legitimately refuse some
questions, and that refusal is correct behaviour, not a bug. If a service manual turns
up later, it drops into the same corpus.

**Handling:**

- **Imported, never committed.** The manual is copyrighted. It's my personal copy,
  imported into app storage on my device. It is never bundled into the APK and never
  committed to this repository.
- **Retrieval is small.** A ~150-page handbook is a few hundred chunks. Brute-force
  cosine similarity over a few hundred vectors held in memory is instantaneous — no
  vector database, no dependency, no excuse for one. Combine with SQLite FTS5 for
  keyword recall, since part numbers and warning-light names are exact-match problems
  rather than semantic ones. Hybrid retrieval, trivially.
- **Embeddings** come from Gemini's embedding model at import time (a one-off network
  call for a few hundred chunks), then live locally forever. Retrieval itself is
  offline.
- **Every returned chunk carries its page number**, and citations render as
  "Owner's Handbook, p. 84" — which I can then check against the real PDF in split
  view.

> **Tempting shortcut, deliberately rejected:** Gemini's context window is large enough
> to hold the entire handbook, so the assistant could skip retrieval and paste the whole
> manual into every request. That works, and it's genuinely simpler. But it sends the
> full document on every question, costs far more tokens, and — crucially — produces
> answers with no citation anchor, which breaks the provenance guarantee (P4) that the
> whole assistant design rests on. Retrieval stays.

### 10.3 The curated fact table

Separately from RAG, a small hand-verified `facts` table (~50 rows): tyre pressures,
oil grade and capacity, coolant spec, key torque values, bulb types, fuse ratings,
service intervals, fluid capacities. Each row: `key`, `value`, `unit`, `source`,
`page_ref`, `verified_on`.

Three jobs for one small table:

1. Powers a **Quick Specs** screen — no AI, no network, instant.
2. Provides **offline answers** to the most common questions when there's no signal —
   which, on a road trip, is exactly when I need the tyre pressure.
3. Acts as the authority for §10.5's safety rule.

**Seeding it:** rather than typing fifty rows by hand, do a one-off extraction pass —
feed the handbook PDF to Gemini with a JSON schema and have it pull out every
specification with its page number. Then **verify each row against the PDF manually
before marking it `verified`.** The extraction saves the typing; it does not save the
checking. Given P5, an unverified row in this table is worse than a missing one — a
missing row produces an honest "I don't know", a wrong row produces a confident lie
about a tyre pressure. Rows start as `unverified` and are promoted only by my own eyes
on the page.

### 10.4 The numeric grounding check

A concrete guardrail, cheap to implement, that directly serves P5:

> After the model composes an answer, extract every numeral in it. Any numeral that
> does not appear in a tool result or a cited manual chunk is flagged. If the answer
> is about a safety-critical topic, the answer is **blocked** and regenerated or
> refused. Otherwise it is downgraded to ⚪ *General, unverified*.

This is a deterministic post-check, not a prompt instruction — prompts alone will not
reliably stop a model from producing a plausible-looking torque figure.

### 10.5 The safety rule

Safety-critical topics: tyre pressures, torque specifications, fluid capacities and
grades, brake specifications, valve clearances, electrical ratings, load limits,
tyre sizes and speed ratings.

For these, the assistant answers **only** from the fact table or a cited manual chunk.
If neither has it, the answer is: *"Not in the manual I've indexed — check the printed
manual or the dealer."* It never estimates, never says "typically around", never
reasons from other motorcycles.

Everything else — DIY procedures, approximate costs, general advice, "is this rattle
normal" — is permitted with a clear ⚪ badge and, where relevant, a "verify before
acting" note.

### 10.6 Gemini — model choice, and a privacy finding that changes the design

**Provider: Google Gemini**, using the existing API key. Good fit — the Flash tier is
cheap-to-free, fast, multimodal, has a large context window and supports function
calling and JSON-schema structured output, which is exactly the shape of §10.1.

**Two things to know before building:**

**1. Pin the model in config, not in code.** Google retires models on a schedule — the
Gemini 2.5 series (Pro, Flash, Flash-Lite) is reported to reach end of life on
**16 October 2026**, about six weeks from this writing, with Flash users migrating to
the next Flash generation. Use the current **Flash-tier** model (Pro is overkill here
and is no longer on the free tier), keep the model ID a settings value, and verify the
exact current ID in AI Studio at build time rather than trusting any ID written down
here — I could not reach Google's official docs from this sandbox to confirm the
current lineup, and the third-party sources that were reachable disagreed with each
other about version numbers. Treat every model ID as unverified until checked. Same
discipline as P4, applied to my own tooling.

**2. ⚠️ The free tier trains on your data.** This is the finding that changes a design
decision. Google's free tier (AI Studio / free-tier API) reserves the right to use
prompts *and* responses to improve its models, including human review and annotation.
The paid tier does not. There's a regional carve-out that applies the paid-tier policy
to free usage in the EEA, Switzerland and the UK — **India is not covered by it.**

So on the free tier, in this app's naive form, my fuel spend, service costs, workshop
names and ownership history become Google training data. That's a bad trade for a
personal record I intend to keep for a decade.

**The fix — plan on the server, compute and compose on the device.**

The original design said "only tool results leave the device, never the raw database".
Gemini's economics let us go considerably further, because record answers are
*structured*:

```
  Question: "how much have I spent on fuel this year?"

  ┌─ SENT TO GEMINI ─────────────────────────────┐
  │  the question text                            │
  │  the tool schema (names + parameter types)    │
  └───────────────────────────────────────────────┘
                     ↓
        returns: sum_expenses(category="fuel",
                              from="2026-01-01")
                     ↓
  ┌─ STAYS ON DEVICE ────────────────────────────┐
  │  SQL executes locally           → ₹18,430    │
  │  local template renders the sentence          │
  │  "You've spent ₹18,430 on fuel in 2026        │
  │   across 34 fills."                           │
  └───────────────────────────────────────────────┘
```

**The model plans; the device computes and writes the answer.** For the large majority
of record questions the numbers never leave the tablet at all — only the question text
and a static tool schema do. It's faster (one round trip instead of two), cheaper, it
works from a template so the numbers cannot be garbled in transit, and it makes the
free tier's training policy a non-issue for the data that actually matters.

Three consequences worth accepting:

- **Answers are templated, not conversational**, for record questions. Fine — for
  "how much did I spend", a crisp templated sentence is *better* than a chatty one.
- **Genuinely open-ended record questions** ("summarise how my ownership costs have
  changed") do need the numbers sent. Those get an explicit, per-question opt-in —
  a visible "this will send your figures to Google" confirmation — rather than
  happening silently.
- **Manual questions are unaffected.** Retrieved handbook chunks contain no personal
  data; sending them is harmless.

> **Recommendation:** build the plan-then-render path first, and enable billing on the
> key anyway. Usage here is a handful of requests a day; the paid tier will cost
> approximately nothing and removes the training question entirely. Belt and braces —
> cheap insurance for a ten-year personal archive.

**Bonus: Gemini vision for OCR.** Since the Inbox (§5.2) is now the app's critical
path, it's worth noting that Gemini's multimodal input plus JSON-schema structured
output can read a crumpled Indian petrol bill far better than on-device text
recognition plus hand-written regex. Trade-off: it sends the photo to Google and needs
network. **Recommendation:** on-device ML Kit as the default fast path, Gemini vision
as an explicit "couldn't read this — try harder?" fallback button. Best of both, and
the user is always the one who decides the photo gets uploaded.

**The key** lives in encrypted local storage, entered once in settings. It is never
committed anywhere, never hardcoded, and not in this repository.

### 10.7 Worth being honest about

The assistant is the highest-risk, highest-effort component and it is **not** where
the app's value comes from. Everything in §5 works with no AI at all. The assistant
is an accelerant on a good dataset, which is exactly why it belongs in Phase 4 and not
Phase 1 — it has nothing useful to say until there are records to say it about.

---

## 11. Analytics that earn their place

Each visualisation must answer a question I would actually ask. If I can't write the
question, it doesn't ship.

| Question | View |
|---|---|
| What has this bike cost me in total? | Lifetime spend, split by category, with true ₹/km |
| Where does the money actually go? | Category donut + ranked list, per year |
| Is my mileage getting worse? | km/L per full tank, with a rolling-5 trend line |
| Is my mileage worse because of *me* or the bike? | Mileage vs. avg km/day for the same period, overlaid |
| Am I riding more or less? | km per month, bar |
| What's my real running cost? | Three ₹/km numbers side by side, current vs previous year |
| What does servicing cost me per year? | Service + parts spend by year |
| Which month is expensive and why? | Monthly spend with the top three line items surfaced |
| How far do I get on a tank? | Distribution of tank ranges — sets realistic trip planning |

Deliberately **not** building: fuel-price-vs-time charts (I don't control it), pace or
speed analytics (not the point), goal-setting or gamification, comparisons against
other riders (no other riders — no accounts).

Every chart states its window and gets a one-line plain-language takeaway underneath,
because a chart I have to interpret from scratch each time is a chart I'll stop
opening.

---

## 12. Non-goals

Saying no explicitly, so scope creep has something to bounce off:

- ❌ Accounts, login, users, sharing, social feeds, leaderboards
- ❌ A phone build (§4.1) — the phone is a camera, not a client
- ❌ Multi-device sync (follows from tablet-only; revisit only if §4.1's trigger fires)
- ❌ Multi-user or "my riding group" features
- ❌ Play Store release, ASO, monetisation, subscriptions
- ❌ A backend server of any kind
- ❌ Analytics SDKs, crash reporting, telemetry — nothing phones home
- ❌ Supporting every motorcycle. This is a Speed 400 app; the schema is generic, the
  knowledge base and defaults are not.
- ❌ OBD / hardware integration (§2 — not available)
- ❌ Community fuel prices, dealer locators, marketplace, parts shopping
- ❌ Replacing DigiLocker/mParivahan for legal document production (§7.6)

---

## 13. Priorities

| Priority | Items |
|---|---|
| **Must have** | Odometer + projection · fuel logging (₹-first) · expenses · timeline · dashboard · backup & restore · service records · maintenance intervals & reminders · documents with expiry · capture inbox |
| **Should have** | Analytics (§11) · warranty guard · fault log · trip readiness · attachments/OCR · full-text search · build sheet · tyre tracking · export to PDF |
| **Could have** | AI assistant · vendor directory · inventory · home-screen widget · manual ride log · quick specs screen |
| **Won't have (this version)** | GPS ride tracking · multi-device sync · anything in §12 |

The ordering principle: **the value of this app compounds with elapsed time.** A crude
version shipped in three weeks that starts capturing data is worth more than a polished
version shipped in four months, because the first one has three months of history by
the time the second one launches. Ship the logbook early; build everything else while
it fills up.

---

## 14. Technical approach

Proposal, open to revision at Phase 0.

| Layer | Choice | Why |
|---|---|---|
| Language / UI | Kotlin + Jetpack Compose, Material 3 | Native; the strongest tablet story on Android |
| Layout | Fixed two/three-pane list-detail, landscape-first | Tablet-only (§4.1) — no `WindowSizeClass` branching, no responsive compromises |
| Tablet extras | Drag-and-drop import, keyboard shortcuts, split-screen friendly | The Inbox and the manual-beside-assistant flows depend on these |
| Database | Room (SQLite), single source of truth | Offline-first, relational — this data is deeply relational |
| Search | SQLite FTS5 | Free, fast, offline, no dependency |
| Vectors | In-memory float arrays + cosine | A few hundred chunks; a vector DB would be absurd here |
| Background | `WorkManager` daily recompute + notification channels | Reliable across Android's battery restrictions |
| Image import | Photo Picker, share-sheet intent filters, drag-and-drop | The Inbox is the app's critical path (§5.2) — no in-app camera needed |
| OCR | ML Kit text recognition on-device, Gemini vision as opt-in fallback | Fast and private by default; accurate when it matters (§10.6) |
| PDF | `PdfRenderer` to view; PDFBox-Android to extract handbook text at import | |
| AI | Gemini SDK — Flash-tier model, **ID kept in settings** | Retirement cycles are real (§10.6); never hardcode a model ID |
| Embeddings | Gemini embedding model at import; vectors stored locally | One-off network call, then retrieval is offline forever |
| Charts | Vico, or hand-rolled Compose Canvas | Few enough charts to consider drawing them |
| DI | Hilt | |
| Files | App-private storage; SAF for import/export | |
| Security | Biometric app lock; encrypted prefs for the API key; consider SQLCipher | Documents and spend history are personal |
| Testing | JUnit on the fuel-economy, interval and cost engines | The maths is the part that must be right (§9) |
| Build | Gradle + GitHub Actions producing a signed APK artifact | Sideload; no store |

**Rejected alternatives.** A PWA or Flutter would be faster to start, but reliable
scheduled local notifications, share-sheet registration, background work and SAF file
handling are the load-bearing parts of "I don't have to remember anything" — and those
are exactly where cross-platform layers are weakest. Native is the right call for an
app that will only ever run on one Android tablet.

**Backup (P6), Phase 1, non-negotiable:**
- One-tap export → a single encrypted archive (SQLite dump + attachments + manifest)
- Restore from that archive on a fresh install
- Weekly automatic export to a user-chosen folder / Drive via SAF
- A plain JSON/CSV export alongside it, so the data is never trapped in my own format
- A restore is verified at least once before the app is trusted with real history

---

## 15. Implementation phases

Each phase ends with something genuinely usable. No phase is a scaffolding-only phase.

### Phase 0 — Decide and set up *(~1 weekend)*
- **Download the owner's handbook PDF** (§10.2, Appendix) — everything 🟢 depends on it
- Read its maintenance schedule and transcribe the real intervals into the component
  catalogue, replacing the *unverified* placeholders
- Seed the `facts` table from the handbook and **verify every row by eye** (§10.3)
- Answer the remaining §16 questions
- Project skeleton, Room schema, navigation, theme
- Backfill onboarding: purchase details, current odometer, old bills photographed
- **Done when:** the app opens, knows the bike exists, and every interval in it traces
  to a page number in the handbook

### Phase 1 — The logbook *(~2–3 weekends)* — **ship this and start using it**
- Odometer readings + km/day projection engine
- Fuel logging (₹-first) + full-to-full economy engine + entry validation
- Expenses with categories and line items
- Timeline (all events, filterable)
- Dashboard v1: quick actions, bike card, pulse, recent activity
- Capture Inbox: gallery import + share-sheet target + drag-and-drop (§5.2) —
  promoted in importance now that it's the app's only route in
- **Backup / restore / export**
- **Done when:** I stop using anything else to record fuel and spending

### Phase 2 — Memory *(~3 weekends)*
- Components, intervals, ComponentActions, DIY vs workshop
- Service records with line items and attached invoices
- The reminder engine, notification policy, notification actions
- Documents with expiry, offline "show at a checkpoint" mode
- Warranty guard
- Fault / niggle log
- **Done when:** I stop worrying about missing a renewal or a service

### Phase 3 — Understanding *(~2 weekends)*
- Analytics (§11) with the three ₹/km numbers
- Full-text search across everything
- Build sheet, tyre panel
- Trip readiness check
- Export a complete history PDF
- Tablet multi-pane polish
- **Done when:** I can answer any question about the bike's past in under a minute

### Phase 4 — The assistant *(~3 weekends)*
- Handbook import, chunking, Gemini embeddings, page-cited hybrid retrieval
- Record tools over SQLite, typed and deterministic
- Gemini router with function calling; **on-device composition** for record answers
  (§10.6) so the numbers never leave the tablet
- Numeric grounding check + safety rule
- Offline fact-table fallback + Quick Specs screen
- Split-view: handbook PDF open beside the assistant, so a citation is one glance away
- **Done when:** I ask it something instead of opening the handbook, and trust the answer

### Phase 5 — Long tail *(ongoing)*
- Home-screen widget · vendor directory · inventory · manual ride log
- Reconsider GPS ride tracking on the evidence of whether I miss it
- Revisit sync if the two-device answer in §16 turned out to be "both"

---

## 16. Open questions

### Resolved

| Question | Answer | What it changed |
|---|---|---|
| One device or two? | **Tablet only** | Sync complexity deleted; capture moves to phone-camera-plus-import; roadside document mode cut. §4.1, §5.2, §7.6 |
| Owner's manual? | **Don't have it — sourced from the web** | It's freely available (§10.2). Phase 0 gains a download-and-transcribe task. Note it's the *owner's handbook*, not the workshop manual, so torque specs will be out of scope for 🟢 answers |
| Which LLM? | **Gemini** (existing key) | Rewrote §10.6. Surfaced the free-tier training issue and the plan-then-render fix; opened up vision OCR as an Inbox fallback |

### Still open

1. **What history exists to backfill?** Purchase date, purchase price, current
   odometer, and any past service invoices — paper or digital. This determines whether
   analytics are useful on day one or in six months. Needed at Phase 0.
2. **Do you do your own maintenance?** Chain cleaning, oil changes, brake pads — or is
   everything dealer-done? Heavy DIY shifts weight toward procedures, parts inventory
   and torque specs (and makes the missing workshop manual a real gap); dealer-only
   shifts it toward invoices, warranty and cost tracking.
3. **Will you enable billing on the Gemini key?** Recommended (§10.6) — usage will cost
   pennies and it removes the training-data question entirely. If you'd rather stay on
   the free tier, the plan-then-render design already covers the important case; you'd
   just lose the open-ended record questions.
4. **Do you want ride tracking at all,** or is a manual trip log enough? Still the
   single biggest effort swing in the plan.
5. **How much time per week** do you want to put into this? It changes whether Phase 1
   is three weeks or three months, and whether Phases 4–5 are realistic at all.

---

## 17. Risks

| Risk | Severity | Mitigation |
|---|---|---|
| **Logging fatigue** — the app dies of neglect | Critical | The friction budget (P1), the Capture Inbox, ₹-first fuel entry. Treat any flow over budget as a bug. |
| **Batching lag** — tablet-only means entry is deferred, and deferred things get dropped | High | Inbox count on the dashboard, weekly reconcile nudge, photo-timestamp preservation. If it stays backed up for three months, build the phone capture companion (§4.1) |
| **Personal records become training data** | High | Plan-then-render keeps figures on-device (§10.6); enable billing; explicit opt-in for anything that sends numbers |
| **Hardcoded Gemini model ID breaks on a retirement date** | Medium | Model ID is a settings value, not a constant (§14) |
| **Data loss** — one device, ten years of records | Critical | Backup in Phase 1 (P6), verified restore, plain-format export |
| **Hallucinated safety spec** | Critical | Verified-only rule (P5), numeric grounding check (§10.4), refusal over estimation |
| **Bad data poisoning analytics** | High | Entry-time validation (§9.2), full-to-full economy (§9.1), anomalies flagged not silently absorbed |
| **Stale odometer breaking reminders** | High | Fuel-fill piggyback, staleness nudges, estimates never written as observations (§8.3) |
| **Scope creep** — this plan is already large | High | Ship Phase 1 and *use it* before building Phase 2. Non-goals (§12) are load-bearing. |
| **Unverified intervals treated as gospel** | Medium | `interval_source` on every component, provenance badges everywhere (P4) |
| **The assistant becomes the project** | Medium | It's Phase 4 for a reason. Everything else works without it. |
| **Android background restrictions killing reminders** | Medium | WorkManager, exact alarms only where justified, battery-optimisation exemption prompt during onboarding, test on the actual device |

---

## 18. Success criteria

Not download counts. These:

1. I log **every** fuel fill for three consecutive months without it feeling like a chore.
2. I never again miss an insurance or PUC renewal.
3. When a mechanic asks "when did you last change the oil?", I answer in ten seconds
   with an odometer reading and an invoice.
4. I can say what this motorcycle has actually cost me, per kilometre, with a straight
   face.
5. Before a long ride, one screen tells me whether the bike and its paperwork are ready.
6. A year in, the timeline is something I enjoy scrolling through.

---

## Appendix — sources

### The owner's handbook (Phase 0: download one of these)

The official *Owner's Handbook — Speed 400 and Scrambler 400 X* (UK English,
09/2023). **This is the app's authoritative source** — the thing every 🟢 badge
points at. I could not download it from the sandbox this plan was written in (network
egress policy blocked all three hosts), so this is a manual Phase 0 step.

**Preferred — Triumph's own technical-information service:**

```
https://api.triumphtechnicalinformation.com/handbooks/documents/698361316236d0957f547eca/pdf
```

This is a first-party Triumph endpoint, which makes it a better provenance anchor than
any mirror — the app can cite the official document ID rather than "some PDF from a
forum". Use this one if it resolves.

**Mirrors, if the above needs auth or a browser session:**

- [Team-BHP forum — direct PDF of the official handbook](https://www.team-bhp.com/forum/attachments/motorbikes/2604894d1715434748-triumph-speed-400-review-triumph-speed-400-scrambler-400-x-owners-handbook-uk-english-09-2023.pdf)
- [ManualsLib — Triumph Speed 400 Owner's Handbook](https://www.manualslib.com/manual/3346108/Triumph-Speed-400.html) (~5 MB)
- [ManualsLib — Triumph Speed 400 (2023) Owner's Handbook](https://www.manualslib.com/manual/3443037/Triumph-Speed-400-2023.html)
- [World of Triumph — Owners Handbooks](https://www.worldoftriumph.com/pages/owners-handbooks)

> **Known issue, carried forward:** an attempt to upload this PDF into the planning
> session produced a **1-page, 72 KB file containing a single raster image and no text
> layer** — almost certainly a rendered preview or an auth-wall page rather than the
> handbook itself. If the same thing happens again, the download needs a real browser
> session against Triumph's site rather than a direct GET. Verify any downloaded file
> before trusting it: **the real handbook is roughly 5 MB and 100+ pages with an
> extractable text layer.** A one-page PDF is not it.

A workshop/service manual would additionally cover torque specs and teardown
procedures; worth looking for if §16's DIY question comes back "yes".

### Gemini API

Used for §10.6. Note these are third-party summaries — **verify the current model
lineup, pricing and data-usage terms in Google AI Studio and Google's own docs before
building**, since I could not reach `ai.google.dev` from this sandbox and the
secondary sources disagreed with each other on version numbers.

- [Gemini API free tier — limits and quotas](https://pecollective.com/tools/gemini-free-tier-guide/)
- [Gemini API pricing guide 2026](https://www.opslyft.com/blog/google-gemini-api-pricing)
- [Does Gemini train on your data — free vs paid tier](https://meetily.ai/llm-privacy/gemini)
- [Gemini free-tier data privacy](https://docs.bswen.com/blog/2026-03-23-gemini-free-tier-data-privacy/)

### Bike specifications (context only)

Public sources used for orientation while planning. **None of these are treated as
authoritative by the app** (P4); every specification and interval must be confirmed
against the owner's handbook before the app asserts it. The 349cc/398cc contradiction
noted in §3 came from comparing these.

- [Triumph Motorcycles India — T-series Q&A](https://www.triumphmotorcycles.in/for-the-ride/news/motorcycles/t-series-q-and-a-2024-03-28)
- [DriveSpark — Speed 400 service interval](https://www.drivespark.com/two-wheelers/2023/triumph-speed-400-service-interval-details-038657.html)
- [RushLane — Triumph 400 maintenance costs](https://www.rushlane.com/triumph-400-maintenance-will-be-lower-than-royal-enfield-350-bajaj-12474587.html)
- [Triumph 400 Forum — first service](https://www.triumph400forum.com/threads/first-service.239/)
- [Autocar India — Speed 400 specifications](https://www.autocarindia.com/bikes/triumph/speed-400/specifications)
- [Team-BHP — Speed 400 specifications](https://www.team-bhp.com/new-bikes/triumph/speed-400/specifications/)

---

## Appendix B — Session prompts

Paste-ready prompts for building this in a fresh session. Each assumes `plan.md` (this
file) is in the working directory and nothing else is known.

### Environment checklist

Before starting, make sure the session has:

- **Network access to:** `api.triumphtechnicalinformation.com`, `ai.google.dev`,
  `generativelanguage.googleapis.com`, `dl.google.com`, `maven.google.com`,
  `repo1.maven.org`, `services.gradle.org`. The planning session was blocked on most of
  these, which is why the handbook was never retrieved.
- **Android SDK + JDK 17+**, or a session that can install them.
- **Your Gemini API key** — as an environment variable, never committed.
- A repo for the app. This plan currently lives in a portfolio repo; the app should get
  its own.

---

### Prompt 0 — Phase 0: bootstrap and ground truth

```
Read plan.md in full. It is the complete product spec for a personal Android
tablet app that acts as the digital memory of my Triumph Speed 400. I wrote it
with an earlier session; you have no other context, and everything you need is
in that file.

Execute Phase 0 from §15.

Start with the part everything else depends on — the owner's handbook:

1. Download the Triumph Speed 400 owner's handbook PDF. Preferred source and
   mirrors are in the Appendix. VERIFY WHAT YOU GET: the real handbook is
   ~5 MB, 100+ pages, with an extractable text layer. A previous attempt
   returned a 1-page 72 KB raster with no text — that is an auth wall, not the
   handbook. If you hit the same thing, say so and try the mirrors rather than
   proceeding with a bad file.

2. From the handbook, extract:
   - the full scheduled maintenance table (every item, km and time interval)
   - tyre pressures (solo and pillion), oil grade and capacity, coolant spec,
     spark plug, brake fluid spec, chain slack, fuel tank capacity, engine
     displacement
   Record a page number for every single value.

3. Write these into two seed files:
   - components.json  — the component catalogue from §6.1, with real intervals
                        and interval_source="manual" plus manual_page_ref
   - facts.json       — the curated fact table from §10.3
   Anything you cannot find in the handbook gets interval_source="unverified"
   and is flagged in your summary. Do not fill gaps from general knowledge —
   §3 P5 is a hard rule.

4. Then scaffold the app: Kotlin + Compose, Material 3, Room, Hilt, per §14.
   Tablet-only, landscape-first, fixed list-detail panes — no WindowSizeClass
   branching (§4.1). Implement the §6 schema with UUID PKs and row-level
   updated_at. Get it building and running on a tablet emulator.

Report: what you extracted vs. what the handbook didn't cover, and anything in
the plan that turned out to be wrong once you had the real document.
```

---

### Prompt 1 — Phase 1: the logbook

```
Read plan.md. Phase 0 is done — the schema exists and components.json /
facts.json are seeded from the handbook.

Build Phase 1 from §15: the logbook. This is the phase that has to ship early,
because the app's value compounds with elapsed time (§13) — I want to start
logging real data while later phases get built.

Scope:
- Odometer readings + the km/day projection engine (§5.1). Estimates must never
  be written to the DB as observations.
- Fuel logging, ₹-first per §7.2 — amount and rate in, litres derived. Not
  litres-first; that's backwards for Indian pumps.
- The full-to-full fuel economy engine, exactly as specified in §9.1. Partial
  fills accumulate; missed fills break the chain rather than producing a wrong
  number.
- Entry-time validation per §9.2 — every check is a QUESTION, never a rejection.
- Expenses as line items (§4.2). Money lives in exactly one place; no total is
  ever computed by summing events.
- Timeline, dashboard v1 (§7.1), Capture Inbox (§5.2 — gallery import,
  share-sheet target, drag-and-drop; this is the app's only route in, so it has
  to be good).
- Backup / restore / export (§14). Non-negotiable this phase. Verify a restore
  actually works before I trust it with real data.

Write unit tests for the fuel-economy, projection and cost engines before the
UI. §9 is where correctness actually matters.
```

---

### Prompt 2 — Phase 2: maintenance, reminders, documents

```
Read plan.md. Phases 0-1 are done and I've been logging real data.

Build Phase 2 from §15:
- Components, intervals, ComponentActions, DIY vs workshop
- Service records with line items and attached invoices
- The reminder engine per §8 — all four rule types, km rules projected onto
  dates via §5.1, and the notification policy in §8.2 (light checks batch into
  a weekly digest; never notify individually)
- Notification actions: Done / Snooze / Log it, writing real events
- Documents with expiry (§7.6) — archive, not roadside. Insurance needs two
  expiry dates (OD and TP); check my actual policy for the real structure.
- Warranty guard (§5.3) — separate high-priority channel, refuses completion
  without an invoice
- Fault/niggle log (§5.4)
- Staleness handling per §8.3

Provenance badges (§3 P4) render everywhere an interval or number appears.
```

---

### Prompt 3 — Phase 3: understanding

```
Read plan.md. Build Phase 3 from §15.

- Analytics per §11 — ONLY the charts in that table. Each one answers a written
  question; if you want to add a chart, write the question first and tell me.
- The three cost-per-km numbers (§9.3), always labelled, never a bare "cost per
  km". Depreciation is a 🟡 estimate.
- Full-text search (FTS5) across everything including my notes
- Build sheet (§7.10), tyre panel (§5.6)
- Trip readiness check (§5.5) — the payoff screen
- Export a complete history PDF (§5.9)
- Tablet multi-pane polish, keyboard shortcuts for batch entry

Every chart states its window and carries a one-line plain-language takeaway.
```

---

### Prompt 4 — Phase 4: the assistant

```
Read plan.md, especially §10 in full. Build Phase 4.

Provider is Gemini. Two things from §10.6 are load-bearing:

1. PLAN-THEN-RENDER. The model selects tools; the DEVICE executes them and
   composes the answer from local templates. My financial figures must not go
   to Google for ordinary record questions — only the question text and the
   static tool schema cross the network. Open-ended questions that genuinely
   need the numbers get an explicit per-question opt-in with a visible warning.

2. The model ID lives in settings, never in code. Verify the current Flash-tier
   model ID in AI Studio before wiring it up — do not trust any ID written in
   plan.md. The 2.5 series was reported to retire 16 Oct 2026.

Also build:
- Handbook import → chunk → Gemini embeddings → local vectors + FTS hybrid
  retrieval, every chunk page-cited (§10.2). Do NOT paste the whole manual into
  context; §10.2 explains why that shortcut is rejected.
- The record tools in §10.1, typed and deterministic over SQLite
- Provenance badges on every answer (§3 P4)
- The numeric grounding check (§10.4) as a deterministic post-check, not a
  prompt instruction
- The safety rule (§10.5) — verified-only for tyre pressures, torque, fluid
  specs, brake specs, valve clearances. Refuse rather than estimate. Note the
  owner's handbook lacks torque specs, so those refusals are correct.
- Offline fact-table fallback + Quick Specs screen
- Split view: handbook PDF beside the assistant
```

---

### Prompt 5 — utilities

**Verify the seeded facts:**
```
Read plan.md §10.3 and §3 P5. Go through facts.json row by row against the
handbook PDF. For each row confirm the value and the page number, then mark it
verified. Report anything that doesn't match, anything you can't locate, and
anything marked verified that shouldn't be. An unverified row is better than a
wrong one.
```

**Ship a build:**
```
Read plan.md §14. Set up Gradle + GitHub Actions to produce a signed APK I can
sideload onto my tablet. No Play Store, no analytics SDK, no crash reporting —
nothing phones home (§12). The signing key and the Gemini key stay out of the
repo.
```

**Sanity-check the plan against reality:**
```
Read plan.md. I've been using the app for a month. Here's what's actually
happened: [describe]. Tell me which assumptions in the plan turned out wrong,
what should be re-prioritised, and whether the §4.1 revisit trigger (chronically
backed-up Inbox → build a phone capture companion) has fired.
```

---

### Still to decide (§16)

Answer these when the relevant phase starts — a fresh session will ask:

1. What history can you backfill? (purchase date, price, current odo, old invoices)
2. Do you do your own maintenance, or is it all dealer-done?
3. Will you enable billing on the Gemini key?
4. Do you want ride tracking at all, or is a manual trip log enough?
5. How much time per week?
