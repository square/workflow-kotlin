plugins {
  `java-library`
  kotlin("jvm")
  kotlin("kapt")
  `maven-publish`
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.squareup.moshi.codegen)

  kapt(libs.squareup.moshi.codegen)

  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  implementation(libs.squareup.moshi.adapters)
  implementation(libs.squareup.moshi)

  testImplementation(libs.kotlin.test.jdk)
}
