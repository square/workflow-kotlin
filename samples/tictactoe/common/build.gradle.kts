plugins {
  `kotlin-jvm`
}

dependencies {
  api(libs.rxjava2.rxjava)
  api(libs.squareup.okio)

  api(project(":workflow-core"))
  api(project(":workflow-ui:core-common"))

  implementation(libs.kotlin.jdk6)
  implementation(libs.kotlinx.coroutines.core)

  implementation(project(":samples:containers:common"))
  implementation(project(":workflow-rx2"))

  testImplementation(libs.hamcrest)
  testImplementation(libs.junit)
  testImplementation(libs.truth)

  testImplementation(project(":workflow-testing"))
}
