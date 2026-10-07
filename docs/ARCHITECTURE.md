# Architecture

Aarush CPM follows a unidirectional, layered MVVM / Clean-Architecture-lite structure:

```
UI (Jetpack Compose screens)
      │  collects StateFlow, calls ViewModel functions
      ▼
ViewModel (per-screen, in ui/<feature>/)
      │  calls repository suspend functions / observes Flows
      ▼
Repository (data/repository/)
      │  orchestrates DAOs + CalculationEngine, never contains UI/Android code
      ▼
CalculationEngine (domain/calculation/) ◄── pure functions, unit tested in isolation
      │
Room DAOs (data/dao/) ── AppDatabase (data/database/)
      │
SQLite (on-device, offline-first)
```

## Why this shape

- **Business calculations are never inline in Compose.** Every formula in the product
  spec (project value, category budgets, material requirement, waste, stock, client
  balance, profit, cash flow, labour prediction, shortage warnings) lives in
  `CalculationEngine`, a single pure Kotlin object with no Android imports. This makes
  it trivially unit-testable (`app/src/test/.../CalculationEngineTest.kt`) and guarantees
  the same formula is never accidentally re-implemented slightly differently on two
  screens.

- **Repositories own cross-entity consequences.** The core UX rule — *"enter once,
  calculate everywhere"* — is implemented in `ExpenseRepository.addExpense()`: adding
  one `Expense` row automatically creates a `MaterialPurchase` row (when it's a material
  expense) and updates the tagged vendor's cached `amountPaid` (when it's a vendor
  expense). No screen has to re-enter the same fact twice, and no ViewModel has to know
  about these side effects — they're encapsulated where the data relationships live.

- **ViewModels are thin.** They hold `StateFlow<UiState>`, translate user actions into
  repository calls, and format nothing (formatting helpers live in `ui/common/`). This
  keeps them easy to reason about and keeps Compose screens dumb/declarative.

- **Manual DI (`di/AppContainer.kt`) instead of Hilt/Koin.** This keeps the project easy
  to open and build with zero extra Gradle plugins or annotation processors beyond Room.
  Every dependency is constructor-injected through interfaces/classes, so swapping in a
  DI framework later is a mechanical change, not a redesign.

## Offline-first & future sync

All reads/writes go through Room. No repository assumes network availability. The
`AuthRepository` is intentionally isolated so V1's local/mock authentication can be
swapped for Firebase Auth (or any REST backend) later without touching ViewModels or UI
— they only depend on `AuthRepository`'s public suspend functions.

For future cloud sync, the natural seam is the repository layer: each repository could
grow a `syncWithRemote()` function that reconciles Room with a remote source, while
`Flow`-based observers in the UI continue to "just work" once Room is updated locally.

## Module map

| Package | Responsibility |
|---|---|
| `data/entity` | Room `@Entity` data classes — the schema |
| `data/dao` | Room `@Dao` interfaces — raw CRUD/queries |
| `data/database` | `AppDatabase`, `Converters` (enum ↔ String) |
| `data/repository` | Cross-entity orchestration, the only layer ViewModels talk to |
| `domain/calculation` | `CalculationEngine` (formulas) and `CategoryRules` (vendor/material → cost category), pure & tested |
| `domain/schedule` | `ScheduleEngine` (BOQ→quantity, budgets, CPM, tracking, earned value, S-curve), `ResidentialScheduleTemplate`, `WorkCalendar` — pure & tested |
| `di` | `AppContainer` manual dependency graph |
| `ui/<feature>` | One package per screen: `ViewModel` + `@Composable` screen |
| `ui/common` | Shared components (`AppCard`, `StatTile`, `MetricRow`, `ProgressRail`, `StatusPill`…), formatting/date helpers, `AppViewModelFactory` |
| `ui/schedule` | Schedule tab: overview, activities, Gantt, site log, dialogs |
| `ui/navigation` | `AarushNavGraph` — single source of truth for routes |
| `ui/theme` | Material 3 color scheme / typography |

## Schedule module

```
ScheduleRepository ──reads──► BOQ items, notations, coefficients, rate card, vendors,
        │                     cost allocations, expenses, progress logs
        ▼
ScheduleEngine (pure)
  plannedQuantities()   BOQ (floor-tagged first, then shared by %) → activity quantity
  durationFromQuantity  quantity ÷ crew output
  allocateBudgets()     civil: BOQ material cost + labour contracts by man-days;
                        other categories: % of Budget 1 by weight  → Σ = Budget 2
  criticalPath()        forward/backward pass on a WorkCalendar → baseline, float, critical
  track()               actual progress → forecast dates, delay, status
  earnedValue(), sCurve(), remainingQuantityInWindow()
        ▼
ScheduleReport ──► Schedule tab, Home schedule-health card, alerts
```

Only inputs are stored on `ScheduleActivity`; everything else is derived on read, so an
edit to the BOQ, a rate, a vendor contract or a cost % is reflected immediately. Logging
progress books `MaterialUsage` (quantity × coefficient × waste) so stock is real, and writes
a `ProjectProgress` snapshot for the project list.

`BudgetMath` is shared by the Home Budget-2 card and the schedule so both always agree.

## Design system

`ui/theme/Theme.kt` defines the light/dark "site ledger" tokens (`SiteTheme.colors`:
accent, planned, good, bad, warn, hairline, muted, sheet) on top of the Material 3 scheme,
plus `LabelMono` and `FigureStyle`. Screens use the shared components in
`ui/common/Components.kt` instead of raw Material cards, so colour and spacing changes are
made in one place.

## Still data-layer only

`LabourRate`/`LabourEntry` (labour is planned per activity and recorded on progress logs)
and `AppNotification` (alerts are computed live by `ScheduleRepository.buildReport`) have
no screens of their own.
