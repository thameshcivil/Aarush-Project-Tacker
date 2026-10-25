plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("app.cash.paparazzi")
}

android {
    namespace = "com.aarush.cpm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.aarush.cpm"
        minSdk = 26
        targetSdk = 34
        // Every CI build gets a higher number, so phones see it as an update (App Distribution
        // and Android both need the number to go up). Local builds stay at 2.
        val ciBuild = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        versionCode = if (ciBuild != null) 100 + ciBuild else 2
        versionName = if (ciBuild != null) "1.2.$ciBuild" else "1.2.0-local"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    // One fixed signing key for every build (CI decodes it from the AARUSH_KEYSTORE_B64 secret).
    // Same key = updates install over the old app without uninstalling, and Google sign-in
    // works (Firebase knows this key's SHA-1). Without the secret, Gradle's temporary key is used.
    val aarushKeystore = System.getenv("AARUSH_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (aarushKeystore != null) {
            create("aarush") {
                storeFile = aarushKeystore
                storePassword = System.getenv("AARUSH_KEYSTORE_PASSWORD")
                keyAlias = "aarush"
                keyPassword = System.getenv("AARUSH_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (aarushKeystore != null) signingConfig = signingConfigs.getByName("aarush")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            if (aarushKeystore != null) signingConfig = signingConfigs.getByName("aarush")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core / Compose
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // DataStore (for "remember me" / settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Sign-In (Credential Manager — the current recommended API, replaces the old
    // GoogleSignInClient). Requires a Firebase/Google Cloud OAuth Web Client ID — see
    // README "Setting up Google Sign-In" for the one-time setup only you can do.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Cloud sync (Phase 2): Firestore for the shared data, Firebase Auth with the team's
    // Google account. Free Spark plan; project aarush-cpm, database in Mumbai.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Biometric login ("remember me" + fingerprint/face unlock)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    // Charts (simple, lightweight - Compose native canvas used instead of heavy lib)

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.google.truth:truth:1.4.2")
    // Plain SQLite on the JVM: the cloud-sync tests run two simulated phones against it.
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
