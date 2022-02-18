plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  compileOnly(gradleApi())

  implementation(libs.android.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.kotlinx.binaryCompatibility.gradle.plugin)
  implementation(libs.vanniktech.publish)
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
