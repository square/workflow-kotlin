plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":workflow-core"))
        implementation(project(":workflow-runtime"))

        implementation(libs.lanterna)
      }
    }
  }
}
