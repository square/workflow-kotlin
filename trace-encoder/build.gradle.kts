plugins {
  `kotlin-jvm`
  id("com.google.devtools.ksp")
  id("com.vanniktech.maven.publish")
}

dependencies {
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.squareup.moshi.codegen)

  ksp(libs.squareup.moshi.codegen)

  api(libs.kotlin.jdk8)
  api(libs.kotlinx.coroutines.core)

  implementation(libs.squareup.moshi.adapters)
  implementation(libs.squareup.moshi)

  testImplementation(libs.kotlin.test.jdk)
}
