import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 compiles Kotlin itself; org.jetbrains.kotlin.android must NOT be applied. ADR-011.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    // M13.8 — measures what `domain/` is actually covered by, rather than what reading it suggests.
    // Build-only: it instruments the JVM test run and writes a report; nothing reaches the APK.
    alias(libs.plugins.kover)
}

/**
 * The Compose stability configuration, named once because two blocks below have to agree on it.
 *
 * ⚠ Declared here rather than at the foot of the file: a build script's top-level `val`s initialise
 * in source order, so one used above its declaration is simply `null` — "Cannot convert 'null' to
 * File", reported against a line that looks perfectly correct.
 */
val composeStabilityFile = "compose-stability.conf"

/*
 * M13.5 — see `compose-stability.conf` and ADR-112.
 *
 * ⚠ Strong skipping is NOT configured here, and must not be: it has been on by default since Kotlin
 * 2.0.20 and this module is on 2.2.10. A `featureFlags` line enabling it would read as though it
 * were off without one.
 *
 * The reports/metrics destinations are commented out rather than deleted. Regenerating them is how
 * the stability file is verified — it is the only way to see whether the compiler read it — and a
 * commented line is a shorter path back than a paragraph describing one.
 */
composeCompiler {
    stabilityConfigurationFiles.add(layout.projectDirectory.file(composeStabilityFile))
    // reportsDestination = layout.buildDirectory.dir("compose-reports")
    // metricsDestination = layout.buildDirectory.dir("compose-metrics")
}

/*
 * ⚠⚠ M13.5 — **the Compose plugin does not track that file as a compile input, and the failure is
 * silent.** Editing `compose-stability.conf` leaves `compileDebugKotlin` and
 * `compileDebugAndroidTestKotlin` UP-TO-DATE, so the edit does not take effect and nothing says so:
 * the build succeeds, the app runs, and it was compiled against the previous contents.
 *
 * That cost a wrong test result here. `StabilityConfigTest` was verified by removing `HabitPeriod`
 * from the file — it went red, correctly — and then **stayed** red after the line was restored,
 * because the restore recompiled nothing. Without this block the only reliable edit is
 * `--rerun-tasks`, which nobody remembers and CI does not need.
 *
 * Matched by name rather than by type: AGP 9 compiles Kotlin itself and
 * `org.jetbrains.kotlin.android` is deliberately not applied (ADR-011), so `KotlinCompile` is not a
 * type this file can name.
 */
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
    inputs
        .file(layout.projectDirectory.file(composeStabilityFile))
        .withPropertyName("composeStabilityConfiguration")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}

