package com.squareup.workflow1.buildsrc

import org.gradle.api.Project
import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

fun KotlinMultiplatformExtension.iosWithSimulatorArm64() {
  ios()
  iosSimulatorArm64()

  targets.withType(KotlinNativeTargetWithSimulatorTests::class.java) {
    this.testRuns.configureEach {
      // Kotlin 1.7.2x is hard-coded to "iPhone 13", which is no longer included by default in Xcode
      deviceId = project.properties["workflow.iosSimulatorName"] as? String ?: "iPhone 14"
    }
  }

  val iosMain by sourceSets.getting
  val iosSimulatorArm64Main by sourceSets.getting
  iosSimulatorArm64Main.dependsOn(iosMain)

  val iosTest by sourceSets.getting
  val iosSimulatorArm64Test by sourceSets.getting
  iosSimulatorArm64Test.dependsOn(iosTest)
}
