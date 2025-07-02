plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "eu.darken.octi.sync"
    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        minSdk = ProjectConfig.minSdk
    }


    setupModuleBuildTypes()

    setupCompileOptions()

    setupKotlinOptions()

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
        tasks.withType<Test> {
            useJUnitPlatform()
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    testImplementation(project(":app-common-test"))

    addAndroidCore()
    addDI()
    addCoroutines()
    addSerialization()
    addTesting()

    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha03")
    implementation("com.google.crypto.tink:tink-android:1.7.0")
}