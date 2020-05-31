Change Log
==========

Version 0.30.0
--------------

_2020-05-31_

* Update Compose to dev12. (#41)
* New/Breaking: Make `renderAsState` public, make `WorkflowContainer` take a ViewEnvironment. (#32)
* Breaking: Rename `bindCompose` to `composedViewFactory`. (#35)
* Breaking: Rename `showRendering` to `WorkflowRendering` and make it not an extension function. (#36)
* Breaking: Rename `ComposeViewFactoryRoot` to `CompositionRoot` and decouple the implementation. (#34)
* Fix: Make `ViewRegistry.showRendering` update if `ViewRegistry` changes. (#33)
* Fix: Fix subcomposition of ComposableViewFactory and WorkflowRendering. (#37)

Version 0.29.0
--------------

_2020-05-18_

* First release from separate repo.
* Update: Compose version to dev11. (#26)
* New: Add the ability to display nested renderings with `bindCompose`. (#7)
* New: Introduce `ComposeWorkflow`, a self-rendering Workflow. (#8)
* New: Introduce tooling module with support for previewing ViewBindings with Compose's Preview. (#15)
* New: Introduce WorkflowContainer for running a workflow inside a Compose app. (#16)
* Breaking: Tidy up the package structure. (#23)
