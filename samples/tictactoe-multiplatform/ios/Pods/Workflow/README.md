# workflow

![Swift CI](https://github.com/square/workflow-swift/workflows/Swift%20CI/badge.svg)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](https://www.apache.org/licenses/LICENSE-2.0)
[![CocoaPods compatible](https://img.shields.io/cocoapods/v/Workflow.svg)](https://cocoapods.org/pods/Workflow)

A unidirectional data flow library for Swift and [Kotlin](https://github.com/square/workflow-kotlin), emphasizing:

* Strong support for state-machine driven UI and navigation.
* Composition and scaling.
* Effortless separation of business and UI concerns.

_**This project is currently in development and the API subject to breaking changes without notice.**
Follow Square's engineering blog, [The Corner](https://developer.squareup.com/blog/), to see when
this project becomes stable._

While the API is not yet stable, this code is in heavy production use in Android and iOS
apps with millions of users.

## Using Workflows in your project

### Swift Package Manager

[![SwiftPM compatible](https://img.shields.io/badge/SwiftPM-compatible-orange.svg)](#swift-package-manager)

If you are developing your own package, be sure that Workflow is included in `dependencies`
in `Package.swift`:

```swift
dependencies: [
    .package(url: "git@github.com:square/workflow-swift.git", from: "1.0.0-beta.3")
]
```

In Xcode 11+, add Workflow directly as a dependency to your project with
`File` > `Swift Packages` > `Add Package Dependency...`. Provide the git URL when prompted: `git@github.com:square/workflow-swift.git`.

### Cocoapods

[![CocoaPods compatible](https://img.shields.io/cocoapods/v/Workflow.svg)](https://cocoapods.org/pods/Workflow)

If you use CocoaPods to manage your dependencies, simply add Workflow and WorkflowUI to your
Podfile:

```ruby
pod 'Workflow'
pod 'WorkflowUI'
```

## Resources

* [Documentation](https://square.github.io/workflow/)

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
