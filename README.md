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

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md).

## License

[MIT](LICENSE)
