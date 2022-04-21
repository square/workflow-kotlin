plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
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
        api(libs.kotlin.jdk6)
        api(libs.squareup.okio)
        api(libs.kotlinx.coroutines.core)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test.jdk)
        implementation(libs.truth)
      }
    }
  }
}
