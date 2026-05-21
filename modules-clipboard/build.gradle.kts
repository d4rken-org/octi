plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "eu.darken.octi.modules.clipboard"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
    }

    buildFeatures {
        compose = true
    }

    setupModuleBuildTypes()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
            // InteropFixtureSync (in app-common-test) reads this to locate fixture-lock.json
            // and write its cache at <rootDir>/.cache/interop-fixtures/. Pinning it here makes
            // IDE-direct and Gradle invocations agree.
            systemProperty("interopRepoRoot", rootProject.projectDir.absolutePath)
            // Pin fixture-lock.json + INTEROP_FIXTURE_OVERRIDES as task inputs so a lock bump
            // or override change invalidates the cached test result and forces a re-fetch +
            // re-verify, instead of being skipped UP-TO-DATE.
            inputs.file(rootProject.layout.projectDirectory.file("fixture-lock.json"))
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.property(
                "INTEROP_FIXTURE_OVERRIDES",
                providers.environmentVariable("INTEROP_FIXTURE_OVERRIDES").orElse(""),
            )
        }
    }
}

setupModule()

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    testImplementation(project(":app-common-test"))
    implementation(project(":module-core"))
    implementation(project(":sync-core"))
    implementation(project(":modules-meta"))
    implementation("androidx.glance:glance-appwidget:1.2.0-rc01")
    implementation("androidx.glance:glance-material3:1.2.0-rc01")
    testImplementation("androidx.glance:glance-appwidget-testing:1.2.0-rc01")
    testImplementation("androidx.glance:glance-testing:1.2.0-rc01")
    testImplementation("org.robolectric:robolectric:4.16.1")

    addAndroidCore()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addCompose()
    addTesting()
}