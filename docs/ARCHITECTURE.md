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
| `domain/calculation` | `CalculationEngine` — every formula, pure & tested |
| `di` | `AppContainer` manual dependency graph |
| `ui/<feature>` | One package per screen: `ViewModel` + `@Composable` screen |
| `ui/common` | Shared formatting helpers, `AppViewModelFactory` |
| `ui/navigation` | `AarushNavGraph` — single source of truth for routes |
| `ui/theme` | Material 3 color scheme / typography |

## What's scaffolded but not yet wired to a screen

Per the phased roadmap (see main README), these entities/DAOs/repositories exist and are
fully functional at the data layer, but don't have a dedicated Compose screen yet:
`ScheduleActivity`/`ScheduleDependency` (construction schedule), `LabourRate`/`LabourEntry`
(labour prediction), `ProjectProgress` (daily/weekly progress log), `AppNotification`
(alerts/warnings), PDF/Excel report export. Adding a screen for any of these is a matter
of writing a `ViewModel` + `@Composable` in a new `ui/<feature>` package and wiring a
route in `AarushNavGraph.kt` — the data layer does not need to change.
