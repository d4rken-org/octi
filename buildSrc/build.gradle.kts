plugins {
    `kotlin-dsl`
    `java-library`
}

repositories {
    google()
    mavenCentral()
}
dependencies {
    implementation("com.android.tools.build:gradle:9.0.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.10")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.6")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.10")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.3.10")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.android.tools:common:31.13.2")
}