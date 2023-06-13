plugins {
  application
  id("kotlin-jvm")
}

application.mainClass.set("com.squareup.sample.helloterminal.MainKt")

dependencies {
  implementation(libs.kotlinx.coroutines.core)

  implementation(project(":samples:hello-terminal:terminal-workflow"))
  implementation(project(":workflow-core"))
}
