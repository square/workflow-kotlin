plugins {
  kotlin("multiplatform")
  id("kotlinx-serialization")
}

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":workflow-ui:core-common"))
        implementation(project(":workflow-core"))

        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlin.jdk8)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(project(":workflow-testing"))
        implementation(libs.kotlin.test.jdk)
        implementation(libs.truth)
      }
    }
  }
}
