plugins {
  `kotlin-jvm`
  id("org.jetbrains.dokka")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  api(libs.kotlin.jdk6)
  api(libs.squareup.okio)
  api(libs.kotlinx.coroutines.core)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
