plugins {
  kotlin("jvm")

  id("com.rickbusarow.gradle-dependency-sync") version "0.11.4"
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)



  dependencySync("com.android.tools.build:gradle:7.0.0")

}
