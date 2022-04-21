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
        api(libs.kotlin.jdk6)
        api(libs.kotlinx.coroutines.core)
        api(libs.rxjava2.rxjava)

        implementation(libs.kotlinx.coroutines.rx2)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(project(":workflow-testing"))
        implementation(libs.kotlin.test.jdk)
      }
    }
  }
}
