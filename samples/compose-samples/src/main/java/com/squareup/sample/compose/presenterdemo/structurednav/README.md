# structurednav

This package contains a composable set of APIs for structuring the navigation in a complex app. It
is merely embedded in this demo package for easy prototyping, but would eventually be shipped as a
dedicated library module beside the main `workflow-compose` module.

There are two navigational concepts at play:

- Backstack
- Overlay stack

Both of these are exposed via Compose APIs that act like "container" composables in Compose UI: they
take an arbitrary number of child nodes and arrange them in some specific way. They both essentially
return their children in a wrapped list, however the specific wrapper type is what tells the view
layer how to arrange the models' views, and also contains some additional properties for navigation.

Where these "containers" differ from Compose UI is that they do some introspection into their
children to fractally enforce some structural invariants. Namely, the invariant that an overlay
stack can contain backstacks, but a backstack must not contain overlay stacks.

## Overlays

This is the simpler of the two containers. The main API is the `BodyAndOverlaysPresenter`
composable, which emits a `BodyAndOverlaysViewModel` that contains all its children in a list and
indicates to the view layer that they should be arranged in a z-ordered stack, with the body at the
bottom/in the back. The composable provides a scope receiver to its `content` function that allows
an event handler to be specified on each child that determines how it handles dismiss requests. It's
up to the view layer to determine what user inputs constitute a "dismiss request"; some reasonable
ones are tapping outside the overlay's content or pressing the system back button. The dismiss
handler is optional and need only be specified for user-dismissable overlays.

```kotlin
@Composable fun MyOverlaysScreen() {
  var shownOverlays by remember { mutableStateOf(0) }
  BodyAndOverlaysPresenter {
    BodyPresenter()

    if (shownOverlays >= 1) {
      Overlay1Presenter(
          modifier = PresenterModifier.onDismissRequest { shownOverlays = 0 }
      )
    }
    if (shownOverlays >= 2) {
      Overlay2Presenter(
          modifier = PresenterModifier.onDismissRequest { shownOverlays = 1 }
      )
    }
  }
}
```

This container has no opinions on the number of, style of, or behavior of individual overlays.
The overlay view models can be anything—the view layer will just display whatever it gets. While the
body can generally be anything, it's recommended to restrict overlay models to specific presenters
that display fixed overlay styles. A particular app may encourage specific overlays by wrapping this
composable with more specific ones. E.g:

```kotlin
@Composable fun BodyAndAlertPresenter(
  modifier: PresenterModifier = PresenterModifier,
  alert: (@Composable (PresenterModifier) -> Unit)? = null,
  onDismissRequested: (() -> Unit)? = null,
  body: @Composable () -> Unit,
) {
  BodyAndOverlaysPresenter(modifier) {
    body()
  }

  if (alert != null) {
    alert(PresenterModifier.onDismissRequested(onDismissRequested))
  }
}
```

That example has one issue: if `body` emits multiple top-level nodes, only the first will be treated
as the body, and the rest will become overlays. To address this, it's recommended to wrap each child
composable in its own container to ensure it only produces a single node. A particularly good tool
for this job is `BackstackPresenter` (see below).

When `BodyAndAlertPresenter`s are nested, they are effectively flattened into a single list. The
body of such a presenter in an overlay position becomes just another overlay.

## Backstack

A backstack is a stack (list) of view models where usually only the last/top one is displayed. When
the top model is pushed or popped, or the last screen otherwise changes identity, the view layer
performs a bunch of animations to make it look like forward or backward navigation. This package
does not implement that view layer, it only consists of the presenter-layer APIs.

The main API for creating backstacks is the `BackstackPresenter` composable. In its simplest form,
this composable emits a `BackstackViewModel` that contains all its children in a list and indicates
that the last one should be displayed, with back-navigation support, animating when the last
identity of the last model changes. This composable provides a scope receiver to its `content`
function that allows specifying a back handler for each child. The back handler is optional and need
only be specified if the user is allowed to manually navigate backwards. Since a stack should never
be empty, a back handler on the first child is ignored.

```kotlin
@Composable fun MyBackstackScreen() {
  var page by remember { mutableStateOf(0) }

  BackstackPresenter {
    FirstPagePresenter()

    if (page >= 1) {
      SecondPagePresenter(
          modifier = PresenterModifier.onBackRequested { page = 0 }
      )
    }

    if (page >= 2) {
      ThirdPagePresenter(
          modifier = PresenterModifier.onBackRequested { page = 1 }
      )
    }
  }
}
```

When `BackstackPresenter`s are nested, they compose naturally. Parent stacks can specify back
handlers on child stacks that will be routed to when the child stack has a single entry. The view
layer is expected to use the most-deeply-nested back handler as the authoritative handler, so a
nested backstack will always get back events routed to it first. When a nested stack has only one
child left the nested stack has no back handler and the parent will get the event, allowing it to
pop the nested stack of its own stack.

```kotlin
@Composable NestedBackstacks() {
  var showPage2 by remember { mutableStateOf(false) }

  BackstackPresenter {
    FirstPage(onAdvance = { showPage2 = true })

    if (showPage2) {
      BackstackPresenter(PresenterModifier.onBackRequested { showPage2 = false }) {
        var showPage3 by remember { mutableStateOf(false) }
        // When back is pressed and this is the top of the stack, the event is
        // routed to the back handler above.
        SecondPage(onAdvance = { showPage3 = true })

        if (showPage3) {
          ThirdPage(PresenterModifier.onBackRequested { showPage3 = false })
        }
      }
    }
  }
}
```

