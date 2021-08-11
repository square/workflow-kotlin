plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
}

version = 1.0

kotlin {
  jvm()
  iosX64()

  sourceSets {
    val commonMain by getting {
      dependencies {
        api(project(":workflow-core"))
      }
    }
  }

  cocoapods {
    homepage = "https://square.github.io/workflow/"
    summary = "Shared code for multiplatform workflow sample"
  }
}
