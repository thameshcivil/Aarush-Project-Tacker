# Database

Aarush CPM uses a single Room database, `aarush_cpm.db`, currently **version 8**
(`AppDatabase.VERSION`), defined in `data/database/AppDatabase.kt`.

## Entity-relationship overview

```
User (local auth only — not linked to Project by FK in V1)

Project 1─── * CostAllocation      (thumb-rule % + material/labour split per category)
Project 1─── * Vendor
Project 1─── * BOQItem
Project 1─── * Material
Project 1─── * MaterialCoefficient (work-item → material → qty-per-unit + waste override)
Project 1─── * MaterialPurchase ──0..1 Expense   (auto-created when Expense.type == MATERIAL)
Project 1─── * MaterialUsage     ──0..1 ActivityProgressLog (auto-booked consumption)
Project 1─── * Expense           ──0..1 Vendor   (optional FK when type == VENDOR)
Project 1─── * ClientPayment
Project 1─── * LabourRate
Project 1─── * LabourEntry       ──0..1 ScheduleActivity
Project 1─── * ScheduleActivity  ──0..1 Vendor, ── BOQ notation (linkedNotationCode)
ScheduleActivity 1─── * ScheduleDependency (finish-to-start, with lagDays)
ScheduleActivity 1─── * ActivityProgressLog (site progress entries)
Project 1─── * BOQNotation, MaterialRateCard (0..1 Vendor via linkedVendorId)
Project 1─── * ProjectProgress
Project 1─── * AppNotification
Project 1─── * ProjectAreaComponent (one row per area/rate line item — see below)

AppSettings — global key/value, not linked to a project (defaults referenced by
              CalculationEngine callers unless a project-level value overrides them)
```

Room foreign keys are expressed as plain `Long` id columns (e.g. `Expense.projectId`)
rather than `@ForeignKey` constraints with cascade rules, so that partially-entered data
(e.g. an expense typed before its vendor exists) never throws at the database layer —
validation instead happens in the ViewModel/UI (see product spec section 29,
"Data Validation").

## Table-by-table notes

| Entity | Key columns worth knowing |
|---|---|
| `Project` | `projectValue` is the sum of `areaSqft × ratePerSqft` across every `ProjectAreaComponent` row for that project (computed by `ProjectRepository.createProject()`), stored so a manual override (`isProjectValueManuallyOverridden`) persists correctly. `plinthAreaSqft`/`ratePerSqft` are kept as a blended total-area / effective-rate summary for screens that just want one headline figure — the real per-component breakdown lives in `ProjectAreaComponent`. |
| `ProjectAreaComponent` | One row per area/rate line item entered when creating (or later editing) a project — e.g. "Residence" at ₹2,500/sqft and "Staircase" at ₹3,200/sqft as two separate rows on the same project, each contributing its own `areaSqft × ratePerSqft` to the total. A project with a single area still works fine — it's just one row. |
| `CostAllocation` | One row per `(projectId, category)`. `percentOfProjectValue` + `materialPercent`/`labourPercent` are all user-editable after creation; budgets are **derived**, never stored, so editing a percentage instantly recalculates everywhere. |
| `BOQItem` | `materialType` is a free-text key that should match a `MaterialCoefficient.workItemKey` for that project — this is how BOQ quantities flow into material requirement calculations without a rigid enum. |
| `MaterialCoefficient` | `wastePercentOverride` is nullable; when null, `BOQItem.wastePercent` is used instead, so waste % can be set per-BOQ-line or per-coefficient. |
| `MaterialPurchase` | `expenseId` links back to the `Expense` that generated it (nullable — a purchase can also be logged directly, bypassing the expense flow, if needed). |
| `Expense` | The single "actual spending" entry point (product spec section 13/36). `type` drives what else gets updated (see `ExpenseRepository.addExpense`). |
| `Vendor` | `amountPaid` is a cached running total, kept in sync by `ExpenseRepository` whenever an `Expense` tagged with that vendor is added/edited/deleted. `contractValue` is computed once at creation from `rateType`/`rate`/`quantity`, then freely editable. |
| `User` | `authProvider` is `"LOCAL"` for username/password accounts or `"GOOGLE"` for Sign-in-with-Google accounts (plain `String`, not an enum, to keep the migration trivial). Google accounts have an empty `passwordHash`. |
| `AppSettings` | Simple `key → value` string store. Also holds `schedule.sundayOff.<projectId>`. |
| `BOQItem` (v8) | `floor` tags a measurement with its floor/location ("Foundation", "Ground Floor"…, blank = whole building) so the schedule allocates it exactly. |
| `MaterialRateCard` | `linkedVendorId` marks a vendor's "Labour Cost for <Vendor>" line — owned by the Vendors tab, never rebuilt by the Settings save. |
| `ScheduleActivity` (v8) | Stores only the *inputs* — notation + floor + share % (→ quantity from BOQ), category + weight or override (→ budget), productivity (→ duration), `plannedStart/End` (the baseline). Quantities, budgets, forecast dates and earned value are derived by `ScheduleEngine` at read time. `progressPercent`, `completedQuantity`, `actualStart/Completion` are caches recomputed from the logs. |
| `ScheduleDependency` (v8) | `lagDays` for waits such as slab curing. |
| `ActivityProgressLog` (v8) | One site entry: date, quantity done (or cumulative %), workers, notes. Each log owns the `MaterialUsage` rows it booked (`MaterialUsage.progressLogId`). |
| `ProjectProgress` | Overall % snapshot written after each log — feeds the project card/Home. |

## Type converters

Room can't store Kotlin enums natively, so `data/database/Converters.kt` maps every enum
(`ProjectStatus`, `CostCategory`, `VendorWorkCategory`, `VendorRateType`, `ExpenseType`,
`PaymentMode`, `PaymentStatus`, `ActivityStatus`, `NotificationType`) to/from its `name`
string. Dates are stored as epoch-millisecond `Long`, so no date converter is needed.

## Migrations

| Migration | Change |
|---|---|
| 1 → 2 | `project_area_components` table; `users.authProvider` |
| 2 → 3 | `boq_notations`, `material_rates`; BOQ dimension columns (and a `quantityMode` column later dropped from the entity) |
| 3 → 4 | `boq_items.perMember` |
| 4 → 5 | `expenses.direction` |
| 5 → 6 | `material_rates.linkedVendorId` |
| 6 → 7 | `expenses.freightAmount` |
| 7 → 8 | **Rebuilds `boq_items`** without the orphaned `quantityMode` column (which made Room's schema validation crash any install or backup that had passed through v3) and adds `floor`; schedule columns on `schedule_activities`, `schedule_dependencies.lagDays`, the `activity_progress_logs` table and `material_usages.progressLogId`. |

Every migration is additive or copy-based — no data loss. When you change any `@Entity`,
bump `AppDatabase.VERSION`, add a `Migration(old, new)` and register it in
`AppDatabase.getInstance()`. Don't use `fallbackToDestructiveMigration()` with real data.
Restore-from-backup rejects a backup whose schema version is newer than the app's.

## Deleting

Child rows link to a project by plain `projectId` columns (no FK cascades), so
`ProjectRepository.deleteProject()` deletes every child table explicitly in one transaction.

## Seed / demo data

`data/repository/SampleDataSeeder.kt` creates the demo G+1 residence (2000 sqft @
₹2,500/sqft) through the real repositories: built-in notations/rates, floor-tagged BOQ, a
civil vendor and its labour line, purchases (→ stock), a vendor payment, a client receipt,
a generated schedule and some logged progress. The project is flagged `isDemoData`;
Settings → *Remove demo* deletes it and all its rows.
