plugins {
  `kotlin-jvm`
  id("org.jetbrains.dokka")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  api(project(":workflow-ui:core-common"))
  api(libs.kotlin.jdk6)
  api(libs.squareup.okio)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
