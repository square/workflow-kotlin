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
  "commonMainCompileOnly"(Dependencies.Annotations.intellijCommon)

  "commonMainApi"(Dependencies.Kotlin.Stdlib.common)
  "jvmMainApi"(Dependencies.Kotlin.Stdlib.jdk6)
  "commonMainApi"(Dependencies.Kotlin.Coroutines.core)
  // For Snapshot.
  "commonMainApi"(Dependencies.okioMultiplatform)

  "jvmTestImplementation"(Dependencies.Kotlin.Coroutines.test)
  "jvmTestImplementation"(Dependencies.Kotlin.Test.jdk)
}
