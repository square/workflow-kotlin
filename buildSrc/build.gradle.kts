@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  `kotlin-dsl`
  alias(libs.plugins.google.ksp)
  id("java-gradle-plugin")
}

repositories {
  mavenCentral()
  google()
  maven("https://plugins.gradle.org/m2/")
}

gradlePlugin {
  plugins {

    register("dokka") {
      id = "dokka"
      implementationClass = "com.squareup.workflow1.buildsrc.dokka.DokkaConventionPlugin"
    }
    register("dokka-version-archive") {
      id = "dokka-version-archive"
      implementationClass = "com.squareup.workflow1.buildsrc.dokka.DokkaVersionArchivePlugin"
    }
  }
}

dependencies {

  implementation(platform(libs.kotlin.bom))

  compileOnly(gradleApi())

  implementation(libs.android.gradle.plugin)
  implementation(libs.dokka.gradle.plugin)
  implementation(libs.dokka.versioning)
  implementation(libs.dropbox.dependencyGuard)
  implementation(libs.kotlin.gradle.plugin)
  implementation(libs.ktlint.core)
  implementation(libs.kotlinter)
  implementation(libs.squareup.moshi)
  implementation(libs.squareup.moshi.adapters)
  implementation(libs.vanniktech.publish)

  ksp(libs.squareup.moshi.codegen)
}

java {
  // Java 11 is required when compiling against AGP 7.4.0+
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}
