# Aarush Construction Project Management System

An offline-first Android app for tracking and managing building construction projects
end-to-end — from plinth-area project costing through BOQ, material requirement
calculation, expenses, vendor and client payments, profit & loss, cash flow, schedule,
and completion prediction.

The core design principle: **after creating a project, day-to-day use requires only two
inputs — BOQ and actual spending. Everything else is calculated automatically**, while
remaining manually editable at every step.

> This is a real, functional Android Studio project — real Room database, real CRUD,
> real calculations, real navigation — not a static prototype.

## Features

- **Project setup**: multi-row area × rate breakdown (e.g. Residence at one ₹/sqft rate,
  Staircase or Portico at another) → auto-summed project value (manually overridable)
- **Thumb-rule cost distribution** across Civil/Structural, MEP, Painting, Joinery, Other
  — fully editable percentages, validated to sum to 100%
- **Material/labour split** within each category budget
- **BOQ management** with per-item material type, quantity, rate, and waste %
- **Material coefficient engine** — user-defined coefficients (e.g. cement bags per m³
  of concrete), never hard-coded, driving automatic material requirement calculation
- **Material inventory** — required vs purchased vs used vs current stock vs balance to
  purchase vs estimated remaining cost, all derived automatically
- **Single expense entry point** — one "Add Expense" fans out automatically to project
  spend, category spend, material purchase + stock, vendor payable, and cash flow
- **Vendor management**, including a civil vendor scoped to the whole civil package with
  ₹/sqft, lump sum, quantity×rate, or percentage-based contracts
- **Client payment tracking** with automatic client balance calculation
- **Profit & Loss dashboard**: budgeted vs actual profit, cost overrun, savings,
  category-wise variance
- **Cash flow**: money in vs money out vs running balance
- **Project dashboard**: budget-vs-actual by category, material status, days remaining,
  progress %
- **Sample/demo project** (Residential Building, 2000 sqft @ ₹2,500/sqft) — flagged and
  deletable as a group
- Data-layer support (entities/DAOs/repositories) for construction schedule, labour
  prediction, shortage alerts, and reports — see [Roadmap](#roadmap) for what's wired to
  a screen today vs what's scaffolded for the next phase

## Technology stack

- Kotlin, Jetpack Compose, Material 3
- MVVM with a pure, unit-tested calculation layer (Clean-Architecture-lite — see
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md))
- Room (SQLite) for 100% offline local storage
- Kotlin Coroutines + `StateFlow`
- Navigation Compose
- Manual dependency injection (no Hilt/Koin required to build)
- Gradle Kotlin DSL

## Getting started

### Prerequisites

- Android Studio Koala (2024.1) or newer
- JDK 17
- Android SDK 34 (compileSdk/targetSdk), minSdk 26

### Open in Android Studio

1. Clone the repo:
   ```bash
   git clone https://github.com/<your-org>/aarush-cpm.git
   cd aarush-cpm
   ```
2. Open the folder in Android Studio → **File → Open** → select the project root.
3. Let Gradle sync. Android Studio bundles a compatible Gradle distribution and will
   offer to fetch `gradle-8.7-bin.zip` automatically the first time (see
   `gradle/wrapper/gradle-wrapper.properties`).
4. Run the `app` configuration on an emulator or physical device (API 26+).

### Getting the Gradle wrapper JAR

This repository ships `gradlew` / `gradlew.bat` and `gradle-wrapper.properties`, but not
the binary `gradle-wrapper.jar` (binaries don't belong in source control review, and this
project was generated in an offline sandbox with no network access to fetch it). Before
running `./gradlew` from the command line, generate it once with a local Gradle install:

```bash
gradle wrapper --gradle-version 8.7
```

Or simply open the project in Android Studio first — it will generate/download the
wrapper JAR automatically on sync, and from then on `./gradlew` works normally on the
command line too.

### Build an APK from the command line

