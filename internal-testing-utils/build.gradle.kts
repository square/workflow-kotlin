plugins {
  kotlin("jvm")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(Dependencies.Kotlin.Stdlib.jdk8)

  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
}
