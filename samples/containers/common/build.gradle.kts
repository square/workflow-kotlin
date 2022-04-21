plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":workflow-ui:container-common"))
        implementation(project(":workflow-core"))

        implementation(libs.kotlin.jdk6)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test.jdk)
        implementation(libs.hamcrest)
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(project(":workflow-testing"))
      }
    }
  }
}
