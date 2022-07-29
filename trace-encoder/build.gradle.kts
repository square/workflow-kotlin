plugins {
  `kotlin-jvm`
  id("com.google.devtools.ksp")
  published
}

dependencies {
  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)
  api(libs.squareup.okio)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.squareup.moshi.codegen)

  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)

  ksp(libs.squareup.moshi.codegen)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.core)
  testImplementation(libs.kotlin.test.jdk)
}
