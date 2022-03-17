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
  compileOnly(gradleApi())

  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)

  ksp(libs.squareup.moshi.codegen)
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
