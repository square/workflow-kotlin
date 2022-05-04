import com.squareup.workflow1.buildsrc.kotlinCommonSettings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
}

extensions.getByType(JavaPluginExtension::class).apply {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }

  project.kotlinCommonSettings(this)
}
