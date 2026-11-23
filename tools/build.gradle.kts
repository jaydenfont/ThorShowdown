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

dependencies {
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
