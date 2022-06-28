plugins {
  `kotlin-multiplatform`
  published
  id("app.cash.molecule")
}

kotlin {
  jvm { withJava() }
  // TODO: No native targets yet for Molecule until Compose 1.2.0 available in JB KMP runtime.
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
        api(libs.compose.runtime)
        api(libs.kotlinx.coroutines.core)
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
      }
    }
  }
}
