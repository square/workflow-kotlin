plugins {
  `kotlin-dsl`
  alias(libs.plugins.google.ksp)
  alias(libs.plugins.ktlint)
}

repositories {
  mavenCentral()
  google()
  maven("https://plugins.gradle.org/m2/")
}

dependencies {

  implementation(platform(libs.kotlin.bom))

  compileOnly(gradleApi())

  implementation(libs.android.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.dropbox.dependencyGuard)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.vanniktech.publish)

  ksp(libs.squareup.moshi.codegen)
}

java {
  // Java 11 is required when compiling against AGP 7.4.0+
  toolchain.languageVersion.set(JavaLanguageVersion.of(11))
}

kotlin {
  jvmToolchain(11)
}
