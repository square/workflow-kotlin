# workflow

[![Kotlin CI](https://github.com/square/workflow-kotlin/workflows/Kotlin%20CI/badge.svg)](https://github.com/square/workflow-kotlin/actions?query=branch%3Amain)
[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.workflow1/workflow-core-jvm.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.squareup.workflow1%22)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![Kotlinlang slack](https://img.shields.io/static/v1?label=kotlinlang&message=squarelibraries&color=brightgreen&logo=slack)](https://kotlinlang.slack.com/archives/C5HT9AL7Q)

Workflow is an application framework that provides architectural primitives.

Workflow is:

* Written in and used for Kotlin and Swift
* A unidirectional data flow library that uses immutable data within each Workflow.
  Data flows in a single direction from source to UI, and events in a single direction
  from the UI to the business logic.
* A library that supports writing business logic and complex UI navigation logic as
  state machines, thereby enabling confident reasoning about state and validation of
  correctness.
* Optimized for composability and scalability of features and screens.
* Corresponding UI frameworks that bind Rendering data classes for “views”
  (including event callbacks) to Mobile UI frameworks for Android and iOS.
* A corresponding testing framework that facilitates simple-to-write unit
  tests for all application business logic and helps ensure correctness.

_**1.0.0-rc is ready and the core is stable. There are still experimental /**
**under construction areas of the API for UI integration however.**
These classes and functions are marked with `@WorkflowUIExperimentalApi`.
They are suitable for production use (we've been shipping them for months
at the very heart of our flagship app), but may require signature tweaks as
we iterate a bit more on Dialog management, and configuring transition effects.
If they do change, we will take care to minimize the impact via deprecation, etc._

## Using Workflows in your project

### Maven Artifacts

Artifacts are hosted on Maven Central. If you're using Gradle, ensure `mavenCentral()` appears in
your `repositories` block, and then add dependencies on the following artifacts:

<table>
  <tr>
    <th>Maven Coordinates</th>
    <th>Depend on this if…</th>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-core-jvm:x.y.z</code></td>
    <td>You are writing a library module/project that uses Workflows, but you don't need to interact
    with the runtime from the outside.</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-rx2:x.y.z</code></td>
    <td>You need to interact with RxJava2 from your Workflows.</td>
  </tr>
<tr>
    <td nowrap><code>com.squareup.workflow1:workflow-rx3:x.y.z</code></td>
    <td>You need to interact with RxJava3 from your Workflows.</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-testing-jvm:x.y.z</code></td>
    <td>You are writing tests. This should only be included as a test dependency.</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-ui-core-android:x.y.z</code></td>
    <td>You're writing an Android app that uses Workflows.</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-ui-modal-android:x.y.z</code></td>
    <td>Your Android app uses modals (popups).</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-ui-backstack-android:x.y.z</code></td>
    <td>Your android app uses backstacks.</td>
  </tr>
</table>

### Lower-level Artifacts

Most code shouldn't need to depend on these directly. They should generally only be used to build
higher-level integrations with UI frameworks.

<table>
  <tr>
    <th>Maven Coordinates</th>
    <th>Depend on this if…</th>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-runtime-jvm:x.y.z</code></td>
    <td>You need to interact directly with the runtime, i.e. streams of renderings and outputs.</td>
  </tr>
  <tr>
    <td nowrap><code>com.squareup.workflow1:workflow-ui-core-jvm:x.y.z</code></td>
    <td>You are writing workflow-ui-android for another UI framework. Defines the core types used by
    that artifact.</td>
  </tr>
</table>

### Jetpack Compose support

[Jetpack Compose](https://developer.android.com/jetpack/compose) is the new UI toolkit for Android.
It is comparable to SwiftUI for iOS. The main UI artifacts in this repository support standard
Android Views, but various types of Compose integrations are provided under the
**[compose](/workflow-ui/compose)** folder.

You'll find workflow + compose info and documentation there.

## Resources

* Wondering why to use Workflow? See
  ["Why Workflow"](https://square.github.io/workflow/userguide/whyworkflow/)
* There is a [Glossary of Terms](https://square.github.io/workflow/glossary/)
* We have a [User Guide](https://square.github.io/workflow/userguide/concepts/)
  describing core concepts.
* For Kotlin (and Android), there is a codelab style
  [tutorial](https://github.com/square/workflow-kotlin/tree/main/samples/tutorial) in the repo.
* For Swift (and iOS), there is also a Getting Started
  [tutorial](https://github.com/square/workflow-swift/tree/main/Samples/Tutorial) in the repo.
* There are also a number of
  [Kotlin samples](https://github.com/square/workflow-kotlin/tree/main/samples)
  and [Swift samples](https://github.com/square/workflow-swift/tree/main/Samples).

### Support & Contact

Workflow discussion happens in the Workflow Community slack. Use this [open invitation](https://join.slack.com/t/workflow-community/shared_invite/zt-a2wc0ddx-4bvc1royeZ7yjGqEkW1CsQ).

Workflow maintainers also hang out in the [#squarelibraries](https://kotlinlang.slack.com/messages/C5HT9AL7Q)
channel on the [Kotlin Slack](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up?_ga=2.93235285.916482233.1570572671-654176432.1527183673).

## Releasing and Deploying

See [RELEASING.md](RELEASING.md).

## License

<pre>
Copyright 2019 Square Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>