```bash
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

### Run tests

```bash
./gradlew testDebugUnitTest       # unit tests (CalculationEngine, etc.)
./gradlew connectedAndroidTest    # instrumented UI tests (needs an emulator/device)
```

## Login (V1)

Authentication is local/mock in V1 (username or email + password, stored hashed in
Room), with a "Create account" flow and a "Remember me" toggle. The architecture keeps
this behind `AuthRepository`'s interface so a real backend can be swapped in later
without touching any ViewModel or screen. "Forgot password" is a UI stub in V1 since
there's no backend to send a reset link through yet.

**Sign in with Google** is also available on the login screen, using the current
[Credential Manager API](https://developer.android.com/identity/sign-in/credential-manager-siwg)
(`GoogleSignInHelper.kt`). This requires a one-time setup step that only you (the app
owner) can do, because it needs your app's own signing certificate registered with
Google — see below.

### Setting up Google Sign-In

1. Create (or open) a project in the [Google Cloud Console](https://console.cloud.google.com/)
   or [Firebase Console](https://console.firebase.google.com/) — either works, since this
   uses standard Google OAuth, not a Firebase-specific SDK.
2. Get your app's SHA-1 signing certificate fingerprint:
   ```bash
   # Debug builds (Android Studio's default debug keystore):
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
   For release builds, use your real release keystore instead.
3. In Google Cloud Console → **APIs & Services → Credentials**, create an **OAuth 2.0
   Client ID** of type **Android**, using package name `com.aarush.cpm` and the SHA-1 from
   step 2. This registers your app with Google (needed even though the code doesn't
   reference this ID directly).
4. Create a second OAuth 2.0 Client ID of type **Web application**. Copy its client ID —
   it looks like `1234567890-abc...apps.googleusercontent.com`.
5. Paste that Web client ID into `app/src/main/res/values/strings.xml`, replacing the
   `google_web_client_id` placeholder value.
6. Rebuild and run. The "Sign in with Google" button will now show the real account
   picker instead of the "not configured yet" message.

Until you complete this, the button still works — it just returns a clear error instead
of crashing, so the rest of the app is unaffected either way.

**Note on V1 trust model:** because this is a fully offline, local-only app with no
backend server, the Google ID token is trusted on-device the same way a typed password
is (it never leaves the device). If you add a real backend later, verify the ID token
server-side before treating someone as logged in, rather than trusting the client alone.


## Database

See [`docs/DATABASE.md`](docs/DATABASE.md) for the full entity-relationship notes,
per-table column explanations, and migration guidance.

## Architecture

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the layer-by-layer breakdown and
the reasoning behind key decisions (why calculations live in one pure `CalculationEngine`
object, how "enter once, calculate everywhere" is implemented, etc.).

## Screenshots

_Add screenshots here once you've run the app — e.g._

| Dashboard | Create Project | Project Detail | Add Expense |
|---|---|---|---|
| _screenshot_ | _screenshot_ | _screenshot_ | _screenshot_ |

## Roadmap

Built in the phased order below. Phases 1–3 have working screens end-to-end; Phase 4
has Vendor + Client Payment screens plus P&L/cash-flow figures on the project dashboard;
Phases 5–6 have their data layer (entities, DAOs, repositories) in place but no
dedicated screen yet — see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#whats-scaffolded-but-not-yet-wired-to-a-screen).

- [x] **Phase 1** — Login → Project → Project Value → Cost Distribution → Dashboard
- [x] **Phase 2** — BOQ → Material Coefficients → Material Requirement
- [x] **Phase 3** — Expenses → Purchases → Inventory
- [x] **Phase 4 (partial)** — Vendors → Client Payments → Profit (on dashboard) →
      Cash Balance (on dashboard)
- [ ] **Phase 5** — Schedule screen, labour prediction UI, material shortage alerts UI
- [ ] **Phase 6** — Reports (PDF/Excel export), notifications UI, further polish

### Future expansion (by design, not yet built)

Multi-user accounts, cloud backup/sync (Firebase/Supabase/custom REST — the repository
layer is the intended seam), vendor/client logins, photo-based expense records with
OCR/invoice scanning, WhatsApp sharing, automatic PDF quotations, GST support, purchase
orders, vendor work measurement, site photo/drawing storage, Gantt charts, advanced
analytics.

## Changelog

**Since first build:**
- Added local **Backup & Restore** (Settings tab) — see the dedicated section below for
  why this matters and how it works.
- Fixed a crash: the BOQ, Expenses, Vendors, and Client Payments screens would close the
  app immediately on open. Cause: their ViewModels read a `lateinit` `StateFlow` before
  the coroutine that initialized it had run. Fixed by initializing all screen state
  eagerly with `flatMapLatest` over a `MutableStateFlow<Long?>` project id (see any of
  `BOQViewModel`, `ExpenseViewModel`, `VendorViewModel`, `ClientPaymentViewModel`).
