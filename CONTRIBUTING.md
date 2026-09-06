# Contributing to Aarush CPM

Thanks for your interest in improving the Aarush Construction Project Management System.

## Ground rules

- **Never commit secrets.** No API keys, signing keystores, `google-services.json`,
  or passwords. `.gitignore` already blocks the common cases — don't work around it.
- All business math lives in `domain/calculation/CalculationEngine.kt`. If you're adding
  a formula, add it there (pure function, no Android/Room imports) and add a unit test in
  `app/src/test/java/com/aarush/cpm/calculation/`.
- Keep layers separate: **UI → ViewModel → Repository → Room**. Compose functions should
  never contain business calculations or direct DAO calls.
- New Room entities require a schema version bump and a migration (see
  `docs/DATABASE.md`) — don't silently change existing table shapes.

## Getting set up

1. Fork and clone the repo.
2. Open in Android Studio (Koala/2024.1+ recommended).
3. Let Gradle sync. If prompted, download the missing Gradle distribution (see README).
4. Run the `app` configuration on an emulator or device (minSdk 26).

## Making a change

1. Create a branch: `git checkout -b feature/short-description`.
2. Write/update unit tests for anything in `domain/calculation` or `data/repository`.
3. Run `./gradlew testDebugUnitTest` before opening a PR.
4. Keep PRs focused — one feature or fix per PR is easier to review.
5. Describe **what** changed and **why** in the PR description; screenshots are welcome
   for UI changes.

## Reporting bugs / requesting features

Open a GitHub issue with:
- Steps to reproduce (for bugs)
- Expected vs actual behaviour
- Device/emulator + Android version, if relevant

## Code style

- Kotlin official code style (already set in `gradle.properties`).
- Prefer `StateFlow` over `LiveData` for new ViewModel state.
- Favour small, named composables over deeply nested inline lambdas.
