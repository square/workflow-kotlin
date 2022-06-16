plugins {
  `kotlin-multiplatform`
  id("org.jetbrains.dokka")
  id("app.cash.molecule")
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

kotlin {
  jvm { withJava() }
  // ios()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.RequiresOptIn")
      }
    }
    val commonMain by getting {
      dependencies {
        api(libs.kotlin.jdk6)
        api(libs.kotlinx.coroutines.core)
        api(libs.compose.runtime)
        // For Snapshot.
        api(libs.squareup.okio)
        implementation(libs.molecule.runtime)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlinx.atomicfu)
        implementation(libs.kotlinx.coroutines.test.common)
        implementation(libs.kotlin.test.jdk)
        implementation(libs.molecule.testing)
      }
    }
  }
}
