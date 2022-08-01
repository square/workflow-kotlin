package com.squareup.workflow1.buildsrc

import org.gradle.kotlin.dsl.getValue
import org.gradle.kotlin.dsl.getting
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

fun KotlinMultiplatformExtension.iosWithSimulatorArm64() {
  ios()
  iosSimulatorArm64()

  val iosMain by sourceSets.getting
  val iosSimulatorArm64Main by sourceSets.getting
  iosSimulatorArm64Main.dependsOn(iosMain)

  val iosTest by sourceSets.getting
  val iosSimulatorArm64Test by sourceSets.getting
  iosSimulatorArm64Test.dependsOn(iosTest)
}
