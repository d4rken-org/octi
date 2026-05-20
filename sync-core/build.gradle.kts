plugins {
    id("com.android.library")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "eu.darken.octi.sync"
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
            // Forward the opt-in flag for InteropFixtureGeneratorTest. Gradle does NOT pass
            // -D props to test JVMs by default, so the gate would silently no-op otherwise.
            // The verify side (InteropFixtureVerifyTest) is always on — no flag needed.
            System.getProperty("generateInteropFixtures")?.let { systemProperty("generateInteropFixtures", it) }
        }
    }
}

setupModule()

// Real Gradle Test task that regenerates the cross-repo interop fixtures under
// `sync-core/src/test/resources/interop/`. Mirrors the unit-test classpath via
// `afterEvaluate` because AGP wires up testDebugUnitTest lazily during the variant API.
//
// `outputs.upToDateWhen { false }` forces a re-run on every invocation — the system
// property isn't part of Gradle's input hashing for the underlying Test task, so
// without this the second invocation would be cached as up-to-date.
afterEvaluate {
    val source = tasks.named<Test>("testDebugUnitTest").get()
    val fixturesDir = project.layout.projectDirectory
        .dir("src/test/resources/interop")
        .asFile
        .absolutePath
    tasks.register<Test>("generateInteropFixtures") {
        group = "verification"
        description = "Regenerate cross-repo interop fixtures under sync-core/src/test/resources/interop/. " +
            "Decrypted by InteropFixtureVerifyTest on every PR run; consumed by octi-web and octi-desktop."
        testClassesDirs = source.testClassesDirs
        classpath = source.classpath
        useJUnitPlatform()
        filter {
            includeTestsMatching("eu.darken.octi.sync.core.interop.InteropFixtureGeneratorTest")
        }
        systemProperty("generateInteropFixtures", "true")
        systemProperty("interopFixturesOutDir", fixturesDir)
        outputs.upToDateWhen { false }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:${Versions.Desugar.core}")

    implementation(project(":app-common"))
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

    implementation("androidx.security:security-crypto-ktx:1.1.0")
    implementation("com.google.crypto.tink:tink-android:1.16.0")
    implementation("org.conscrypt:conscrypt-android:${Versions.Conscrypt.core}")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}