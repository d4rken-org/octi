plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "eu.darken.octi.common"
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
    testImplementation(project(":app-common-test"))
    addAndroidCore()
    addAndroidUI()
    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addCompose()
    addNavigation3()
    addTesting()

    // Compile-only so widget-shared extensions (e.g. WidgetCornerRadius) can reference Glance
    // types without dragging the dependency into non-widget consumers of app-common. Each widget
    // module brings glance-appwidget at runtime itself.
    compileOnly("androidx.glance:glance-appwidget:1.2.0-rc01")
}