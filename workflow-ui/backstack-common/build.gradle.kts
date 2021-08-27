plugins {
  `java-library`
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
}

kotlin {
  jvm {
    withJava()
  }
  iosX64()

  sourceSets {
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("com.squareup.workflow1.ui.WorkflowUiExperimentalApi")
      }
    }

    val commonMain by getting {
      dependencies {
        api(project(":workflow-ui:core-common"))
      }
    }
    val jvmMain by getting {
      dependencies {
        api(Dependencies.Kotlin.Stdlib.jdk6)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(Dependencies.Kotlin.Test.jdk)
        implementation(Dependencies.Test.truth)
      }
    }
  }
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

/*dependencies {
  api(project(":workflow-ui:core-common"))
  api(Dependencies.Kotlin.Stdlib.jdk6)
  api(Dependencies.okio)

  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Test.truth)
}*/
