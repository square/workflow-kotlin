plugins {
  `java-library`
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
}

kotlin {
  jvm {
    withJava()
  }
  iosX64()

  sourceSets {
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("com.squareup.workflow1.InternalWorkflowApi")
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

dependencies {
  compileOnly(Dependencies.Annotations.intellij)

  "commonMainApi"(Dependencies.Kotlin.Stdlib.common)
  "jvmMainApi"(Dependencies.Kotlin.Stdlib.jdk6)
  "commonMainApi"(Dependencies.Kotlin.Coroutines.core)
  // For Snapshot.
  "commonMainApi"(Dependencies.okioMultiplatform)

  "jvmTestImplementation"(Dependencies.Kotlin.Coroutines.test)
  "jvmTestImplementation"(Dependencies.Kotlin.Test.jdk)

  "commonTestImplementation"(Dependencies.Kotlin.Test.annotations)
  "commonTestImplementation"(Dependencies.Kotlin.Test.common)
}
