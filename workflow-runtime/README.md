# Module workflow-runtime

This module contains the core APIs and logic for running workflows.

## Kotlin Multiplatform

This module is a Kotlin Multiplatform module. The targets currently included for build and test
are `jvm`, `ios`, and `iosSimulatorSimulatorArm64`. If you are having issues with the tests,
ensure you have the correct version of XCode installed and can launch a simulator as it's specified
in the gradle build file (Currently iPhone 14).
