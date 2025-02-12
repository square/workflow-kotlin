plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.okio)
  api(libs.androidx.compose.runtime)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.truth)
  testImplementation(libs.turbine)
}
