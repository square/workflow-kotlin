# compose-samples

This module is named "compose-samples" because the binary validation tool seems to refuse to look
at the `:workflow-ui:compose` module if this one is also named `compose`.

1. To run the iOS target you need to be on a Mac and have Xcode installed.
2. Then install the Kotlin Multiplatform plugin for your intellij IDE
3. Finally go to run configurations and add a new iOS Application configuration and select the iOS
project file in ./iosApp
