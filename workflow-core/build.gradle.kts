plugins {
  `kotlin-jvm`
  id("org.jetbrains.dokka")
}



dependencies {
  compileOnly(libs.jetbrains.annotations)

  api(libs.kotlin.jdk6)
  api(libs.kotlinx.coroutines.core)
  // For Snapshot.
  api(libs.squareup.okio)

  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.kotlin.test.jdk)
}
