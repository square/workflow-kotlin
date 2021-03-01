# workflow-kotlin-compose

[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.workflow/workflow-ui-core-compose.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:com.squareup.workflow%20AND%20a:workflow-ui-core-compose)

This module provides experimental support for [Jetpack Compose UI][1] with workflows.

The only integration that is currently supported is the ability to define [ViewFactories][2] that
are implemented as `@Composable` functions. See the `hello-compose-binding` sample in `samples` for
an example of how to use.

----

## Pre-Alpha

**DO NOT USE this module in your production apps!**

Jetpack Compose is in pre-alpha, developer preview stage. The API is incomplete and changes
_very frequently_. This integration module exists as a proof-of-concept, to show what's possible,
and to experiment with various ways to integrate Compose with Workflow.

----

## Usage

### Add the dependency

Add the dependencies from this project (they're on Maven Central):

```groovy
dependencies {
  // Main dependency
  implementation "com.squareup.workflow:workflow-ui-core-compose:${versions.workflow_compose}"

  // For the preview helpers
  implementation "com.squareup.workflow:workflow-ui-compose-tooling:${versions.workflow_compose}"
}
```

### Enable Compose

You must be using the latest Android Gradle Plugin 4.x version, and enable Compose support
in your `build.gradle`:

```groovy
android {
  buildFeatures {
    compose true
  }
  composeOptions {
    kotlinCompilerVersion "1.4.0-dev-withExperimentalGoogleExtensions-20200720"
    kotlinCompilerExtensionVersion "${compose_version}"
  }
}
```

To create a `ViewFactory`, call `composedViewFactory`. The lambda passed to `composedViewFactory` is
a `@Composable` function.

```kotlin
val HelloBinding = composedViewFactory<MyRendering> { rendering, _ ->
  MaterialTheme {
    Clickable(onClick = { rendering.onClick() }) {
      Text(rendering.message)
    }
  }
}
```

The `composedViewFactory` function returns a regular [`ViewFactory`][2] which can be added to a
[`ViewRegistry`][3] like any other:

```kotlin
val viewRegistry = ViewRegistry(HelloBinding)
```

## License
```
Copyright 2020 Square, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

[1]: https://developer.android.com/jetpack/compose
[2]: https://square.github.io/workflow/kotlin/api/workflow/com.squareup.workflow1.ui/-view-factory/
[3]: https://square.github.io/workflow/kotlin/api/workflow/com.squareup.workflow1.ui/-view-registry/
