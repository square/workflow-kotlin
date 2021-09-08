plugins {
  kotlin("multiplatform")
}
kotlin {
  jvm()
  iosX64()

  sourceSets {
    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
      }
    }
  }

  sourceSets["commonMain"].dependencies {
    implementation(project(":workflow-ui:backstack-common"))
    implementation(project(":workflow-ui:modal-common"))
    implementation(project(":workflow-core"))
  }

  sourceSets["jvmTest"].dependencies {
    implementation(Dependencies.Kotlin.Stdlib.jdk6)
    implementation(Dependencies.Kotlin.Test.jdk)
    implementation(Dependencies.Test.hamcrestCore)
    implementation(Dependencies.Test.junit)
    implementation(Dependencies.Test.truth)
    implementation(project(":workflow-testing"))
  }
}