android {
    namespace = "com.cristianprincipi.habits"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cristianprincipi.habits"
        minSdk = 26
        targetSdk = 37
        // THE VERSION SCHEME, settled at M14.2.
        //
        // versionName is semver, MAJOR.MINOR.PATCH, and is the only version a human ever reads —
        // the About screen prints it. versionCode is a plain monotonic integer that exists for
        // Play's benefit alone: it must increase on every upload and never otherwise matters, so it
        // is NOT derived from the name. Deriving it (10000*major + 100*minor + patch and the like)
        // buys nothing here and makes a hotfix upload awkward the one time it counts.
        //
        // Bump versionCode on every build that leaves this machine. Bump versionName when the
        // change is worth a user noticing.
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true

        // The About screen (M11.8) states the app's version, and the version lives here. The
        // alternative reads it back out of PackageManager at runtime, which needs a Context on a
        // screen that otherwise needs nothing at all.
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    /*
     * M13.8 — `src/sharedTest` is compiled into **both** test source sets.
     *
     * It exists for one reason: `NotionSetupWalkTest` drives the real `NotionSetupViewModel` on a
     * device, and the fake repository it needs was already written, tested and trusted in the JVM
     * suite. The alternatives were a second copy of `FakeNotionSetupRepository` — two fakes that
     * drift and then disagree about what the wizard does — or `hilt-android-testing` plus a custom
     * instrumentation runner, which the user declined because changing `testInstrumentationRunner`
     * puts all 277 existing instrumented tests at risk for one new test.
     *
     * ⚠ Keep it to fakes that depend on nothing but `domain/` and coroutines. Anything reaching for
     * a JVM-only test library (`kotlinx-coroutines-test`, MockK, Ktor's `MockEngine`) belongs in
     * `src/test`, because this directory has to compile against Android too.
     *
     * ⚠ `kotlin.srcDir`, **not** `java.srcDir`. AGP 9 compiles Kotlin itself (ADR-011), and a
     * directory added to the `java` source directory set never reaches `compileDebugUnitTestKotlin`:
     * the build configures happily and then fails with `Unresolved reference` in every file that
     * used the moved class. Tried, seen, and recorded rather than remembered.
     */
    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/*
 * M13.8 — what Kover is allowed to count, and why the list is not cosmetic.
 *
 * ⚠⚠ **The unfiltered number is actively misleading, and it nearly cost this step its finding.**
 * `domain/usecase` first measured **70%**, which reads as a real gap. Every one of the 58 missing
 * lines was in a Dagger `*_Factory` — generated code, in a package where every hand-written class
 * was already at **100%**. A coverage number that counts code nobody wrote does not report on the
 * tests; it reports on how many `@Inject` constructors the package has.
 *
 * Everything excluded below is generated by Dagger, Hilt, Room or the Compose compiler. Nothing
 * hand-written is excluded — if a class of ours is untested, this file must not be the reason it
 * looks fine.
 *
 * ⚠ `debug` also carries `src/debug` (the gallery and its preview activities), which is a debug-only
 * harness that ships in no release build and is exercised by hand rather than by tests. It is
 * excluded by package for that reason and not because it is inconvenient.
 */
kover {
    reports {
        filters {
            excludes {
                classes(
                    "*_Factory",
                    "*_Factory\$*",
                    "*_MembersInjector",
                    "*_HiltModules*",
                    "Hilt_*",
                    "*_GeneratedInjector",
                    "*_ComponentTreeDeps",
                    "dagger.hilt.internal.*",
                    "hilt_aggregated_deps.*",
                    "*_Impl",
                    "*_Impl\$*",
                    "*ComposableSingletons*",
                )
                packages("com.cristianprincipi.habits.gallery")
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

// Room's own Gradle plugin, not a `ksp { arg(...) }` string: it registers the schema directory as
// a real task output, which is what makes the exported JSON reproducible under the configuration
// cache. CLAUDE.md §6 requires these schemas to be committed and migration-tested.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.navigation.compose)

    // Background sync (CLAUDE.md §3.1). WorkManager is what makes a sync survive process death and
    // reboot; nothing in the check-off path waits on it.
    implementation(libs.androidx.work.runtime.ktx)

    // Home screen widgets (M15.6). Approved by the user 2026-09-08 over plain RemoteViews, which
    // needs no dependency at all now that the dot grid is drawn to a Bitmap either way. Glance
    // earns its place on SizeMode.Responsive — the two widget sizes are one declaration rather
    // than hand-managed onAppWidgetOptionsChanged — and on a suspend content function, so reading
    // Room needs no AppWidgetManager dance across a process boundary.
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // The Notion client. CLAUDE.md §6: data/remote/notion is the only package that may touch the
    // network, and api.notion.com is the only host this app may ever contact (§8).
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    // Ktor's MockEngine — the fake transport M4.14's fake Notion API is built on. CLAUDE.md §6:
    // never point an automated test at the live API.
    testImplementation(libs.ktor.client.mock)

    androidTestImplementation(libs.androidx.test.ext.junit)
    // Declared explicitly to OVERRIDE the espresso 3.5.0 / runner 1.5.0 that Compose UI test pulls
    // in transitively. Espresso 3.5.0 reflects on InputManager.getInstance(), which no longer
    // exists on API 37 — every Compose UI test dies at onIdle without this. See ADR-018.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    // Room's processor must run for the androidTest source set too — see RoomToolchainProbe.
    kspAndroidTest(libs.androidx.room.compiler)
    // ⚠ `MigrationTestHelper` is instrumented-only: it opens a real SQLite file at an old version
    // and runs the migration against it, which no JVM test can do. The schema JSON it validates
    // against is already in the androidTest APK — the Room Gradle plugin puts `app/schemas/` on the
    // androidTest assets path by itself, verified by unzipping the built APK rather than assumed.
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
