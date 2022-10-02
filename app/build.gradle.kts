plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "androidx.navigation.safeargs.kotlin")
apply(plugin = "com.bugsnag.android.gradle")

android {
    val packageName = ProjectConfig.packageName

    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        applicationId = packageName

        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        versionCode = ProjectConfig.Version.code
        versionName = ProjectConfig.Version.name

        testInstrumentationRunner = "eu.darken.octi.HiltTestRunner"

        buildConfigField("String", "PACKAGENAME", "\"${ProjectConfig.packageName}\"")
        buildConfigField("String", "GITSHA", "\"${lastCommitHash()}\"")
        buildConfigField("String", "BUILDTIME", "\"${buildTime()}\"")

        manifestPlaceholders["bugsnagApiKey"] = getBugSnagApiKey(
            File(System.getProperty("user.home"), ".appconfig/${packageName}/bugsnag.properties")
        ) ?: "fake"
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".appconfig/${packageName}")
        create("releaseFoss") {
            setupCredentials(File(basePath, "signing-foss.properties"))
        }
        create("releaseGplay") {
            setupCredentials(File(basePath, "signing-gplay-upload.properties"))
        }
    }

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            signingConfig = signingConfigs["releaseFoss"]
        }
        create("gplay") {
            dimension = "version"
            signingConfig = signingConfigs["releaseGplay"]
        }
    }

    buildTypes {
        val customProguardRules = fileTree(File("../proguard")) {
            include("*.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
            proguardFiles("proguard-rules-debug.pro")
        }
        create("beta") {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
        release {
            lint {
                abortOnError = true
                fatal.add("StopShip")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            proguardFiles(*customProguardRules.toList().toTypedArray())
        }
    }

    buildOutputs.all {
        val variantOutputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
        val variantName: String = variantOutputImpl.name

        if (listOf("release", "beta").any { variantName.toLowerCase().contains(it) }) {
            val outputFileName = packageName +
                    "-v${defaultConfig.versionName}-${defaultConfig.versionCode}" +
                    "-${variantName.toUpperCase()}-${lastCommitHash()}.apk"

            variantOutputImpl.outputFileName = outputFileName
        }
    }

    buildFeatures {
        viewBinding = true
    }

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
    implementation(project(":syncs-jserver"))
    implementation(project(":syncs-gdrive"))
    implementation(project(":module-core"))
    implementation(project(":modules-meta"))
    implementation(project(":modules-power"))
    implementation(project(":modules-wifi"))

    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()

    addBugsnag()
    addAndroidCore()
    addAndroidUI()
    addWorkerManager()

    addTesting()

    implementation("io.coil-kt:coil:2.0.0-rc02")

    implementation("com.google.android.gms:play-services-auth:20.3.0")
    implementation("com.google.api-client:google-api-client-android:+") {
        exclude("org.apache.httpcomponents")
    }

    implementation("androidx.navigation:navigation-fragment-ktx:2.5.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.5.0")
    androidTestImplementation("androidx.navigation:navigation-testing:2.5.0")

    implementation("androidx.core:core-splashscreen:1.0.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")


}