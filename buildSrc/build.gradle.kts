@Suppress("UnstableApiUsage")
plugins {
  `kotlin-dsl`
  alias(libs.plugins.google.ksp)
}

repositories {
  mavenCentral()
  google()
}

dependencies {

  implementation(platform(libs.kotlin.bom))

  compileOnly(gradleApi())

  implementation(libs.android.gradle.plugin)
  implementation(libs.dropbox.dependencyGuard)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)

  ksp(libs.squareup.moshi.codegen)
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
