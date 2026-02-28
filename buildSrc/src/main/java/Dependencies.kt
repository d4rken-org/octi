import com.android.build.gradle.BaseExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.DependencyHandlerScope
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

private fun DependencyHandler.implementation(dependencyNotation: Any): Dependency? =
    add("implementation", dependencyNotation)

private fun DependencyHandler.testImplementation(dependencyNotation: Any): Dependency? =
    add("testImplementation", dependencyNotation)

private fun DependencyHandler.androidTestImplementation(dependencyNotation: Any): Dependency? =
    add("androidTestImplementation", dependencyNotation)

private fun DependencyHandler.`testRuntimeOnly`(dependencyNotation: Any): Dependency? =
    add("testRuntimeOnly", dependencyNotation)

private fun DependencyHandler.`debugImplementation`(dependencyNotation: Any): Dependency? =
    add("debugImplementation", dependencyNotation)

private fun DependencyHandler.ksp(dependencyNotation: Any): Dependency? =
    add("ksp", dependencyNotation)

private fun DependencyHandler.kspTest(dependencyNotation: Any): Dependency? =
    add("kspTest", dependencyNotation)

private fun DependencyHandler.kspAndroidTest(dependencyNotation: Any): Dependency? =
    add("kspAndroidTest", dependencyNotation)

fun Project.setupModule() {
    extensions.configure<BaseExtension> {
        compileOptions {
            isCoreLibraryDesugaringEnabled = true
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-Xannotation-default-target=param-property",
                "-opt-in=kotlin.ExperimentalStdlibApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.coroutines.FlowPreview",
                "-opt-in=kotlin.time.ExperimentalTime",
                "-opt-in=kotlin.RequiresOptIn",
            )
        }
    }
}

fun DependencyHandlerScope.addDI() {
    implementation("com.google.dagger:dagger:${Versions.Dagger.core}")
    implementation("com.google.dagger:dagger-android:${Versions.Dagger.core}")

    ksp("com.google.dagger:dagger-compiler:${Versions.Dagger.core}")
    ksp("com.google.dagger:dagger-android-processor:${Versions.Dagger.core}")

    implementation("com.google.dagger:hilt-android:${Versions.Dagger.core}")
    ksp("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    testImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kspTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")

    androidTestImplementation("com.google.dagger:hilt-android-testing:${Versions.Dagger.core}")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:${Versions.Dagger.core}")
}

fun DependencyHandlerScope.addCoroutines() {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.Kotlin.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.Kotlin.coroutines}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.Kotlin.coroutines}")

    testImplementation("org.jetbrains.kotlin:kotlin-reflect:${Versions.Kotlin.core}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.Kotlin.coroutines}")
}

fun DependencyHandlerScope.addSerialization() {
    implementation("com.squareup.moshi:moshi:${Versions.Moshi.core}")
    implementation("com.squareup.moshi:moshi-adapters:${Versions.Moshi.core}")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:${Versions.Moshi.core}")
}

fun DependencyHandlerScope.addIO() {
    implementation("com.squareup.okio:okio:3.1.0")
}

fun DependencyHandlerScope.addRetrofit() {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")
}

fun DependencyHandlerScope.addAndroidCore() {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.4.2")
    implementation("androidx.annotation:annotation:1.4.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
}

fun DependencyHandlerScope.addWorkerManager() {
    implementation("androidx.work:work-runtime:${Versions.AndroidX.WorkManager.core}")
    testImplementation("androidx.work:work-testing:${Versions.AndroidX.WorkManager.core}")

    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")
}

fun DependencyHandlerScope.addAndroidUI() {
    implementation("androidx.activity:activity-ktx:1.9.1")

    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.4")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
}

fun DependencyHandlerScope.addCompose() {
    val composeBom = add("implementation", platform("androidx.compose:compose-bom:${Versions.Compose.bom}"))
    add("androidTestImplementation", platform("androidx.compose:compose-bom:${Versions.Compose.bom}"))

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    implementation("androidx.hilt:hilt-navigation-compose:1.3.0-alpha01")
}

fun DependencyHandlerScope.addNavigation3() {
    implementation("androidx.navigation3:navigation3-runtime:${Versions.Navigation3.core}")
    implementation("androidx.navigation3:navigation3-ui:${Versions.Navigation3.core}")

    implementation("androidx.lifecycle:lifecycle-viewmodel-navigation3:2.10.0")

    implementation("androidx.compose.material3.adaptive:adaptive-navigation3:1.3.0-alpha06")
}

fun DependencyHandlerScope.addKotlinxSerialization() {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.Serialization.core}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.Serialization.core}")
}

fun DependencyHandlerScope.addTesting() {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.8.2")
    testImplementation("androidx.test:core-ktx:1.6.1")

    testImplementation("io.mockk:mockk:1.13.13")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")

    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.8.2")


    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    androidTestImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    androidTestImplementation("io.kotest:kotest-property-jvm:5.9.1")

    testImplementation("android.arch.core:core-testing:1.1.1")
    androidTestImplementation("android.arch.core:core-testing:1.1.1")
    debugImplementation("androidx.test:core-ktx:1.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test.espresso.idling:idling-concurrent:3.6.1")
}
