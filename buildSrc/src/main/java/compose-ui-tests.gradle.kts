import com.squareup.workflow1.library
import com.squareup.workflow1.libsCatalog

plugins {
  id("android-defaults")
}

dependencies {
  "androidTestImplementation"(project(":workflow-ui:internal-testing-compose"))

  "androidTestImplementation"(libsCatalog.library("androidx-compose-ui-test-junit4"))
}