- Fixed the launcher icon XML namespace typo (`res/vector` → `res/android`) that made
  `processDebugResources` fail in CI/Android Studio.
- Added **Sign in with Google** on the login screen (Credential Manager API) — requires
  a one-time setup only the app owner can do, see [Setting up Google Sign-In](#setting-up-google-sign-in).
- Project creation now supports **multiple area/rate line items** per project (e.g.
  Residence at one ₹/sqft rate, Staircase at another) instead of a single blended
  area × rate. Existing single-area projects still work identically — a project is just
  the special case of one row.
- The area/rate breakdown moved off the Project Detail screen's scrolling list — tap the
  header card (Client/Location/Area/Value) to view and edit it in a popup instead.
- "Quick actions" (BOQ/Expenses/Vendors/Payments) is now a bottom tab bar on the Project
  Detail screen, alongside a new **Settings** tab.
- Added a per-project **Settings** screen with two editable tables: a built-in BOQ
  notation/coefficient reference (Excavation, PCC, RCC, Brick work, plastering, tiling,
  etc. — each with Cement/P.Sand/M.Sand/Bricks/20mm Aggregate coefficients) and a
  material rate card (Cement, Sand, Steel, Tiles, Painting, etc.). Both are seeded with
  sensible defaults on every new project and fully editable/addable afterward.
- BOQ item codes are now picked from a dropdown of those notations — selecting one
  auto-fills description, unit, and links the coefficient engine automatically.
- BOQ quantity entry now supports two modes: **Nos** (a typed count) or **L × B × D**
  (sets × length × breadth × depth), with the resulting quantity calculated automatically
  instead of typed by hand.
- BOQ list items are now collapsible/expandable — tap a row to see full details.
- The area/rate "Add" form inside the popup is now hidden by default — an **Add item**
  button reveals it only when you need it, instead of always showing empty fields.
- BOQ quantity entry is now a single unified calculator: **No. of Member × No. Per
  Member × Length × Breadth × Depth**, with a fixed unit dropdown (Rmt, Rft, Sqft, Sqm,
  Cft, Cum) instead of free-text units. Any field left blank counts as 1, so a plain
  count, a pure area, or a full volume all use the same five fields. Picking an item
  code from the notation dropdown no longer overwrites the description — that's left
  blank for your own note — and built-in item codes are now numbered (e.g.
  "001-Excavation").
- BOQ, Expenses, Vendors, and Client Payments all now have an **Edit** action (not just
  Delete) on each row, reusing the same Add dialog pre-filled with the existing values.
- Added **Sign in with biometrics** (fingerprint/face/device PIN via Android's
  Credential/BiometricPrompt API) for anyone who previously logged in with "Remember me"
  checked.
- Fixed **"Remember me" not doing anything**: the checkbox used to just flip a flag in
  the database that nothing ever read back. It's now backed by DataStore, actually
  persists across app restarts, pre-fills the identifier field on next launch, and
  unlocks the biometric quick sign-in option.
- Added a **Home** tab (first item in the bottom bar) showing a cross-project summary —
  total BOQ value, expenses, vendor payables, and client payments received across every
  project at a glance.
- Restructured project navigation: after login you land on **Projects** + **Settings**
  only (the cross-project Home summary was removed). Opening a project now gives it its
  own tab bar — **Home, BOQ, Expenses, Vendors, Payments, Settings** — all sharing one
  screen instead of pushing a new screen per action. Home (first, next to BOQ) is what
  used to be the whole project screen: header, budget vs actual, material status.
- BOQ items are now grouped by item code with an expandable header per group (e.g. all
  "001-Excavation" entries together); expanding an individual item shows its full
  description, quantity, and a new **Remark** field.
- Expenses are now grouped by item/material name the same way, each entry showing its
  **date**, quantity, and remark when expanded.

- Expenses were redesigned around a real material rate card: **Received/Expense**
  direction toggle (so a refund or adjustment can be logged from the same form without
  counting as spend), a **Date** picker, **mode of payment**, and a **material picker**
  sourced from the project's rate card (Cement, P.Sand, M.Sand, Bricks, Aggregates,
  Steel, Tiles, Painting, Gravel, Door/Window/Electrical/Plumbing, Extra Labours &
  Machinaries, and now **Labour Cost for Civil Vendor**) that auto-fills unit and rate.
  A **"+ Add Freight & Unloading Charges"** button adds that as its own linked expense
  line rather than folding it into the material's amount. Quantity/Rate/Unit and Save
  work as before.

