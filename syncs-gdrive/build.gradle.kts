plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}

apply(plugin = "dagger.hilt.android.plugin")

android {
    namespace = "eu.darken.octi.syncs.gdrive"
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
    implementation(project(":sync-core"))

    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()
    addTesting()

    implementation("com.google.android.gms:play-services-auth:20.3.0")
    implementation("com.google.api-client:google-api-client-android:+") {
        exclude("org.apache.httpcomponents")
    }

    implementation("com.google.apis:google-api-services-drive:+") {
        exclude("org.apache.httpcomponents")
    }
}