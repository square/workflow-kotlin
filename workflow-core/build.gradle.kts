plugins {
  `java-library`
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(libs.annotations.intellij)

  api(libs.kotlin.jdk6)
  api(libs.kotlin.coroutines.core)
  // For Snapshot.
  api(libs.okio)

  testImplementation(libs.test.coroutines)
  testImplementation(libs.test.kotlin.jdk)
}