- Expenses were redesigned around a real material rate card: **Received/Expense**
  direction toggle (so a refund or adjustment can be logged from the same form without
  counting as spend), a **Date** picker, **mode of payment**, and a **material picker**
  sourced from the project's rate card (Cement, P.Sand, M.Sand, Bricks, Aggregates,
  Steel, Tiles, Painting, Gravel, Door/Window/Electrical/Plumbing, Extra Labours &
  Machinaries, and now **Labour Cost for Civil Vendor**) that auto-fills unit and rate.
  A **"+ Add Freight & Unloading Charges"** button adds that as its own linked expense
  line rather than folding it into the material's amount. Quantity/Rate/Unit and Save
  work as before.
- Removed the standalone **Payments** tab — merged into Expenses. Expense/Received is
  now a filter at the top of the Expense tab (not just a field inside the Add dialog):
  "Expense" shows the grouped spend list, "Received" shows a flat list of amounts
  received with date and mode of payment. Adding a new entry from either view adds to
  that direction automatically.
- The Add Expense dialog was trimmed to exactly: Date, Mode of payment, List of
  material, Description, Quantity, Unit, Rate, an auto-calculated Total, and the
  Freight & Unloading button. Type/Category/Vendor selectors were removed from this
  form — vendor payments are now identified automatically by which material you pick
  (see below), and category defaults to "Other/Misc" for expenses entered this way.
- Expenses tab has a **Sort by: Material / Date** toggle, using the same
  expand/collapse grouped-card pattern as BOQ.
- **Vendors ↔ Expenses are now linked.** Creating a vendor auto-adds a "Labour Cost for
  &lt;Vendor&gt;" row to the project's material list. Picking that row in the Expense
  form's material picker is how you log a payment to that vendor — no separate vendor
  field needed. The Vendor tab shows each vendor's payment history (date + amount) and
  running balance, pulled from those tagged expenses.
- The vendor Add/Edit form now adapts to the work basis: **"Rate work (plinth area)"**
  shows Rate (₹/sqft) + Area (sqft) with a live contract-value total; **"Lumpsum"**
  shows a single Lump Sum Amount field. Other rate types still fall back to a generic
  Rate + Quantity pair.
- Fixed a real UI bug: the Add/Edit dialogs (BOQ, Expense, Vendor) had no scroll
  capability, so once the field list got taller than the dialog, inputs visually
  overlapped instead of scrolling. All three now scroll properly.
- Expense category is now inferred automatically instead of defaulting to "Other/Misc":
  it traces the picked material through the BOQ coefficient engine (material → the
  notation(s) that consume it → that notation's BOQ category) so Budget-vs-Actual by
  category stays meaningful even though the simplified form doesn't ask for it
  directly. Falls back to "Other/Misc" for materials with no BOQ/coefficient link yet
  (including vendor payments, which aren't stock materials).

## Backup & Restore

Uninstalling an Android app deletes its private storage — including Room's SQLite
database — which is exactly what was happening here. **Settings → Backup & Restore**
now lets you export the entire database (every project) to a file you choose via the
system file picker (Downloads, Google Drive, a USB drive, anywhere outside the app's
own storage), and restore it back later, including after a fresh install.

- **Back up now** — checkpoints the database (flushes any in-progress writes) and
  copies it to wherever you pick. Do this before uninstalling, updating across a device
  swap, or just periodically as a safety net.
- **Restore from backup** — pick a previously saved backup file. This **replaces
  everything currently in the app**, so it asks for confirmation first. The app
  restarts automatically afterward — that's required, not optional: the restore swaps
  the database file out from under every already-open connection, so a full process
  restart is the only way already-running screens don't end up reading stale, closed
  state.

As a secondary safety net (not a replacement for the above — it depends on Android's
own cloud backup being enabled and the same Google account being used on reinstall,
neither of which is guaranteed), `backup_rules.xml` / `data_extraction_rules.xml`
explicitly include Room's database folder in Android's built-in Auto Backup, which was
otherwise relying on default behavior despite `android:allowBackup="true"` already being
set.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

[MIT](LICENSE)