Where backstacks get interesting is when they're used to compose other backstacks and overlay
stacks.

## Mutual nesting

We've covered how overlay stacks can be nested in overlay stacks, and backstacks can be nested in
backstacks. But there are two more permutations possible. When overlays are nested inside backstacks
or vice versa, the containers enforce the invariant that an overlay stack may contain backstacks at
each layer, but a backstack may never contain an overlay stack.

Revisiting our `BodyAndAlertPresenter` from the overlay stacks intro, we can enforce layering by
wrapping each layer in a backstack:
```kotlin
@Composable fun BodyAndAlertPresenter(
  modifier: PresenterModifier = PresenterModifier,
  alert: (@Composable (PresenterModifier) -> Unit)? = null,
  onDismissRequested: (() -> Unit)? = null,
  body: @Composable () -> Unit,
) {
  BodyAndOverlaysPresenter(modifier) {
    BackstackPresenter {
      body()
    }
  }

  if (alert != null) {
    BackstackPresenter {
      alert(PresenterModifier.onDismissRequested(onDismissRequested))
    }
  }
}
```

### Backstacks inside overlay stacks

It is common for the body layer of an overlay presenter to contain a backstack of body models.
Overlays may also contain their own backstacks.

Back and dismiss events compose naturally. The view layer for the overlay stack routes back events
to the top-most overlay first. If this is a backstack, then the backstack handles it. When the stack
is empty, back events get routed to the overlay's dismiss handler, then start routing to the next
overlay, etc. all the way down to the body layer. Other dismiss events, e.g. touching outside the
overlay's content, are routed directly to the overlay's dismiss handler.

Consider the following structure, consisting of a body with two overlay layers.

```
    Body: A1 -> A2
Overlay1: B1
Overlay2: C1 -> C2 -> C3
```

From this state, when the user presses back, the new state becomes:

```
    Body: A1 -> A2
Overlay1: B1
Overlay2: C1 -> C2
```

If the user taps outside the `Overlay2` content area, the state becomes:

```
    Body: A1 -> A2
Overlay1: B1
```

If the user presses back from this state, the new state is:

```
    Body: A1 -> A2
```

### Overlay stacks inside backstacks

When a backstack contains overlay stacks they require special consideration. Only the model on the
top of the backstack is allowed to show overlays. It never makes sense for a "hidden" backstack
model (one that's not at the top) to show overlays: they would appear over the top backstack entry.
When a backstack entry becomes hidden by another entry its overlays must be hidden as well.

Overlays generally take precedence over what's below them. To enforce this, the overlay host view
must always get first claim of events. This is much easier to do if the view model structure mirrors
this requirement. Hence overlay stacks are allowed to contain backstacks, but backstacks are never
allowed to contain overlay stacks.

`BackstackPresenter` enforces this when producing its view model. It inspects the type of each child
and when it sees a child is a `BodyAndOverlaysViewModel` it resolves the child's view model, adds
only its body model to the backstack, and then either discards the overlays (if the child is not at
the top of the backstack) or saves them for the next step. After it finishes processing the child
list, if the topmost model contained overlays, then it emits a `BodyAndOverlaysViewModel` with its
own backstack as the body, and all the overlays from the topmost child.

**This composes fractally:** overlay stacks and backstacks may be mutually nested to an arbitrary
depth, and the topmost overlay stack will be bubbled up to the top of the navigation tree, with
nothing but nested backstacks below it at each overlay layer.

Consider the following structure, consisting of a backstack where multiple entries emit overlay
stacks:

```
          | A1 |    | B1 |    | C1 |
Backstack:|    | -> | B2 | -> | C2 |
          |    |    | B3 |    |    |
```

When this structure is processed by the containers, they reduce it to the following:

```
    Body: A1 -> B1 -> C1
Overlay1: C2
```

If the user presses back or otherwise dismisses the `C2` modal, the raw structure becomes:

```
          | A1 |    | B1 |    | C1 |
Backstack:|    | -> | B2 | -> |    |
          |    |    | B3 |    |    |
```

which is reduced to:

```
    Body: A1 -> B1 -> C1
```

When the user presses back again, `C1` is popped off the stack and we get:

```
          | A1 |    | B1 |
Backstack:|    | -> | B2 |
          |    |    | B3 |
```

which becomes:

```
    Body: A1 -> B1
Overlay1: B2
Overlay2: B3
```

Processing of further navigation events is left as an exercise for the reader.

## Direct use of container view model types

For special cases, the `BackStackViewModel` and `BodyAndOverlaysViewModel` types may be created
directly by custom container presenters. The constructors for these models also enforce the
invariant discussed above. The view models also allow back and dismiss handlers to be specified,
respectively.

## Empty containers

Neither container should generally be allowed to be empty. A backstack with zero children displays
nothing, and means that the parent should never have composed the backstack in the first place.
Compose does not allow enforcing this at compile time, but the container view models' constructors
will throw if they are given no entries. Container presenters emit a sentinel empty view model value
when they have no children, and handle that sentinel in their child lists by treating the child as
nonexistent. Using a sentinel value with a dedicated type allows querying whether a child is empty
without resolving its model.
