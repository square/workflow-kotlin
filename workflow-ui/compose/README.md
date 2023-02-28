# Module compose

This module hosts the workflow-ui compose integration, and this file describes in detail how that integration works and why.
It was originally published as a [blog post](https://developer.squareup.com/blog/jetpack-compose-support-in-workflow).

## Timeline and Process

Compose entered beta in the first half of 2020. Since we were all locked in our homes with no social lives, it was the perfect time to start exploring what integration between Compose and Workflows would look like. This was very experimental work — Compose APIs were changing drastically every two weeks. To say the least, it was not “ready for production.” However, it was important to suss out what sort of integration points were available to us, what API shapes felt natural, and where the rough edges were. In addition to figuring out our own adoption story, we have also been able to contribute a lot of feedback to Google (see our [case study](https://developer.android.com/stories/apps/square-compose)), and some of the features we initially wrote specifically for workflow integration ended up making it into the library (e.g. automatic subcomposition linking in child `View`s).

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
- Provide design system components in Compose. (This is planned, but as a separate project that depends on this one.)
- Anything with our own internal declarative UI toolkit, Mosaic (sunsetting it, integrating with it, or otherwise).

## Major Components

There are a few major areas this project needs to focus on to support Compose from Workflows: navigation, `ViewFactory` support, and hosting.

### Navigation support

Workflow isn’t just a state management library — Workflow UI includes navigation containers for things like [backstacks](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/backstack-common/src/main/java/com/squareup/workflow1/ui/backstack/BackStackScreen.kt) and [modals](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/modal-common/src/main/java/com/squareup/workflow1/ui/modal/HasModals.kt) (Support for complex navigation logic was one of our main drivers in writing the library — we outgrew things like Jetpack Navigation a long time ago.).

Because these containers define “lifecycles” for parts of the UI, they need to communicate that to the Compose primitives through the AndroidX concepts of [`LifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/LifecycleOwner) and [`SavedStateRegistry`](https://developer.android.com/reference/kotlin/androidx/savedstate/SavedStateRegistry). When a composition is hosted inside an Android `View`, the [`AbstractComposeView`](https://developer.android.com/reference/kotlin/androidx/compose/ui/platform/AbstractComposeView) that bridges the two reads the [`ViewTreeLifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/ViewTreeLifecycleOwner) to find the nearest `Lifecycle` responsible for that view.

> AndroidX recently introduced the concept of `ViewTree*` helpers — these are static getters and setters that set `View` tags, and search up the view hierarchy for those tags, to allow views to communicate in an ambient way with their children. [`ViewTreeLifecycleOwner`](https://developer.android.com/reference/androidx/lifecycle/ViewTreeLifecycleOwner), for example, allows any view to find the nearest `LifecycleOwner` by looking up the `View` tree. AndroidX `Fragment`s and `ComponentActivity`s set the `ViewTreeLifecycleOwner`, `SavedStateRegistry`, and other owners on their root views to support this.

The `Lifecycle` is then observed, both to know when it is safe to restore state, and to know when to dispose the composition because the navigation element is going away. The view also reads the [`SavedStateRegistry`](https://developer.android.com/reference/androidx/savedstate/SavedStateRegistryOwner), wraps it in a [`SaveableStateRegistry`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/saveable/SaveableStateRegistry), and provides it to the composition via the `LocalSaveableStateRegistry`. As per the [`SavedStateRegistry` contract](https://developer.android.com/reference/androidx/savedstate/SavedStateRegistry#consumeRestoredStateForKey(java.lang.String)), the registry is asked to restore the composition state as soon as the `Lifecycle` moves to the `CREATED` state. Any `rememberSaveable` calls in the composition will use this mechanism to save and restore their state.

In order for this wiring to all work with Workflows, the Workflow navigation containers must correctly publish `Lifecycle`s and `SavedStateRegistry`s for their child views. The container already manages state saving and restoration via the Android `View` “hierarchy state” mechanism that all `View` classes participate in, so it’s not much of a stretch for them to support this new AndroidX stuff as well. The tricky part is that the sequencing of these different state mechanisms is picky and a little complicated, and we ideally want the Workflow code to support this stuff even if the Workflow view root is hosted in an environment that doesn’t (e.g. a non-AndroidX `Activity`).

> None of the AndroidX integrations described in this section actually have anything to do with Compose specifically. They are required for any code that makes use of the AndroidX `ViewTree*Owners` from within a Workflow view tree. Compose just happens to rely on this infrastructure, so Workflow has to support it in order to support Compose correctly.

#### `Lifecycle`

For `LifecycleOwner` support, we need to think of anything that can ask the `ViewRegistry` for a view as a `LifecycleOwner`. This is because all such containers know when they are going to stop showing a particular child view (e.g. because the rendering type has changed, or a rendering is otherwise incompatible with the current one, and a new view must be created and bound). When that happens, they need to move the `Lifecycle` to the `DESTROYED` state to ensure the composition will be disposed.

We can provide an API for this so that containers only need to make a single call to dispose their lifecycle, and everything else “just works.” And luckily, most developers building features with Workflow will never write a container directly but instead use [`WorkflowViewStub`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/WorkflowViewStub.kt), which we will make do the right thing automatically.

[`WorkflowLifecycleOwner`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/WorkflowLifecycleOwner.kt) is the class we use to nest `Lifecycle`s. A `WorkflowLifecycleOwner` is a `LifecycleOwner` with a few extra semantics. `WorkflowLifecycleOwner`s form a tree. The lifecycle of a `WorkflowLifecycleOwner` will follow its parent, changing its own state any time the parent state changes, until either the parent enters the `DESTROYED` state or the `WorkflowLifecycleOwner` is explicitly destroyed. Thus, a tree of `WorkflowLifecycleOwner`s will be synced to the root `Lifecycle` (probably an `Activity`), but a container can set the state of an entire subtree to `DESTROYED` early – this will happen whenever the container is about to replace a view. When a container can show different views over its lifetime, it must install a `WorkflowLifecycleOwner` on each view it creates and destroy that owner when its view is about to be replaced. A `WorkflowLifecycleOwner`s automatically finds and observes its parent `Lifecycle` by the usual method — searching up the view tree.

#### `SavedStateRegistry`

`SavedStateRegistry` is a bit more complicated because of the sequencing of lifecycle and “view instance state” calls with the `SavedStateRegistry` ones.

Before all this AndroidX stuff, here’s how view state saving and restoration worked:

`View` is instantiated. Constructor probably performs some initialization, e.g. setting default `EditText` values. An ID should be set.
`View` is added as a child of a `ViewGroup` and attached to a window.
After the hosting `Activity` moves to the `STARTED` state, `onRestoreInstanceState` is called for every view in the hierarchy (even the `View`’s children, if it has any). `EditText`s, for example,  use this callback to restore any previously-entered text. Because this callback happens after initialization, it looks to the app user like the text was just restored — they never see the initial value.
`View` gets arbitrarily-many calls to `onSaveInstanceState`. The last one of these before the view is destroyed is what may be used to restore the view later.

The old mechanism depends on `View`s having their IDs set. These IDs are used to associate state with particular views, since there is no other way to match view instances between different processes.

Here’s how the view restoration system works with AndroidX’s `SavedStateRegistry`:

`View` is instantiated. Because the view hasn’t been attached to a parent yet, it can’t use the `ViewTree*Owner` functions.
`View` is eventually added to a `ViewGroup`, and attached to the window. Now the view has a parent, so the `onAttached` callback can search up the tree for the `ViewTreeLifecycleOwner`. It also looks for the `SavedStateRegistryOwner` — it can’t use it yet though.
One or more `SavedStateProvider`s are registered on the registry associated with arbitrary string keys — these providers are simply functions that will be called arbitrarily-many times to provide saved values when the system needs to save view state.
The `Lifecycle` is observed, as long as the view remains attached.
When the lifecycle state moves to `CREATED`, the `SavedStateRegistry` can be queried. The view’s initialization logic can now call `consumeRestoredStateForKey` to read back any previously-saved values associated with string keys. If there were no values available, null will be returned and the view should fallback to some default value.
When the view goes away, the `SavedStateProvider`s should be unregistered.

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

### `ViewFactory` support

Again, Workflow UI is built around the [`ViewFactory`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/ViewFactory.kt) interface, functions that build and update classic Android View instances for each type of view model rendered by a Workflow tree. Because Compose supports seamless integration from and to the classic Android `View` world, technically we don’t really _need_ to do anything to allow people to write Compose code inside `ViewFactory`s, at least to get 90% support. However, by providing some more convenient APIs, we not only remove some boilerplate, but also create the opportunity for some simplifications. There are also some edge cases that require a little more effort that we actually _do_ need to build support into Workflows for.

Each View instantiated by a  `ViewFactory` is managed by an implementation of the [`LayoutRunner`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/LayoutRunner.kt) interface. We could make a similar interface for Compose-based factories, but since Composables are just functions, we don’t even need an interface. Compose-based `ViewFactory`s will all share a common supertype, and share the same wiring logic. This logic will encapsulate the correct wiring of `AbstractComposeView`s into the Workflow-managed view hierarchy, as well as wiring up the binding so that rendering changes are correctly propagated into the composition. (The detailed API for this is covered under API Design, below.)

We will also provide a construction analogous to `WorkflowViewStub` to allow Compose-based factories to idiomatically display child renderings.

The above two concepts coordinate, and when a Compose-based factory is delegating to a child rendering that is also bound to a composable factory, we can skip the detour out into the Android view world and simply call the child composable directly from the parent.

Compose has a mechanism for sharing data implicitly between different composables that call each other. They’re called [“composition locals”](https://developer.android.com/jetpack/compose/compositionlocal). A composable can “provide” a value for a given [`CompositionLocal`](https://developer.android.com/reference/kotlin/androidx/compose/runtime/CompositionLocal) (or “local” for short) for a particular subtree of composables underneath it. Locals always flow down the tree. They are type-safe. Each is defined by a global property that provides a process-global “key” for the local, associates it with the type of value it can hold, and the default value if a composable tries reading it before any value has been provided.

Within a composition, even if that composition includes subcompositions, these locals flow seamlessly down the composition from parents to children. However, they also flow correctly down the tree if a composition includes an embedded `AndroidView` that in turns embeds another composition. Compose sets a view tag on Android `View`s hosted in compositions with a special value that will be read by child `AbstractComposeView`s to link the compositions and ensure locals continue to flow. This means that for most cases Workflow doesn’t need to do anything special to make this work.

> Compose didn’t always link compositions in a view tree automatically. Until around late 2020, the Workflow infrastructure had to pass this composition link through its analagous [`ViewEnvironment`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/ViewEnvironment.kt), which prevented any `ViewFactory` from using `AndroidView` or `AbstractComposeView` itself. We submitted a [feature request](https://issuetracker.google.com/issues/156527485) to move this behavior into the core library. Fortunately, [it got accepted](https://android-review.googlesource.com/c/platform/frameworks/support/+/1347523/), and now all Compose/Android view integrations do this [automatically](https://android-review.googlesource.com/c/platform/frameworks/support/+/1564002). This is a great example of why this early experimentation was very helpful.

However, because the Workflow modal infrastructure manages independent view trees (each `Dialog` hosts its own view tree), we need to make sure that compositions hosted inside modals are created as child compositions of any compositions enclosing the [`ModalContainer`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/modal-android/src/main/java/com/squareup/workflow1/ui/modal/ModalContainer.kt). This is one feature that has not yet been implemented in the experimental integration project, because the automatic linking of compositions is fairly recent. The proposed solution is described in the _Linking modal compositions_ section below.

### Hosting

Hosting a Workflow runtime from a composition is not very interesting as far as our internal apps are concerned, because we have a few other layers of infrastructure at the root of our apps. For our use cases, we’re only allowing Compose to be used inside of the `ViewFactory` constructions specified above, so we don’t need to worry about how to host a Workflow runtime inside a composition for now. However, it is exciting to think about using Workflows in an app that is fully Compose-based, and even if we don’t use it internally, it may be useful for external consumers of the library. Details of the hosting API are specified in the _API Design_ section below.

## API Design

The following APIs will be packaged into two Maven artifacts. Most of them will live in a core “Workflow-compose” module, and the preview tooling support will live in a “Workflow-compose-tooling” module.

Alternatively, it may also make sense to split the runtime/hosting APIs into a third module, since the main Workflow modules are split by core/runtime, and most Workflow code doesn’t need runtime stuff. The actual runtime code added for Compose support is quite small, but requires a transitive dependency on the Workflow-runtime module, so splitting the compose modules in kind would keep the transitive deps of non-runtime consumers cleaner.

### Core APIs

----

#### Defining Compose-based `ViewFactory`s

```kotlin
inline fun <reified RenderingT : Any> composedViewFactory(
  noinline content: @Composable (
    rendering: RenderingT,
    environment: ViewEnvironment
  ) -> Unit
): ViewFactory<RenderingT>
```

This is the primary API that most feature developers would touch when combining Workflow and Compose. It’s a single builder function that takes a composable lambda that emits the UI for the given rendering type. The rendering and view environment are simply provided as parameters, and Compose’s machinery takes care of ensuring the UI is updated when a new rendering or view environment is available.

Here’s an example of how it can be used:

```kotlin
val contactFactory = composedViewFactory { rendering, viewEnvironment ->
  Column {
    Text(rendering.name)
    Text(rendering.phoneNumber)
  }
}
```

This inline function creates an instance of a special concrete `ViewFactory` type. This type is currently internal-only, but it may make sense to make it public to allow creating such view factories via subclassing to allow Dagger injection. Such a class would simply look like this:

```kotlin
abstract class ComposeViewFactory<RenderingT : Any> : ViewFactory<RenderingT> {

  @Composable abstract fun Content(
    rendering: RenderingT,
    viewEnvironment: ViewEnvironment
  )

  final override fun buildView(...) = ...
}
```

----

#### Delegating to a child `ViewFactory` from a composition

Aka, `WorkflowViewStub` — Compose Edition! The idea of “view stub” is nonsense in Compose — there are no views! Instead, we simply provide a composable that takes a rendering and a view environment, and tries to display the rendering from the environment’s `ViewRegistry`.

```kotlin
@Composable fun WorkflowRendering(
  rendering: Any,
  viewEnvironment: ViewEnvironment,
  modifier: Modifier = Modifier
)
```

The `Modifier` parameter is also provided as it is idiomatic for composable functions representing UI elements to do so, and allows the caller to control layout and virtually all aspects of the child rendering’s UI.

Here’s an example of how it could be used:

```kotlin
val contactFactory = composedViewFactory { rendering, viewEnvironment ->
  Column {
    Text(rendering.name)

    WorkflowRendering(
      rendering.details,
      viewEnvironment,
      Modifier.fillMaxWidth()
    )
  }
}
```

----

#### Linking modal compositions

This API has not yet been written. The proposed shape is to create a special `ViewEnvironment` key that holds a value something like this in the core Android Workflow UI module:

```kotlin
fun interface ViewRootConnector {
  fun connectViewRoot(
    containingView: View,
    childRootView: View
  )
}

```

When a container that creates new view trees, such as the view tree inside a dialog-based modal, initializes a new view root, it would be required to look for this element in the `ViewEnvironment` and, if found, call `connectViewRoot`.

The Compose integration would then provide an implementation of this that would look up the composition context from the containing View’s tag and set it on the new child root view.

The awkward part of this design is that apps that are using Workflow + Compose would need to ensure they provide this connector in their root `ViewEnvironment`s. One potential workaround for this would be for the main Workflow UI module to use reflection to wire this up automatically, if the compose Workflow module was available on the classpath.

---

#### Previewing Compose-based `ViewFactory`s

Compose provides IDE support for [previewing composables](https://developer.android.com/jetpack/compose/tooling#preview) by annotating them with the `@Preview` annotation. Because previews are composed in a special environment in the IDE itself, they often cannot rely on the external context around the composable being set up as it would normally in a full app. For Workflow integration, it would be nice to be able to write preview functions for view factories.

**This use case doesn’t just apply to composable view factories!** Because Workflow Compose supports mixing Android and Compose factories, we can preview _any_ `ViewFactory`, which means we could even use it to preview classic Android view factories, `LayoutRunner`s, etc.

We don’t technically need any special work to support this. However, lots of view factories nest other renderings’ factories, so preview functions need to provide some bindings in the `ViewRegistry` to fake out those nested factories. To make this easier, we provide a composable function as an extension on `ViewFactory` that takes a rendering object for that factory and renders it, filling in visual placeholders for any calls to `WorkflowRendering`.

```kotlin
@Composable fun <RenderingT : Any> ViewFactory<RenderingT>.Preview(
 rendering: RenderingT,
 modifier: Modifier = Modifier,
 placeholderModifier: Modifier = Modifier,
 viewEnvironmentUpdater: ((ViewEnvironment) -> ViewEnvironment)? = null
)
```

The function takes some additional optional parameters that allow customizing how placeholders are displayed, and lets you add more stuff to the ViewEnvironment if your factory reads certain values that you’d like to control in the preview.

We can also provide a version of this method that’s an extension on [`AndroidViewRendering`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-ui/core-android/src/main/java/com/squareup/workflow1/ui/AndroidViewRendering.kt) so you can `@Preview` your renderings!

Here’s an example of a contact card UI that uses a nested `WorkflowRendering` that is filled with a placeholder:

```kotlin
@Preview
@Composable fun ContactViewFactoryPreview() {
  contactViewFactory.preview(
    ContactRendering(
      name = "Dim Tonnelly",
      details = ContactDetailsRendering(
        phoneNumber = "555-555-5555",
        address = "1234 Apgar Lane"
      )
    )
  )
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

It’s parameters roughly match those of `renderWorkflowIn`: it takes the props for the root Workflow, an optional list of interceptors, and a suspending callback for processing the root Workflow’s outputs. It returns the root Workflow’s rendering value via a `State` object (basically Compose’s analog to [`BehaviorRelay`](https://github.com/JakeWharton/RxRelay/blob/rxrelay-3.0.1/src/main/java/com/jakewharton/rxrelay3/BehaviorRelay.java)).

This function initializes and starts an instance of the Workflow runtime when it enters a composition. It uses the composition’s implicit coroutine context to host the runtime and execute the output callback. It automatically wires up [`Snapshot`](https://github.com/square/workflow-kotlin/blob/v1.0.0-alpha18/workflow-core/src/main/java/com/squareup/workflow1/Snapshot.kt) saving and restoring using Compose’s [`SaveableStateRegistry` mechanism](https://developer.android.com/jetpack/compose/state) (ie using `rememberSaveable`).

Because this function binds the Workflow runtime to the lifetime of the composition, it is best-suited for use in apps that disable restarting activities for UI-related configuration changes. That said, because it automatically saves and restores the Workflow tree’s state via snapshots, it would still work in those cases, just not as efficiently.

Note that this function does not have anything to do with UI itself - it can even be placed in a module that has no dependencies on Compose UI artifacts and only the Compose runtime. If the root Workflow’s rendering needs to be displayed as Android UI, it can be easily done via the `WorkflowRendering` composable function.

Here’s an example:

```kotlin
@Composable fun App(rootWorkflow: Workflow<...>) {
  var rootProps by remember { mutableStateOf(...) }
  val viewEnvironment = ...

  val rootRendering by rootWorkflow.renderAsState(
    props = rootProps
  ) { output ->
    handleOutput(output)
  }

  WorkflowRendering(rootRendering, viewEnvironment)
}
```
----

#### Controlling the Lifecycle of a container

**This component is only required if `SavedStateRegistry` support is implemented via classic view state.**

We introduce an interface called `WorkflowLifecycleOwner` that containers must use to install a `ViewTreeLifecycleOwner` on their immediate child views, and then must later call `destroyOnDetach` on when that view is about to either go away or be replaced with a new view from the `ViewFactory`.

```kotlin
public interface WorkflowLifecycleOwner : LifecycleOwner {

  public fun destroyOnDetach()

  public companion object {

    public fun installOn(
      view: View,
      findParentLifecycle: () -> Lifecycle? = …,
      lifecycleRatchet: Lifecycle = AlwaysResumedLifecycle
    )

    public fun get(view: View): WorkflowLifecycleOwner?

  }
}
```

The ratchet parameter allows a container to hold the lifecycle at a particular state, e.g. to support the saved state registry. Containers which need to hold the lifecycle at a particular state can do so by passing a ratchet and only advancing it once the state has been restored.

----

### Optional APIs

The following APIs might be cool, but they’re not required, and while they were built experimentally we might not want to ship them in production at this time.

----

#### Inline composable renderings

One use case that has come up for both Android and iOS Workflows is to define rendering types which know how to render themselves implicitly. In Workflow UI Android, rendering types can implement the `AndroidViewRendering` interface to specify their own view factories directly, instead of requiring their view factories to be registered explicitly in the `ViewRegistry`.

This feature presents an interesting potential construct for the compose integration: Workflows that are defined as composable functions which emit their own UI directly instead of going through the render —> rendering —> `ViewFactory` steps. Here’s what an API for defining such Workflows could look like:

```kotlin
abstract class ComposeWorkflow<in PropsT, out OutputT : Any> :
  Workflow<PropsT, OutputT, ComposeRendering> {

  @Composable abstract fun render(
    props: PropsT,
    outputSink: Sink<OutputT>,
    viewEnvironment: ViewEnvironment
  )
}

class ComposeRendering : AndroidViewRendering<ComposeRendering>
```

This render method takes a `PropsT` just like a traditional Workflow, but that’s where the similarities end. It doesn’t get any state value (but that doesn’t mean it is stateless - see below). It does not get a `RenderContext`, which means it cannot render child Workflows or run workers. It can however still delegate to other view factories via the `WorkflowRendering` composable. It does get access to a `Sink`, although it’s not the usual `actionSink` - it does not accept arbitrary `WorkflowAction`s, because it doesn’t need to due to the lack of Workflow state. The sink simply accepts `OutputT` values directly, which are effectively all “rendering events”. The render method gets called not as part of the Workflow render pass but rather as part of the view update pass that occurs once the Workflow runtime has emitted a new rendering tree. This is why it can’t render child Workflows - it gets invoked too late in the pipeline. Its rendering type is an opaque, final concrete class that has only one possible use: to be rendered via a `WorkflowViewStub` or the `WorkflowRendering` composable.

Such a Workflow may be stateful, although not in the usual sense: it does not actually store any state in the Workflow tree itself. Instead, it can use Compose’s memoization facility (ie the `remember` function) to store “view” state in the composition, or perhaps even the multiple compositions, into which it’s composed.

The distinction that any state managed by Workflows defined this way is “_view_ state” is important. While it might look like Workflow state because it’s inlined into the definition of the Workflow itself, such state is owned by the view layer and not the Workflow layer. Consider that a single Workflow rendering can potentially be displayed multiple times in different places in the UI - in which case any state required by the rendering’s UI layer will be duplicated and managed separately by each occurrence.

Similarly, while such Workflows cannot run Workers or Workflow side effects, they may perform long-running and potentially concurrent tasks that are scoped to their composition by using the standard Compose effect APIs, just like any composable.

These Workflows do not define their own rendering types, and thus do not have anywhere to define rendering event handler functions. Instead, they can send outputs to their parent workflows directly from composable event callbacks via the `outputSink` parameter.

These workflows can only be leaf workflows since they can’t render children. However, they may be very convenient in modules that already mix their `Workflow` and `ViewFactory` definitions in the same module and want to factor out workflows for self-contained components.

Here’s an example of how it could be used:

```kotlin
// Child Workflow
object ContactWorkflow : ComposeWorkflow<
  Contact, // PropsT
  Output  // OutputT
>() {

  enum class Output {
    CLICKED, DELETED
  }

  @Composable override fun render(
    props: Contact,
    outputSink: Sink<Clicked>,
    viewEnvironment: ViewEnvironment
  ) {
    ListItem(
      primary = { Text(props.name) },
      secondary = { Text(props.phoneNumber) },
      modifier = Modifier
        .clickable { outputSink.send(CLICKED) }
        .swipeToDismissable { outputSink.send(DELETED) }
    )
  }
}

// Parent Workflow
class ContactList : StatelessWorkflow<...> {
  override fun initialState(...) = ...

  override fun render(...) = ListRendering(
    contactRows = props.contacts.map { contact ->
      context.renderChild(
        props = contact,
        Workflow = ContactWorkflow
      ) { output -> ... }
    }
  )
}
```

----

## Potential risk: Data model

Passing both the rendering and view environment down as parameters through the entire UI tree means that every time a rendering updates, we’ll recompose a lot of composables. This is how Workflow was designed, and because compose does some automatic deduping we’ll automatically avoid recomposing the leaves of the UI for a particular view factory unless the data for those bits of ui actually change. However, any time a leaf rendering changes, we’ll also be recomposing all the parent view factories just in order to propagate that leaf to its composable. That means we’re not able to take advantage of a lot of the other optimizations that compose tries to do both now and potentially in the future.

In other words: “Workflow+views” < “Workflow+compose” < “data model designed specifically for compose + compose”.

It should be straightforward to address this issue for view environments - see the _Alternative design_ section for more information. However, it’s not clear how to solve this for renderings without abandoning our current rendering data model. Today, renderings are an immutable tree of immutable value types that require the entire tree to be recreated any time any single piece of data changes. The reason for this design is that it was the only way to safely propagate changes without adding a bunch of reactive streams to renderings everywhere. The key word in that sentence is “was”: Compose’s snapshot state system makes it possible to expose simple mutable properties and still get change notifications that will ensure that the UI stays up-to-date (For an example of how this system can be used to model complex state systems with dependencies, see [this blog post](https://dev.to/zachklipp/plumbing-data-with-derived-state-in-compose-53ka)).

Workflow could take advantage of this by allowing renderings to actually be mutable, so that when one Workflow deep in the tree wants to change something, it can do so independently and without requiring every rendering above it in the tree to also change. Making such a change to such a fundamental piece of Workflow design could have significant implications on other aspects of Workflow design, and doing so is very far outside the scope of this post.

We want to call this out because it seems like we’ll be losing out on one of Compose’s optimization tricks, but we’re not sure how much of a problem this will turn out to be in the real world. The only performance issues that we’re aware of that we’ve run into with Workflow UI so far are issues with recreating leaf views on every rerender, and that in particular _*is*_ something Compose will automatically win at, even with our current data model.

## Alternative design: Propagating `ViewEnvironment`s through `CompositionLocal`s

You’ll notice that all the APIs described above explicitly pass `ViewEnvironment` objects around. This mirrors how other Workflow UI code works, as well as the Mosaic integration. Compose has the concept of “composition local” — which is similar in spirit to `ViewEnvironment` itself (and SwiftUI’s [`Environment`](https://developer.apple.com/documentation/swiftui/environment)). So why not just pass view environments implicitly through composition locals?

This is what we did at first, but it made the API awkward for testing and other cases. Google advises against using composition locals in most cases for a reason. Because Workflow UI requires a `ViewRegistry` to be provided through the `ViewEnvironment`, there’s no obvious default value — what is the correct behavior when no `ViewEnvironment` local has been specified? Crashing at runtime is not ideal. We could provide an empty `ViewRegistry`, but that’s just another way to crash at runtime a few levels deeper in the call stack. Requiring explicit parameters for `ViewEnvironment` solves all these problems at the expense of a little more typing, and matches how the existing `ViewFactory` APIs work.

On the other hand, providing an API to access individual view environment elements from a composable that hides the actual mechanism and uses composition locals under the hood would let us take much better advantage of Compose’s fine-grained UI updates. We could ensure that, when a view environment changes, only the parts of the UI that actually care about the modified part of the environment are recomposed. However, renderings typically change an order of magnitude more frequently than view environments, so there’s probably not much point solving this problem until we’ve solved the same problem with renderings (discussed above under _Potential risk: Data model_).
