plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

application {
    mainClass.set("com.example.thorshowdown.tools.GenerateMoveDataKt")
}

/**
 * Regenerates the app's bundled move data from Showdown's published data (needs network).
 * Run by hand after new moves are released; never part of the build or CI.
 */
tasks.register<JavaExec>("generateMoveData") {
    description = "Downloads Showdown's move data and writes app/src/main/assets/data/moves.json."
    group = "tools"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    args(rootProject.file("app/src/main/assets/data/moves.json").absolutePath)
}

/** The Android SDK's adb: `sdk.dir` from local.properties, else ANDROID_HOME. */
fun adbPath(): String {
    val localProperties = rootProject.file("local.properties")
    val sdkDir =
        localProperties
            .takeIf { it.exists() }
            ?.readLines()
            ?.firstOrNull { it.startsWith("sdk.dir=") }
            ?.substringAfter("=")
            ?.replace("\\:", ":")
            ?.replace("\\\\", "\\")
            ?: System.getenv("ANDROID_HOME")
            ?: error("Set sdk.dir in local.properties or ANDROID_HOME")
    return File(sdkDir, "platform-tools/adb").absolutePath
}

/**
 * Runs the emulator workflow scenarios (one per spec in qa/scenarios) with screenshots, video and log checks,
 * and writes qa-runs/<timestamp>/report.html. Emulator only; never part of the build or CI.
 * Options: -Pscenarios=Login,BattleTurn (default all), -Pserial=emulator-5554.
 */
tasks.register<JavaExec>("qaRun") {
    description = "Runs the workflow scenarios on the emulator and writes a QA report."
    group = "verification"
    dependsOn(":app:installDebug", ":app:installDebugAndroidTest")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.thorshowdown.tools.qa.QaRunMainKt")
    // Plain values and providers only: the configuration cache can't keep references to the script.
    val root = rootProject.projectDir
    val adb = adbPath()
    val serial = providers.gradleProperty("serial").orElse("emulator-5554")
    val scenarios = providers.gradleProperty("scenarios").orElse("")
    val commit =
        providers
            .exec { commandLine("git", "rev-parse", "--short", "HEAD") }
            .standardOutput.asText
            .map { it.trim() }
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "adb=$adb",
                "serial=${serial.get()}",
                "specs=${File(root, "qa/scenarios")}",
                "runs=${File(root, "qa-runs")}",
                "canaries=${File(root, "app/src/androidTest/java/com/example/thorshowdown/qa/Canaries.kt")}",
                "commit=${commit.get()}",
                "scenarios=${scenarios.get()}",
            )
        },
    )
}

/** Re-renders a run's report after the QA agent writes review.json: -PrunDir=qa-runs/<timestamp>. */
tasks.register<JavaExec>("qaReport") {
    description = "Re-renders a QA run's HTML report, merging review.json."
    group = "verification"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.example.thorshowdown.tools.qa.QaReportMainKt")
    val root = rootProject.projectDir
    val runDir = providers.gradleProperty("runDir")
    argumentProviders.add(CommandLineArgumentProvider { listOf(File(root, runDir.get()).absolutePath) })
}

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
