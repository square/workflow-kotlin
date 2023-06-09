import com.squareup.workflow1.buildsrc.kotlinCommonSettings

plugins {
  kotlin("android")
}

extensions.getByType(JavaPluginExtension::class).apply {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

project.kotlinCommonSettings(bomConfigurationName = "implementation")
