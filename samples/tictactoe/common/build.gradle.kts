plugins {
  kotlin("multiplatform")
}

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":samples:containers:common"))
        implementation(project(":workflow-ui:container-common"))
        implementation(project(":workflow-core"))
        implementation(project(":workflow-rx2"))

        implementation(libs.kotlin.jdk6)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.hamcrest)
        implementation(libs.junit)
        implementation(libs.truth)
        implementation(project(":workflow-testing"))
      }
    }
  }
}
