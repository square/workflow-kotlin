plugins {
  `kotlin-dsl`
}

repositories {
  mavenCentral()
  google()
}

dependencies {
  implementation(libs.android.gradle.plugin)
  implementation(libs.vanniktech.publish)
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}
