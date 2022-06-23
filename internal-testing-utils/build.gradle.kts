plugins {
  `kotlin-jvm`
  published
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
