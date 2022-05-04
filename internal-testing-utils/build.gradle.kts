plugins {
  `kotlin-jvm`
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
