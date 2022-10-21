plugins {
  `kotlin-multiplatform`
  published
  id("app.cash.molecule")
}

kotlin {
  jvm { withJava() }
  ios()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.RequiresOptIn")
      }
    }
    val commonMain by getting {
      dependencies {
        api(project(":workflow-core"))
        api(project(":workflow-runtime"))
        api(libs.kotlin.jdk6)
        api(libs.compose.runtime)
        api(libs.kotlinx.coroutines.core)
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
