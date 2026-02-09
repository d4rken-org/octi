plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
}
apply(plugin = "dagger.hilt.android.plugin")
apply(plugin = "androidx.navigation.safeargs.kotlin")

val commitHashProvider = providers.of(CommitHashValueSource::class) {}

android {
    val packageName = ProjectConfig.packageName

    namespace = ProjectConfig.packageName

    compileSdk = ProjectConfig.compileSdk

    defaultConfig {
        applicationId = packageName

        minSdk = ProjectConfig.minSdk
        targetSdk = ProjectConfig.targetSdk

        versionCode = ProjectConfig.Version.code
        versionName = ProjectConfig.Version.name

        testInstrumentationRunner = "eu.darken.octi.HiltTestRunner"

        buildConfigField("String", "PACKAGENAME", "\"${ProjectConfig.packageName}\"")
        buildConfigField("String", "GITSHA", "\"${commitHashProvider.get()}\"")
        buildConfigField("String", "VERSION_CODE", "\"${ProjectConfig.Version.code}\"")
        buildConfigField("String", "VERSION_NAME", "\"${ProjectConfig.Version.name}\"")
    }

    signingConfigs {
        val basePath = File(System.getProperty("user.home"), ".appconfig/${packageName}")
        create("releaseFoss") {
            setupCredentials(File(basePath, "signing-foss.properties"))
        }
        create("releaseGplay") {
            setupCredentials(File(basePath, "signing-gplay.properties"))
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
        val customProguardRules = fileTree(File(projectDir, "proguard")) {
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    tasks.withType<Test> {
        useJUnitPlatform()
    }
    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

androidComponents {
    onVariants { variant ->
        val buildType = variant.buildType ?: return@onVariants
        if (buildType != "release" && buildType != "beta") return@onVariants

        val formattedVariantName = variant.name
            .replace(Regex("([a-z])([A-Z])"), "$1-$2")
            .uppercase()

        val apkFolder = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK)
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        val packageName = ProjectConfig.packageName

        val renameTask = tasks.register("rename${variant.name.replaceFirstChar { it.uppercase() }}Apk") {
            inputs.files(apkFolder)
            outputs.upToDateWhen { false }

            doLast {
                val builtArtifacts = loader.load(apkFolder.get()) ?: return@doLast

                builtArtifacts.elements.forEach { element ->
                    val apkFile = File(element.outputFile)
                    val outputFileName = "$packageName-v${element.versionName}-${element.versionCode}-$formattedVariantName.apk"
                    if (apkFile.exists() && apkFile.name != outputFileName) {
                        apkFile.copyTo(File(apkFile.parentFile, outputFileName), overwrite = true)
                    }
                }
            }
        }

        tasks.matching { it.name == "assemble${variant.name.replaceFirstChar { it.uppercase() }}" }.configureEach {
            finalizedBy(renameTask)
        }
    }
}

setupModule()

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
    testImplementation(project(":app-common-test"))
    implementation(project(":sync-core"))
    implementation(project(":syncs-gdrive"))
    implementation(project(":syncs-kserver"))
    implementation(project(":module-core"))
    implementation(project(":modules-meta"))
    implementation(project(":modules-power"))
    implementation(project(":modules-wifi"))
    implementation(project(":modules-apps"))
    implementation(project(":modules-clipboard"))

    addDI()
    addCoroutines()
    addSerialization()
    addIO()
    addRetrofit()

    addAndroidCore()
    addAndroidUI()
    addWorkerManager()

    addTesting()

    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("io.coil-kt:coil:2.0.0-rc02")

    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.api-client:google-api-client-android:+") {
        exclude("org.apache.httpcomponents")
    }

    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.7")

    implementation("androidx.core:core-splashscreen:1.0.0")

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    "gplayImplementation"("com.android.billingclient:billing:8.0.0")
    "gplayImplementation"("com.android.billingclient:billing-ktx:8.0.0")

    implementation("io.coil-kt:coil:2.0.0-rc02")

    implementation("io.github.z4kn4fein:semver:1.4.2")
}