@file:Suppress("SuspiciousCollectionReassignment")

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

        api(project(":workflow-core"))
        api(project(":workflow-runtime"))
        api(libs.kotlin.jdk7)

        implementation(project(":internal-testing-utils"))
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.kotlin.reflect)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test.jdk)
        implementation(libs.mockito.kotlin)
        implementation(libs.mockk)
      }
    }
  }
}
