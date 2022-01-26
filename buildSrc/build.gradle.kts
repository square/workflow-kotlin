plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation(libs.android.gradle.plugin)
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
