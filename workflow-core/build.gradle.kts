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
        compileOnly(libs.jetbrains.annotations)

        api(libs.kotlin.jdk6)
        api(libs.kotlinx.coroutines.core)
        // For Snapshot.
        api(libs.squareup.okio)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlin.test.jdk)
      }
    }
  }
}
