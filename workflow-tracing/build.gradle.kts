plugins {
  kotlin("multiplatform")
  id("com.vanniktech.maven.publish")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

kotlin {
  jvm { withJava() }

  sourceSets {
    val jvmMain by getting {
      dependencies {
        compileOnly(libs.jetbrains.annotations)

        api(project(":trace-encoder"))
        api(project(":workflow-runtime"))
        api(libs.kotlin.jdk8)
        api(libs.kotlinx.coroutines.core)

        implementation(libs.squareup.okio)
        implementation(libs.squareup.moshi.adapters)
        implementation(libs.squareup.moshi)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test.jdk)
        implementation(libs.mockito.kotlin)
      }
    }
  }
}
