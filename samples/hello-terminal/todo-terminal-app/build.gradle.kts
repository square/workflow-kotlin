plugins {
  application
  id("kotlin-jvm")
}

application.mainClassName = "com.squareup.sample.hellotodo.MainKt"

dependencies {
  implementation(libs.kotlinx.coroutines.core)

  implementation(project(":samples:hello-terminal:terminal-workflow"))
  implementation(project(":workflow-core"))
}
