plugins {
  `java-library`
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
}

kotlin {
  jvm()
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  "jvmMainApi"(Dependencies.Kotlin.Stdlib.jdk6)
  "jvmMainApi"(Dependencies.Kotlin.Coroutines.core)
  // For Snapshot.
  "jvmMainApi"(Dependencies.okio)

  "jvmTestImplementation"(Dependencies.Kotlin.Coroutines.test)
  "jvmTestImplementation"(Dependencies.Kotlin.Test.jdk)
}
