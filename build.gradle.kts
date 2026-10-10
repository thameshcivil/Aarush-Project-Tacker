plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Test-only: renders Compose screens to PNG on the JVM for UI review (no emulator).
    id("app.cash.paparazzi") version "1.3.4" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
