plugins {
    `kotlin-dsl`
    `java-library`
}

repositories {
    google()
    mavenCentral()
}
dependencies {
    implementation("com.android.tools.build:gradle:8.13.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    implementation("com.squareup:javapoet:1.13.0")
    implementation("com.android.tools:common:31.13.2")
}