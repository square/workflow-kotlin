plugins {
  kotlin("multiplatform")
  kotlin("native.cocoapods")
}

version = 1.0

kotlin {
  jvm()
  iosX64()

  sourceSets {
    /*val commonMain by getting {
      dependencies {
        api(project(":workflow-core"))
      }
    }*/

    all {
      languageSettings.apply {
        useExperimentalAnnotation("kotlin.RequiresOptIn")
        useExperimentalAnnotation("kotlinx.coroutines.ExperimentalCoroutinesApi")
      }
    }
  }

  cocoapods {
    homepage = "https://square.github.io/workflow/"
    summary = "Shared code for multiplatform tictactoe sample"
  }

  sourceSets["commonMain"].dependencies {
    api(project(":workflow-core"))
  }

  sourceSets["jvmMain"].dependencies {
    implementation(project(":samples:containers:common"))
    implementation(project(":workflow-ui:backstack-common"))
    implementation(project(":workflow-ui:modal-common"))
    implementation(project(":workflow-rx2"))
  }

  sourceSets["jvmTest"].dependencies {
    implementation(Dependencies.Test.hamcrestCore)
    implementation(Dependencies.Test.junit)
    implementation(Dependencies.Test.truth)
    implementation(project(":workflow-testing"))
  }
}
