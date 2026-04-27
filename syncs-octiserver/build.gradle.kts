plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "eu.darken.octi.syncs.octiserver"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
    }

    setupModuleBuildTypes()

    buildFeatures {
        viewBinding = false
        compose = true
    }

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
    testImplementation(project(":app-common-test"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addCompose()
    addNavigation3()
    addRetrofit()
    addTesting()

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.1")
}