plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "eu.darken.octi.syncs.kserver"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
    }

    setupModuleBuildTypes()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

setupModule()

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    implementation(project(":sync-core"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()
    addTesting()
}