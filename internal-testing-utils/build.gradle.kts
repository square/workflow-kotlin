plugins {
  id("kotlin-jvm")
  id("published")
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.truth)
}
