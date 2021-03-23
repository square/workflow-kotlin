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
  api(project(":workflow-ui:core-common"))
  api(libs.kotlin.jdk6)
  api(libs.okio)

  testImplementation(libs.test.kotlin.jdk)
  testImplementation(libs.test.truth)
}
