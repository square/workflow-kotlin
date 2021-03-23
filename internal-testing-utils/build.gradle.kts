plugins {
  kotlin("jvm")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  testImplementation(libs.test.kotlin.jdk)
  testImplementation(libs.test.junit)
  testImplementation(libs.test.truth)
}
