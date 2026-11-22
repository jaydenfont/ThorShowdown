plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

/** Tests against the real Showdown server. Run by hand with `liveTest`; never part of `test` or CI. */
val liveTest: SourceSet =
    sourceSets.create("liveTest") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }

configurations[liveTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[liveTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

tasks.register<Test>("liveTest") {
    description = "Runs tests against the live Showdown server (needs network)."
    group = "verification"
    testClassesDirs = liveTest.output.classesDirs
    classpath = liveTest.runtimeClasspath
}

dependencies {
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
