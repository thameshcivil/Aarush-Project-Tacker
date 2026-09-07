# Database

Aarush CPM uses a single Room database, `aarush_cpm.db`, currently version 2, defined in
`data/database/AppDatabase.kt`.

## Entity-relationship overview

```
User (local auth only — not linked to Project by FK in V1)

Project 1─── * CostAllocation      (thumb-rule % + material/labour split per category)
Project 1─── * Vendor
Project 1─── * BOQItem
Project 1─── * Material
Project 1─── * MaterialCoefficient (work-item → material → qty-per-unit + waste override)
Project 1─── * MaterialPurchase ──0..1 Expense   (auto-created when Expense.type == MATERIAL)
Project 1─── * MaterialUsage
Project 1─── * Expense           ──0..1 Vendor   (optional FK when type == VENDOR)
Project 1─── * ClientPayment
Project 1─── * LabourRate
Project 1─── * LabourEntry       ──0..1 ScheduleActivity
Project 1─── * ScheduleActivity  ──0..1 Vendor
ScheduleActivity 1─── * ScheduleDependency (self-referential: activity → depends-on-activity)
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
| `AppSettings` | Simple `key → value` string store for global defaults (currency symbol, default waste %, etc.) editable from Settings. |

## Type converters

Room can't store Kotlin enums natively, so `data/database/Converters.kt` maps every enum
(`ProjectStatus`, `CostCategory`, `VendorWorkCategory`, `VendorRateType`, `ExpenseType`,
`PaymentMode`, `PaymentStatus`, `ActivityStatus`, `NotificationType`) to/from its `name`
string. Dates are stored as epoch-millisecond `Long`, so no date converter is needed.

## Migrations

The schema is currently version 2. `MIGRATION_1_2` (in `AppDatabase.kt`) adds the
`project_area_components` table and a `users.authProvider` column, both additive with no
data loss for existing installs. When you change any `@Entity` further, bump
`@Database(version = ...)` again and add a matching `Migration(oldVersion, newVersion)`
registered via `.addMigrations(...)` on the `Room.databaseBuilder` call in
`AppDatabase.getInstance()` — do not rely on `fallbackToDestructiveMigration()` once the
app has real user data.

## Seed / demo data

`data/repository/SampleDataSeeder.kt` creates one full demo project (per product spec
section 33: "Residential Building", 2000 sqft, ₹2,500/sqft) with sample materials,
coefficients, BOQ items, a vendor, expenses, a client payment, and one schedule activity.
Every row it creates sets `isDemoData = true` (on `Project` and `Expense`) so demo data
is identifiable and safe to bulk-delete later.
