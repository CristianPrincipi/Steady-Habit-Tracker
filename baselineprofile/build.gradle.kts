import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * M14.9 — the Baseline Profile generator. ADR-113 and ADR-143.
 *
 * A `com.android.test` module: it builds one instrumentation APK that drives `:app` from outside, and
 * nothing in here reaches the app's APK. What does reach it is the text file it writes into
 * `app/src/release/generated/baselineProfiles/`.
 *
 * Regenerate with the `Pixel_9` emulator booted and NO phone attached:
 *
 *     ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile
 */
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

android {
    namespace = "com.cristianprincipi.habits.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/*
 * ⚠⚠ A CONNECTED EMULATOR, AND THE LOCKS THAT KEEP IT FROM BEING THE USER'S PHONE.
 *
 * Generation installs the app and uninstalls it afterwards, exactly as `connectedAndroidTest` does
 * (`CLAUDE.md` §7) — and the user's phone is often plugged into this machine, carrying a live Notion
 * connection and seven placed widgets.
 *
 * A Gradle managed device was the first design, because a device Gradle creates cannot be their phone.
 * It was abandoned on evidence, not preference: the API 37 image it downloads (`android-37.0`
 * `google_apis_ps16k`, rev 6) never finished booting on this host — three Gradle attempts timed out,
 * with memory freed to 8.5 GB and with `swiftshader_indirect`, and a hand launch of the same AVD sat at
 * an empty `sys.boot_completed` for five minutes — while `Pixel_9` cold-booted in 30 seconds the same
 * hour. ADR-143 §5.
 *
 * So there are three locks instead of one:
 *   1. the check below — no `ANDROID_SERIAL` naming an emulator, no install at all;
 *   2. `BaselineProfileGenerator` refuses any hardware that is not `ranchu`;
 *   3. the session checks `adb devices` before running.
 * AGP's connected-device provider honours `ANDROID_SERIAL`, so lock 1 also makes a plugged-in phone
 * invisible to the run rather than merely refused by it.
 */
baselineProfile {
    useConnectedDevices = true
}

tasks.matching { it.name.startsWith("connected") && it.name.endsWith("AndroidTest") }.configureEach {
    // A local, not a script-level `val`: the `doFirst` lambda must capture only this Provider, or the
    // configuration cache refuses to serialise the task ("cannot serialize Gradle script object
    // references") — which is how the first version of this block failed.
    val androidSerial = providers.environmentVariable("ANDROID_SERIAL")
    doFirst {
        check(androidSerial.orNull?.startsWith("emulator-") == true) {
            "Refusing to run: set ANDROID_SERIAL to an emulator (e.g. emulator-5554). This task installs " +
                "and then UNINSTALLS the app, which on a phone destroys its Notion connection and widgets."
        }
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.benchmark.macro.junit4)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}
