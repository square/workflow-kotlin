# Module compose

This module hosts the workflow-ui compose integration, and this file describes in detail how that integration works and why.

It was originally published as a [blog post](https://developer.squareup.com/blog/jetpack-compose-support-in-workflow).
Since then, our approach has been overhauled.
With that overhaul this document has been updated to reflect how the new system works, but it's still very design doc like.
To skip past all the big picture and implementation verbiage and get right to how to use this stuff, jump down to [API Design](#api-design) below.

## Goals and Non-Goals

At Square, when we start a project, we like to enumerate and distinguish goals and explicit non-goals to delineate the scope of the project.

### Goals

- Allow Workflow screens to be written in Compose.
- Allow interop in both directions using the usual idioms:
   - Non-Compose Workflow screens to nest Compose-based Workflow screens.
   - Compose-based Workflow screens to nest both Compose-based and non-Compose Workflow screens.
- Provide a way for Compose-based apps to host a Workflow runtime and display Compose apps, e.g. a parallel entry point to how non-Compose apps can use `WorkflowLayout`.
- Ensure that data flow through `CompositionLocal`s is propagated to child Compose-based screens, regardless of whether there are non-Workflow screens sitting in between them.

### Non-Goals

- Convert existing screens in our apps to Compose.
- Provide design system components in Compose.

## Major Components

There are a few major areas this project needs to focus on to support Compose from Workflows: navigation, UI factory support (that is, `ScreenViewFactory` and the other types collected by `ViewRegistry`), and hosting.

### Navigation support

Workflow isn’t just a state management library — Workflow UI includes navigation containers for things like [backstacks](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-common/src/main/java/com/squareup/workflow1/ui/navigation/BackStackScreen.kt) and [windows](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-common/src/main/java/com/squareup/workflow1/ui/navigation/Overlay.kt) -- including [modals](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-common/src/main/java/com/squareup/workflow1/ui/navigation/ModalOverlay.kt). (Support for complex navigation logic was one of our main drivers in writing the library — we outgrew things like Jetpack Navigation a long time ago.).

Because these containers define “lifecycles” for parts of the UI, they need to communicate that to the Compose primitives through the AndroidX concepts of [`LifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/LifecycleOwner) and [`SavedStateRegistry`](https://developer.android.com/reference/kotlin/androidx/savedstate/SavedStateRegistry). When a composition is hosted inside an Android `View`, the [`AbstractComposeView`](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AbstractComposeView) that bridges the two reads the [`ViewTreeLifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/ViewTreeLifecycleOwner) to find the nearest `Lifecycle` responsible for that view.

> AndroidX recently introduced the concept of `ViewTree*` helpers — these are static getters and setters that set `View` tags, and search up the view hierarchy for those tags, to allow views to communicate in an ambient way with their children. [`ViewTreeLifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/ViewTreeLifecycleOwner), for example, allows any view to find the nearest `LifecycleOwner` by looking up the `View` tree. AndroidX `Fragment`s and `ComponentActivity`s set the `ViewTreeLifecycleOwner`, `SavedStateRegistry`, and other owners on their root views to support this.

The `Lifecycle` is then observed, both to know when it is safe to restore state, and to know when to dispose the composition because the navigation element is going away. The view also reads the [`SavedStateRegistry`](https://developer.android.com/reference/androidx/savedstate/SavedStateRegistryOwner), wraps it in a [`SaveableStateRegistry`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateRegistry), and provides it to the composition via the `LocalSaveableStateRegistry`. As per the [`SavedStateRegistry` contract](https://developer.android.com/reference/androidx/savedstate/SavedStateRegistry#consumeRestoredStateForKey(java.lang.String)), the registry is asked to restore the composition state as soon as the `Lifecycle` moves to the `CREATED` state. Any `rememberSaveable` calls in the composition will use this mechanism to save and restore their state.

In order for this wiring to all work with Workflows, the Workflow navigation containers must correctly publish `Lifecycle`s and `SavedStateRegistry`s for their child views. The containers already manage state saving and restoration via the Android `View` “hierarchy state” mechanism that all `View` classes participate in, so it’s not much of a stretch for them to support this new AndroidX stuff as well. The tricky part is that the sequencing of these different state mechanisms is picky and a little complicated, and we ideally want the Workflow code to support this stuff even if the Workflow view root is hosted in an environment that doesn’t (e.g. a non-AndroidX `Activity`).

> None of the AndroidX integrations described in this section actually have anything to do with Compose specifically. They are required for any code that makes use of the AndroidX `ViewTree*Owners` from within a Workflow view tree. Compose just happens to rely on this infrastructure, so Workflow has to support it in order to support Compose correctly.

#### `Lifecycle`

For `LifecycleOwner` support, we need to think of anything that can ask the `ViewRegistry` to build a view as a `LifecycleOwner`. This is because all such containers know when they are going to stop showing a particular child view (e.g. because the rendering type has changed, or a rendering is otherwise incompatible with the current one, and a new view must be created and bound). When that happens, they need to move the `Lifecycle` to the `DESTROYED` state to ensure the hosted composition will be disposed.

We can provide an API for this so that containers only need to make a single call to dispose their lifecycle, and everything else “just works.” And luckily, most developers building features with Workflow will never write a container directly but instead use [`WorkflowViewStub`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/WorkflowViewStub.kt), which we will make do the right thing automatically.

[`WorkflowLifecycleOwner`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/androidx/WorkflowLifecycleOwner.kt) is the class we use to nest `Lifecycle`s. A `WorkflowLifecycleOwner` is a `LifecycleOwner` with a few extra semantics. `WorkflowLifecycleOwner`s form a tree. The lifecycle of a `WorkflowLifecycleOwner` follows its parent, changing its own state any time the parent state changes, until either the parent enters the `DESTROYED` state or the `WorkflowLifecycleOwner` is explicitly destroyed. Thus, a tree of `WorkflowLifecycleOwner`s will be synced to the root `Lifecycle` (probably an `Activity` or a `Dialog`), but a container can set the state of an entire subtree to `DESTROYED` early – this will happen whenever the container is about to replace a view. When a container can show different views over its lifetime, it must install a `WorkflowLifecycleOwner` on each view it creates and destroy that owner when the managed view is about to be replaced. A `WorkflowLifecycleOwner`s automatically finds and observes its parent `Lifecycle` by the usual method — searching up the view tree.

#### `SavedStateRegistry`

`SavedStateRegistry` is a bit more complicated because of the sequencing of lifecycle and “view instance state” calls with the `SavedStateRegistry` ones.

Before all this AndroidX stuff, here’s how view state saving and restoration worked:

- `View` is instantiated. Constructor probably performs some initialization, e.g. setting default `EditText` values. An ID should be set.

- `View` is added as a child of a `ViewGroup` and attached to a window.

- After the hosting `Activity` moves to the `STARTED` state, `onRestoreInstanceState` is called for every view in the hierarchy (even the `View`’s children, if it has any). `EditText`s, for example,  use this callback to restore any previously-entered text. Because this callback happens after initialization, it looks to the app user like the text was just restored — they never see the initial value.

- `View` gets arbitrarily-many calls to `onSaveInstanceState`. The last one of these before the view is destroyed is what may be used to restore the view later.

The old mechanism depends on `View`s having their IDs set. These IDs are used to associate state with particular views, since there is no other way to match view instances between different processes.

Here’s how the view restoration system works with AndroidX’s `SavedStateRegistry`:

- `View` is instantiated. Because the view hasn’t been attached to a parent yet, it can’t use the `ViewTree*Owner` functions.

- `View` is eventually added to a `ViewGroup`, and attached to the window. Now the view has a parent, so the `onAttached` callback can search up the tree for the `ViewTreeLifecycleOwner`. It also looks for the `SavedStateRegistryOwner` — it can’t use it yet though.

- One or more `SavedStateProvider`s are registered on the registry associated with arbitrary string keys — these providers are simply functions that will be called arbitrarily-many times to provide saved values when the system needs to save view state.

- The `Lifecycle` is observed, as long as the view remains attached.

- When the lifecycle state moves to `CREATED`, the `SavedStateRegistry` can be queried. The view’s initialization logic can now call `consumeRestoredStateForKey` to read back any previously-saved values associated with string keys. If there were no values available, null will be returned and the view should fallback to some default value.

- When the view goes away, the `SavedStateProvider`s should be unregistered.

Note the difference in when the restoration happens relative to the lifecycle states. The following table summarizes the differences between the instance state mechanism and `SavedStateRegistry`.

| Instance state                                                                                                                                                    | `SavedStateRegistry`                                                                                                                                                                                                                                |
|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| All views participate in this mechanism, and can’t opt-out.                                                                                                       | Views must opt-in by getting access to a `SavedStateRegistry` either via `findViewTreeSavedStateRegistryOwner` or some other way.                                                                                                                   |
| Save/restore callbacks are built into the `View` base class as overridable methods. _Restoration is “push-based” — views are told when to restore themselves._    | Views must register saved state providers explicitly in order to get the save callbacks. _Restoration is “pull-based” — views must request previously-saved values at the appropriate time. Registry API requires coordination with Lifecycle API._ |
| Restored after lifecycle `STARTED`.                                                                                                                               | Restored after lifecycle `CREATED`.                                                                                                                                                                                                                 |
| Identified by hierarchy of view IDs.                                                                                                                              | Identified by string keys.                                                                                                                                                                                                                          |
| IDs only need to be unique within their parent view.                                                                                                              | Keys must be unique within a given registry _(this scope is not as clearly defined, but usually means within the navigation “frame”)_.                                                                                                              |
| Saved state is represented as `Parcelable`s, only not really because well-behaved views should actually return their state as subclasses of a special base class. | Saved state is represented as `Bundle`s. _Compatible with `Parcelable`s but much more convenient API for every-day use._                                                                                                                            |

These differences make tying these together and supporting both from a single container a little complicated.
Every container must support both of these mechanisms, but ideally using a single source of truth for saved state.
Because containers and `WorkflowViewStub`s can exist anywhere in a view tree, they must be able to identify themselves to the different state mechanisms appropriately. It turns out that the legacy instance state approach of using view IDs is capable of supporting the registry string key approach, but it’s not really feasible the other way around. So the source of truth needs to be the instance state, and the registry state is stored in and restored from that.

Because the `SavedStateRegistry` contract says that it must be consumable as soon as the lifecycle is in the `CREATED` state, containers must also be able to control the lifecycle to ensure that it isn’t moved to that state until they’ve had a chance to actually seed the registry with restored data.

The last two points form a cycle: we don’t get the `onRestoreInstanceState` callback until we’re in the `STARTED` state, but we can’t advance our children’s lifecycle past the `CREATED` state until we have read the registry state out of the instance state and seeded the registry. So the sequence we need to implement is:

1. Create a `Lifecycle` to provide to our children, but hold it in the `INITIALIZED` state. Create a `SavedStateRegistry` to provide to our children as well.
1. In `onRestoreInstanceState`, read both the instance state for our children, and the registry state.
1. Seed our `SavedStateRegistry` with the restored state.
1. Advance state to `CREATED`. Children will consume state from the registry.
1. Advance state to `STARTED`. (This can actually be done in a single step with the previous one, the Lifecycle APIs ensure every state is hit.)
1. Invoke children’s `onRestoreInstanceState` methods.

We have looked at a few ways of implementing this:

1. Single source of state: `SavedStateRegistry`. `BackStackContainer` only uses `SavedStateRegistry` to save and restore state, and implements support for classic view state on top of the newer registry APIs. This approach is infeasible because it requires emulating the way that state is namespaced with `View` IDs with string keys, and that is surprisingly difficult.
1. Single source of state: view state. `BackStackContainer` only uses view state, and implements support for the newer registry APIs on top of classic view state. While easier to implement than (1), it requires changing `WorkflowLifecycleOwner` to give the container more control over the lifecycle to comply with `SavedStateRegistry`'s contract about when states can be restored.
1. Use both. `BackStackContainer` uses the classic view state hooks to manage classic view state, and uses `SavedStateRegistry` hooks to manage registry state. This allows each mechanism to keep its advantage, and doesn't require emulating one's behavior with the other.

### UI Factory support

Workflow UI is built around UI factory interfaces like [`ScreenViewFactory`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/ScreenViewFactory.kt) and [`OverlayDialogFactory`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/navigation/OverlayDialogFactory.kt), functions that build and update classic Android `View` and `Dialog` instances for each type of view model rendered by a Workflow tree. To find the appropriate factory to express a rendering of a particular type, container classes like [`WorkflowViewStub`] delegate most of their work to factory finder interfaces like [`ScreenViewFactoryFinder`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/ScreenViewFactoryFinder.kt) and `[OverlayDialogFactoryFinder]`(https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/navigation/OverlayDialogFactoryFinder.kt).

To add seamless Compose support, we add another UI factory and finder interface pair: [`ScreenComposableFactory`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/ScreenComposableFactory.kt) and [`ScreenComposableFactoryFinder`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/ScreenComposableFactoryFinder.kt).
And to make those as easy to use as possible, we introduce [`ComposeScreen`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/ComposeScreen.kt), a Compose analog to [`AndroidScreen`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/AndroidScreen.kt)

We also provide [`@Composable fun WorkflowRendering()`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/WorkflowRendering.kt), a construction function analogous to `WorkflowViewStub` to allow Compose-based factories to idiomatically display child renderings.

> You'll note that there is no `OverlayComposableFactory` family of interfaces. So far, all of our window management is strictly via classic Android `Dialog` calls. There is nothing stopping us (or you) from adding Compose-based `Overlay` support in the future as a replacement, but we're definitely not making any promises on that front.

Finally, we provide support for gluing together the Classic and Compose worlds. The [`ViewEnvironment.withComposeInteropSupport()`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/ViewEnvironmentWithComposeSupport.kt) function replaces the `ScreenViewFactoryFinder` and `ScreenComposableFactory` objects in the receiver with implementations that are able to delegate to the other type.

All of the above coordinates nicely, so that when a Compose-based factory is delegating to a child rendering that is also bound to a composable factory, we can skip the detour out into the Android view world and simply call the child composable directly from the parent. It is also possible to provide both Classic and Compose treatments of any type of rendering. We use this technique with the standard [`NamedScreen`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-common/src/main/java/com/squareup/workflow1/ui/NamedScreen.kt) and [`EnvironmentScreen`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-common/src/main/java/com/squareup/workflow1/ui/EnvironmentScreen.kt) wrapper types to prevent needless journeys through `@Composable fun AndroidView()` and the `ComposeView` class. (https://github.com/square/workflow-kotlin/issues/546)

## API Design

The following APIs will are packaged into two Maven artifacts. Most of them live in a core “Workflow-compose” module, and the preview tooling support is in a “Workflow-compose-tooling” module.

### Core APIs

----

#### Opting in / Bootstrapping

Alas, we can't make this just work out of the box without you making a bootstrap call to put the key pieces in place.
Even if your own UI code is strictly built in Compose, the stock `BackStackScreen` and `BodyAndOverlaysScreen` types are still implemented only via classic `View` code.
You need to call `ViewEnvironment.withComposeInteropSupport()` somewhere near the top.
For example, here is how to do it with your `renderWorkflowIn()` call:

```kotlin
private val viewEnvironment = ViewEnvironment.EMPTY.withComposeInteropSupport()

renderWorkflowIn(
  workflow = HelloWorkflow.mapRendering {
    it.withEnvironment(viewEnvironment)
  },
  scope = viewModelScope,
  savedStateHandle = savedState,
)
```

#### Defining Compose-based UI factories

The most straightforward and common way to tie a `Screen` rendering type to a `@Composable` function
is to implement [`ComposeScreen`](https://github.com/square/workflow-kotlin/blob/9bfd5119fabd0a3dfbc25bf7d93e52c7b31bb4cd/workflow-ui/compose/src/main/java/com/squareup/workflow1/ui/compose/ComposeScreen.kt), the Compose-friendly analog to [`AndroidScreen`](https://github.com/square/workflow-kotlin/blob/v1.12.1-beta06/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/AndroidScreen.kt).

```kotlin
import java.nio.file.WatchEvent.Modifier

data class HelloScreen(
  val message: String,
  val onClick: () -> Unit
) : ComposeScreen {
  @Composable override fun Content() {
    Hello(this)
  }
}

@Composable
private fun Hello(
  screen: HelloScreen,
  modifier: Modifier = Modifier
) {
  Button(screen.onClick, modifier) {
    Text(message)
  }
}
```

`ComposeScreen` is a convenience that automates creating a `ScreenComposableFactory` implementation responsible for expressing, say, `HelloScreen` instances by calling `HelloScreen.Content()`.

This `ScreenComposableFactory` interface is the lynchpin API combining Workflow and Compose. It’s basically a single builder function that takes a `@Composable` lambda that emits the UI for the given `Screen` rendering type. In Compose terms, the `Screen` acts as hoisted state for thre related `@Composable`. The rendering and view environment are simply provided as parameters, and Compose’s machinery takes care of ensuring the UI is updated when a new rendering or view environment is available.

Here’s an example of how `ScreenComposableFactory` can be used directly to keep a rendering type decoupled from the related Compose code:

```kotlin
data class ContactScreen(
  val name: String,
  val phoneNumber: String
): Screen
```
```kotlin
val contactUiFactory = ScreenComposableFactory<ContactScreen> { screen ->
  Column {
    Text(screen.name)
    Text(screen.phoneNumber)
  }
}

private val viewEnvironment = ViewEnvironment.EMPTY +
    (ViewRegistry to ViewRegistry(contactUiFactory))
    .withComposeInteropSupport()

renderWorkflowIn(
    workflow = HelloWorkflow.mapRendering {
      it.withEnvironment(viewEnvironment)
    },
    scope = viewModelScope,
    savedStateHandle = savedState,
)
```

----

#### Delegating to a child UI factory from a composition

Aka, `WorkflowViewStub` — Compose Edition! The idea of “view stub” is nonsense in Compose — there are no views! Instead, we simply provide a composable that takes a rendering and a view environment, and tries to display the rendering from the environment’s `ViewRegistry`.

```kotlin
@Composable fun WorkflowRendering(
  rendering: Screen,
  modifier: Modifier = Modifier
)
```

The `Modifier` parameter is also provided as it is idiomatic for composable functions representing UI elements to do so, and allows the caller to control layout and virtually all aspects of the child rendering’s UI.

Here’s an example of how it could be used:

```kotlin
data class ContactScreen(
  val name: String,
  val details: Screen
): Screen

val contactUiFactory = ScreenComposableFactory<ContactScreen> { screen ->
  Column {
    Text(screen.name)

    WorkflowRendering(
      screen.details,
      Modifier.fillMaxWidth()
    )
  }
}
```

---

#### Previewing Compose-based (and non-Compose!) UI Factories

Compose provides IDE support for [previewing composables](https://developer.android.com/jetpack/compose/tooling#preview) by annotating them with the `@Preview` annotation. Because previews are composed in a special environment in the IDE itself, they often cannot rely on the external context around the composable being set up as it would normally in a full app. For Workflow integration, we provide support to write preview functions for UI factories.

We don’t technically need any special work to support this. However, lots of view factories nest other renderings’ factories, so preview functions need to provide some bindings in the `ViewRegistry` to fake out those nested factories. To make this easier, we provide a composable function as an extension on `Screen` that takes a rendering object for that factory and renders it, filling in visual placeholders for any calls to `WorkflowRendering()`.

```kotlin
@Composable fun Screen.Preview(
  modifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
)
```
**This doesn’t just apply to composable UI!**
You can call `Preview()` on any `Screen` that has been bound to UI code, regardless of how that UI code is implemented.

The function takes some additional optional parameters that allow customizing how placeholders are displayed, and lets you add more stuff to the `ViewEnvironment` if your factory reads certain values that you’d like to control in the preview.

Here’s an example of a contact card UI that uses a nested `WorkflowRendering` that is filled with a placeholder:

```kotlin
@Preview
@Composable fun ContactViewFactoryPreview() {
  ContactScreen(
    name = "Dim Tonnelly",
    details = ContactDetailsRendering(
      phoneNumber = "555-555-5555",
      address = "1234 Apgar Lane"
    )
  ).Preview()
}
```

----

#### Hosting a Workflow runtime from a composition

Unlike the other apis described here, which all exist to allow individual Workflows’ renderings’ view factories to be defined as Compose code, this API is intended for use at the top of a purely-Workflow application (or anywhere that needs to host a Workflow runtime). Its non-Compose counterpart is the `renderWorkflowIn` function. It is an extension function on a Workflow that mirrors other Composable APIs for subscribing to reactive state (e.g. `Flow.collectAsState`, `LiveData.observeAsState`).

```kotlin
@Composable
fun <PropsT, OutputT : Any, RenderingT>
Workflow<PropsT, OutputT, RenderingT>.renderAsState(
 props: PropsT,
 interceptors: List<WorkflowInterceptor> = emptyList(),
 onOutput: suspend (OutputT) -> Unit
): State<RenderingT>
```

Its parameters roughly match those of `renderWorkflowIn`: it takes the props for the root Workflow, an optional list of interceptors, and a suspending callback for processing the root Workflow’s outputs. It returns the root Workflow’s rendering value via a `State` object (basically Compose’s analog to [`StateFlow`](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-state-flow/)).

This function initializes and starts an instance of the Workflow runtime when it enters a composition. It uses the composition’s implicit coroutine context to host the runtime and execute the output callback. It automatically wires up [`Snapshot`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-core/src/main/java/com/squareup/workflow1/Snapshot.kt) saving and restoring using Compose’s [`SaveableStateRegistry` mechanism](https://developer.android.com/jetpack/compose/state) (ie using `rememberSaveable`).

Because this function binds the Workflow runtime to the lifetime of the composition, it is best suited for use in apps that disable restarting activities for UI-related configuration changes (which really is the best way to build a Compose-first application). That said, because it automatically saves and restores the Workflow tree’s state via snapshots, it would still work in those cases, just not as efficiently.

Note that this function does not have anything to do with UI itself - it can even be placed in a module that has no dependencies on Compose UI artifacts and only the Compose runtime. If the root Workflow’s rendering needs to be displayed as Android UI, it can be easily done via the `WorkflowRendering` composable function.

Here’s an example:

```kotlin
@Composable fun App(rootWorkflow: Workflow<...>) {
  var rootProps by remember { mutableStateOf(...) }

  val rootRendering by rootWorkflow.renderAsState(
    props = rootProps
  ) { output ->
    handleOutput(output)
  }

  WorkflowRendering(rootRendering)
}
```

----

## Potential risk: Data model

Passing both rendering down as a parameter through the entire UI tree means that
every time a rendering updates, we’ll recompose a lot of composables.
This is how Workflow was designed, and because compose does some automatic deduping
we’ll automatically avoid recomposing the leaves of the UI for a particular view factory
unless the data for those bits of ui actually change. However, any time a leaf rendering changes,
we’ll also be recomposing all the parent view factories just in order to propagate that leaf to its composable.
That means we’re not able to take advantage of a lot of the other optimizations that compose tries to do both now and potentially in the future.

In other words: “Workflow+views” < “Workflow+compose” < “data model designed specifically for compose + compose”.

It’s not clear how to solve this for renderings without abandoning our current rendering data model.
Today, renderings are an immutable tree of immutable value types
that require the entire tree to be recreated any time any single piece of data changes.
The reason for this design is that it was the only way to safely propagate changes
without adding a bunch of reactive streams to renderings everywhere.

The key word in that sentence is “was”: Compose’s snapshot state system makes it possible
to expose simple mutable properties and still get change notifications that will ensure
that the UI stays up-to-date.
For an example of how this system can be used to model complex state systems with dependencies,
see [this blog post](https://dev.to/zachklipp/plumbing-data-with-derived-state-in-compose-53ka).

Workflow could take advantage of this by allowing renderings to actually be mutable,
so that when one Workflow deep in the tree wants to change something, it can do so independently
and without requiring every rendering above it in the tree to also change.
Making such a change to such a fundamental piece of Workflow design could have significant implications
on other aspects of Workflow design, and doing so is very far outside the scope of this post.

We want to call this out because it seems like we’ll be losing out on one of Compose’s optimization tricks,
but we’re not sure how much of a problem this will turn out to be in the real world.
The only performance issues that we’re aware of that we’ve run into with Workflow UI so far are issues
with recreating leaf views on every rerender, and that in particular _*is*_ something Compose will automatically win at,
even with our current data model.
