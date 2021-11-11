plugins {
  id("com.android.library")
  kotlin("android")
  id("org.jetbrains.dokka")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))
apply(from = rootProject.file(".buildscript/configure-android-defaults.gradle"))
apply(from = rootProject.file(".buildscript/android-ui-tests.gradle"))

dependencies {
  compileOnly(Dependencies.AndroidX.viewbinding)

  api(project(":workflow-core"))
  // Needs to be API for the WorkflowInterceptor argument to WorkflowRunner.Config.
  api(project(":workflow-runtime"))
  api(project(":workflow-ui:core-common"))

  api(Dependencies.AndroidX.transition)
  api(Dependencies.Kotlin.Stdlib.jdk6)

  implementation(Dependencies.AndroidX.activity)
  implementation(Dependencies.AndroidX.coreKtx)
  implementation(Dependencies.AndroidX.fragment)
  implementation(Dependencies.AndroidX.Lifecycle.ktx)
  implementation(Dependencies.AndroidX.savedstate)
  implementation(Dependencies.Kotlin.Coroutines.android)
  implementation(Dependencies.Kotlin.Coroutines.core)

  testImplementation(Dependencies.Kotlin.Coroutines.test)
  testImplementation(Dependencies.Kotlin.Test.jdk)
  testImplementation(Dependencies.Kotlin.Test.mockito)
  testImplementation(Dependencies.Test.AndroidX.core)
  testImplementation(Dependencies.Test.AndroidX.lifecycle)
  testImplementation(Dependencies.Test.junit)
  testImplementation(Dependencies.Test.truth)
  testImplementation(Dependencies.Test.robolectric)

  androidTestImplementation(Dependencies.AndroidX.appcompat)
  androidTestImplementation(Dependencies.AndroidX.Lifecycle.viewModel)
  androidTestImplementation(Dependencies.Test.truth)
}
