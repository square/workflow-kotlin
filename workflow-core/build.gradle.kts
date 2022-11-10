import com.squareup.workflow1.buildsrc.iosWithSimulatorArm64

plugins {
  `kotlin-multiplatform`
  published
}

kotlin {
  iosWithSimulatorArm64()
  jvm { withJava() }
  js { browser() }
}

dependencies {
  commonMainApi(libs.kotlin.jdk6)
  commonMainApi(libs.kotlinx.coroutines.core)
  // For Snapshot.
  commonMainApi(libs.squareup.okio)

  commonTestImplementation(libs.kotlinx.atomicfu)
  commonTestImplementation(libs.kotlinx.coroutines.test.common)
  commonTestImplementation(libs.kotlin.test.jdk)
}
