plugins {
  application
  kotlin("multiplatform")
}

application.mainClassName = "com.squareup.sample.helloterminal.MainKt"

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        implementation(project(":samples:hello-terminal:terminal-workflow"))
        implementation(project(":workflow-core"))
        implementation(project(":workflow-runtime"))
      }
    }
  }
}
