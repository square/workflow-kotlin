plugins {
  kotlin("jvm")

  id("com.rickbusarow.gradle-dependency-sync") version "0.11.4"
}

apply(from = rootProject.file(".buildscript/configure-maven-publish.gradle"))

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  implementation(libs.kotlin.jdk8)

  testImplementation(libs.kotlin.test.jdk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)







  dependencySync("com.android.tools.build:gradle:7.0.3")
  dependencySync("androidx.activity:activity:1.3.1")
  dependencySync("androidx.activity:activity-ktx:1.3.1")
  dependencySync("androidx.appcompat:appcompat:1.3.1")
  dependencySync("androidx.compose.foundation:foundation:1.1.0-rc01")
  dependencySync("androidx.compose.material:material:1.1.0-rc01")
  dependencySync("androidx.compose.ui:ui:1.1.0-rc01")
  dependencySync("androidx.compose.ui:ui-test-junit4:1.1.0-rc01")
  dependencySync("androidx.compose.ui:ui-tooling:1.1.0-rc01")
  dependencySync("androidx.constraintlayout:constraintlayout:2.1.0")
  dependencySync("androidx.databinding:viewbinding:7.0.3")
  dependencySync("androidx.fragment:fragment:1.3.6")
  dependencySync("androidx.fragment:fragment-ktx:1.3.6")
  dependencySync("androidx.gridlayout:gridlayout:1.0.0")
  dependencySync("androidx.lifecycle:lifecycle-runtime-ktx:1.1.0")
  dependencySync("androidx.lifecycle:lifecycle-runtime-testing:1.1.0")
  dependencySync("androidx.lifecycle:lifecycle-viewmodel:1.1.0")
  dependencySync("androidx.lifecycle:lifecycle-viewmodel-ktx:1.1.0")
  dependencySync("androidx.lifecycle:lifecycle-viewmodel-savedstate:1.1.0")
  dependencySync("androidx.recyclerview:recyclerview:1.2.1")
  dependencySync("androidx.savedstate:savedstate:1.1.0")
  dependencySync("androidx.test:core:1.4.1")
  dependencySync("androidx.test:runner:1.4.1")
  dependencySync("androidx.test.ext:junit:4.13.2")
  dependencySync("androidx.test.ext:truth:1.4.1")
  dependencySync("androidx.transition:transition:1.4.1")
  dependencySync("com.google.android.material:material:1.3.0")
  dependencySync("com.google.truth:truth:1.1.3")
  dependencySync("org.jetbrains.dokka:dokka-gradle-plugin:1.5.31")
  dependencySync("org.jetbrains.kotlin:kotlin-stdlib-common:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-stdlib:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-reflect:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-serialization:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-test-annotations-common:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-test-common:1.6.10")
  dependencySync("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")
  dependencySync("org.jetbrains.kotlinx:binary-compatibility-validator:0.6.0")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.1")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-rx2:1.5.1")
  dependencySync("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.5.1")
  dependencySync("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.2")
  dependencySync("org.jlleitschuh.gradle:ktlint-gradle:10.2.1")
  dependencySync("org.openjdk.jmh:jmh-core:1.32")
  dependencySync("org.openjdk.jmh:jmh-generator-annprocess:1.32")
  dependencySync("junit:junit:4.13.2")
  dependencySync("org.mockito:mockito-core:3.3.3")
  dependencySync("org.mockito.kotlin:mockito-kotlin:3.3.3")
  dependencySync("io.mockk:mockk:1.11.0")
  dependencySync("org.robolectric:robolectric:4.5.1")
  dependencySync("io.reactivex.rxjava2:rxandroid:2.2.21")
  dependencySync("io.reactivex.rxjava2:rxjava:2.2.21")
  dependencySync("com.squareup.cycler:cycler:0.1.9")
  dependencySync("com.squareup.leakcanary:leakcanary-android:2.7")
  dependencySync("com.squareup.leakcanary:leakcanary-android-instrumentation:2.7")
  dependencySync("com.squareup.moshi:moshi:1.13.0")
  dependencySync("com.squareup.moshi:moshi-adapters:1.13.0")
  dependencySync("com.squareup.moshi:moshi-kotlin-codegen:1.13.0")
  dependencySync("com.squareup.okio:okio:2.10.0")
  dependencySync("com.squareup.radiography:radiography:2.4.0")
  dependencySync("com.squareup:seismic:1.0.2")
  dependencySync("com.jakewharton.timber:timber:4.7.1")
  dependencySync("com.vanniktech:gradle-maven-publish-plugin:0.18.0")
  dependencySync("com.android.tools:desugar_jdk_libs:1.1.5")
  dependencySync("androidx.test.espresso:espresso-core:3.3.0")
  dependencySync("androidx.test.espresso:espresso-idling-resource:3.3.0")
  dependencySync("androidx.test.espresso:espresso-intents:3.3.0")
  dependencySync("androidx.test.uiautomator:uiautomator:2.2.0")
  dependencySync("com.googlecode.lanterna:lanterna:3.1.1")
  dependencySync("org.hamcrest:hamcrest-core:2.2")
  dependencySync("org.jetbrains:annotations:19.0.0")
  dependencySync("me.champeau.gradle:jmh-gradle-plugin:0.5.3")
  dependencySync("androidx.activity:activity-compose:1.3.1")

}
