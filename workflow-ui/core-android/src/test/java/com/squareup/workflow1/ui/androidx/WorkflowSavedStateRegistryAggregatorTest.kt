<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
package com.squareup.workflow1.ui.container
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
package com.squareup.workflow1.ui.backstack
========
package com.squareup.workflow1.ui.androidx
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
import com.google.common.truth.Truth
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
import com.google.common.truth.Truth.assertThat
========
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
internal class StateRegistryAggregatorTest {
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
class StateRegistryAggregatorTest {
========
@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowSavedStateRegistryAggregatorTest {
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `attach stops observing previous parent when called multiple times without detach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `attach stops observing previous parent when called multiple times without detach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `attach stops observing previous parent when called multiple times without detach`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent1 = SimpleStateRegistry()
    val parent2 = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent1)
    Truth.assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(1)

    aggregator.attachToParentRegistry("key", parent2)
    Truth.assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(0)
  }

  @Test
  fun `attach throws more helpful exception when key already registered`() {
    val key = "fizzbuz"
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.savedStateRegistry.registerSavedStateProvider(key) { Bundle() }
    }

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.attachToParentRegistry(key, parent)
    }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    Truth.assertThat(error).hasMessageThat()
      .contains("Error registering StateRegistryHolder as SavedStateProvider with key \"$key\"")
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    assertThat(error).hasMessageThat()
      .contains("Error registering StateRegistryHolder as SavedStateProvider with key \"$key\"")
========
    assertThat(error).hasMessageThat()
      .contains("Error registering SavedStateProvider: key \"$key\" is already in use")
  }

  @Test fun `install throws when missing WorkflowLifecycleOwner`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.installChildRegistryOwnerOn(
        View(ApplicationProvider.getApplicationContext()),
        "key"
      )
    }

    assertThat(error).hasMessageThat().startsWith("Expected android.view.View@")
    assertThat(error).hasMessageThat()
      .endsWith("(key) to have a ViewTreeLifecycleOwner. Use WorkflowLifecycleOwner to fix that.")
  }

  @Test fun `install throws on redundant call`() {
    val view = View(ApplicationProvider.getApplicationContext()).apply {
      ViewTreeSavedStateRegistryOwner.set(this, SimpleStateRegistry())
      WorkflowLifecycleOwner.installOn(this)
    }

    val aggregator = WorkflowSavedStateRegistryAggregator()

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.installChildRegistryOwnerOn(view, "key")
    }

    assertThat(error).hasMessageThat()
      .contains(
        "already has ViewTreeSavedStateRegistryOwner: com.squareup.workflow1.ui.androidx." +
          "WorkflowSavedStateRegistryAggregatorTest\$SimpleStateRegistry"
      )
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `attach observes parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `attach observes parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `attach observes parent lifecycle`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }

    aggregator.attachToParentRegistry("key", parent)

    // The androidx internals add 2 additional observers. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    Truth.assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(3)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `attach doesn't observe parent when already restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `attach doesn't observe parent when already restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `attach doesn't observe parent when already restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }

    // Restore the aggregator.
    aggregator.attachToParentRegistry("key", parent)
    parent.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    // Re-attach it.
    aggregator.detachFromParentRegistry()
    aggregator.attachToParentRegistry("key", parent)

    // The androidx internals add 2 additional observers. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    Truth.assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(1)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `stops observing parent after ON_CREATED`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `stops observing parent after ON_CREATED`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `stops observing parent after ON_CREATED`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      // Must restore parent in order to advance lifecycle.
      stateRegistryController.performRestore(null)
    }

    aggregator.attachToParentRegistry("key", parent)
    parent.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

    // The androidx internals add 1 additional observer. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    Truth.assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(1)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `detach stops observing parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `detach stops observing parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `detach stops observing parent lifecycle`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent)
    aggregator.detachFromParentRegistry()

    Truth.assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(0)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `saveRegistryController saves when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `saveRegistryController saves when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `saveRegistryController saves when parent is restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parent.lifecycle }
    }

    var childSaveCount = 0
    aggregator.installChildRegistryOwnerOn(childView, "childKey")
    childView.savedStateRegistry.registerSavedStateProvider("counter") {
      childSaveCount++
      Bundle()
    }

    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    aggregator.saveAndPruneChildRegistryOwner("childKey")

    Truth.assertThat(childSaveCount).isEqualTo(1)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `saveRegistryController doesn't save if not restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `saveRegistryController doesn't save if not restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `saveRegistryController doesn't save if not restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parent.lifecycle }
    }

    var childSaveCount = 0
    aggregator.installChildRegistryOwnerOn(childView, "childKey")
    childView.savedStateRegistry.registerSavedStateProvider("counter") {
      childSaveCount++
      Bundle()
    }

    aggregator.attachToParentRegistry("parentKey", parent)
    // Not advancing the lifecycle here means we don't trigger restoration.

    aggregator.saveAndPruneChildRegistryOwner("childKey")

    Truth.assertThat(childSaveCount).isEqualTo(0)
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `restoreRegistryControllerIfReady restores when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `restoreRegistryControllerIfReady restores when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `restores only when parent is restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parent.lifecycle }
    }
    aggregator.installChildRegistryOwnerOn(childView, "childKey")
    assertThat(childView.savedStateRegistry.isRestored).isFalse()

    aggregator.attachToParentRegistry("parentKey", parent)
    assertThat(childView.savedStateRegistry.isRestored).isFalse()

    // Advancing the lifecycle triggers restoration.
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    parent.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    aggregator.restoreRegistryControllerIfReady("childKey", child.stateRegistryController)

    Truth.assertThat(child.savedStateRegistry.isRestored).isTrue()
  }

  @Test
  fun `restoreRegistryControllerIfReady doesn't restore if not restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val child = SimpleStateRegistry()
    aggregator.attachToParentRegistry("parentKey", parent)
    // Not advancing the lifecycle here means we don't trigger restoration.

    aggregator.restoreRegistryControllerIfReady("childKey", child.stateRegistryController)

    Truth.assertThat(child.savedStateRegistry.isRestored).isFalse()
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    parent.lifecycleRegistry.currentState = RESUMED

    aggregator.restoreRegistryControllerIfReady("childKey", child.stateRegistryController)

    assertThat(child.savedStateRegistry.isRestored).isTrue()
  }

  @Test fun `restoreRegistryControllerIfReady doesn't restore if not restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val child = SimpleStateRegistry()
    aggregator.attachToParentRegistry("parentKey", parent)
    // Not advancing the lifecycle here means we don't trigger restoration.

    aggregator.restoreRegistryControllerIfReady("childKey", child.stateRegistryController)

    assertThat(child.savedStateRegistry.isRestored).isFalse()
========
    parent.lifecycleRegistry.currentState = RESUMED
    assertThat(childView.savedStateRegistry.isRestored).isTrue()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
  }

  // This is really more of an integration test.
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `saves and restores child state controller`() {
    val holderToSave = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `saves and restores child state controller`() {
    val holderToSave = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `saves and restores child state controller`() {
    val aggregatorToSave = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parentToSave = SimpleStateRegistry().apply {
      // Need to call restore before moving lifecycle state past INITIALIZED.
      stateRegistryController.performRestore(null)
    }
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    holderToSave.attachToParentRegistry("parentKey", parentToSave)
    parentToSave.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    holderToSave.attachToParentRegistry("parentKey", parentToSave)
    parentToSave.lifecycleRegistry.currentState = RESUMED
========
    aggregatorToSave.attachToParentRegistry("parentKey", parentToSave)
    parentToSave.lifecycleRegistry.currentState = RESUMED
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt

    // Store some data in the system.
    val viewToSave = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parentToSave.lifecycle }
    }
    aggregatorToSave.installChildRegistryOwnerOn(viewToSave, "childKey")
    viewToSave.savedStateRegistry.registerSavedStateProvider("key") { bundleOf("data" to "value") }

    // Save the entire tree. This simulates what would happen just before a config change, e.g.
    val parentSavedBundle = parentToSave.saveToBundle()

    // Create a whole new tree, restored from our bundle.
    val aggregatorToRestore = WorkflowSavedStateRegistryAggregator()
    val parentToRestore = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(parentSavedBundle)
    }
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    holderToRestore.attachToParentRegistry("parentKey", parentToRestore)
    parentToRestore.lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    val childToRestore = SimpleStateRegistry()
    holderToRestore.restoreRegistryControllerIfReady(
      "childKey",
      childToRestore.stateRegistryController
    )
    val restoredChildContent = childToRestore.savedStateRegistry.consumeRestoredStateForKey("key")!!
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    holderToRestore.attachToParentRegistry("parentKey", parentToRestore)
    parentToRestore.lifecycleRegistry.currentState = RESUMED
    val childToRestore = SimpleStateRegistry()
    holderToRestore.restoreRegistryControllerIfReady(
      "childKey",
      childToRestore.stateRegistryController
    )
    val restoredChildContent = childToRestore.savedStateRegistry.consumeRestoredStateForKey("key")!!
========
    aggregatorToRestore.attachToParentRegistry("parentKey", parentToRestore)
    val viewToRestore = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parentToRestore.lifecycle }
    }
    aggregatorToRestore.installChildRegistryOwnerOn(viewToRestore, "childKey")
    parentToRestore.lifecycleRegistry.currentState = RESUMED
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt

    // Verify that our leaf data was restored.
<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    Truth.assertThat(restoredChildContent.getString("data")).isEqualTo("value")
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    assertThat(restoredChildContent.getString("data")).isEqualTo("value")
========
    val restoredChildContent = viewToRestore.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(restoredChildContent.getString("data")).isEqualTo("value")
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `restores child state controller`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `restores child state controller`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `restores child state controller`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
    val parent = stateRegistryOf(
      "parentKey" to bundleOf(
        // The childKey is associated with a "saved" SavedStateRegistryController, so we need to
        // give it the special internal bundle structure instead of just using bundleOf() directly.
        "childKey" to stateRegistryOf(
          "key" to bundleOf("data" to "value")
        ).saveToBundle()
      )
    )

    aggregator.attachToParentRegistry("parentKey", parent)
    parent.lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this) { parent.lifecycle }
    }
    aggregator.installChildRegistryOwnerOn(childView, "childKey")

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
    val childState = child.savedStateRegistry.consumeRestoredStateForKey("key")!!
    Truth.assertThat(childState.getString("data")).isEqualTo("value")
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
    val childState = child.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(childState.getString("data")).isEqualTo("value")
========
    val childState = childView.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(childState.getString("data")).isEqualTo("value")
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `detach doesn't throws when called without attach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `detach doesn't throws when called without attach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
========
  @Test fun `detach doesn't throws when called without attach`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt

    aggregator.detachFromParentRegistry()
  }

<<<<<<<< HEAD:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/container/StateRegistryAggregatorTest.kt
  @Test
  fun `save callback is invoked`() {
    var saveCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = { saveCount++ },
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    parent.saveToBundle()

    Truth.assertThat(saveCount).isEqualTo(1)
  }

  @Test
  fun `restore callback is invoked when restored`() {
    var restoreCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = { restoreCount++ }
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = Lifecycle.State.RESUMED

    Truth.assertThat(restoreCount).isEqualTo(1)
  }

  @Test
  fun `restore callback is not invoked when attached`() {
    var restoreCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = { restoreCount++ }
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)

    Truth.assertThat(restoreCount).isEqualTo(0)
  }

|||||||| 85718c3d:workflow-ui/container-android/src/test/java/com/squareup/workflow1/ui/backstack/StateRegistryAggregatorTest.kt
  @Test fun `save callback is invoked`() {
    var saveCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = { saveCount++ },
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = RESUMED

    parent.saveToBundle()

    assertThat(saveCount).isEqualTo(1)
  }

  @Test fun `restore callback is invoked when restored`() {
    var restoreCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = { restoreCount++ }
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = RESUMED

    assertThat(restoreCount).isEqualTo(1)
  }

  @Test fun `restore callback is not invoked when attached`() {
    var restoreCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = { restoreCount++ }
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    aggregator.attachToParentRegistry("parentKey", parent)

    assertThat(restoreCount).isEqualTo(0)
  }

========
>>>>>>>> origin/main:workflow-ui/core-android/src/test/java/com/squareup/workflow1/ui/androidx/WorkflowSavedStateRegistryAggregatorTest.kt
  /**
   * Creates a [SimpleStateRegistry] that is seeded with [pairs], where each key
   * in the pair is the state registry key passed to
   * [SavedStateRegistry.consumeRestoredStateForKey], and each value
   * in the pair is the [Bundle] returned from that consume method.
   */
  private fun stateRegistryOf(vararg pairs: Pair<String, Bundle>): SimpleStateRegistry {
    val stagingRegistry = SimpleStateRegistry()
    pairs.forEach { (key, bundle) ->
      stagingRegistry.savedStateRegistry.registerSavedStateProvider(key) { bundle }
    }
    val savedBundle = stagingRegistry.saveToBundle()
    return SimpleStateRegistry().apply {
      stateRegistryController.performRestore(savedBundle)
    }
  }

  @JvmName("bundleOfBundles")
  private fun bundleOf(vararg pairs: Pair<String, Bundle?>): Bundle = Bundle().apply {
    pairs.forEach { (key, bundle) ->
      putBundle(key, bundle)
    }
  }

  @JvmName("bundleOfStrings")
  private fun bundleOf(vararg pairs: Pair<String, String>): Bundle = Bundle().apply {
    pairs.forEach { (key, string) ->
      putString(key, string)
    }
  }

  private class SimpleStateRegistry : SavedStateRegistryOwner {
    val lifecycleRegistry = LifecycleRegistry(this)
    val stateRegistryController = SavedStateRegistryController.create(this)

    override fun getLifecycle(): Lifecycle = lifecycleRegistry
    override fun getSavedStateRegistry(): SavedStateRegistry =
      stateRegistryController.savedStateRegistry

    fun saveToBundle(): Bundle = Bundle().also { bundle ->
      stateRegistryController.performSave(bundle)
    }
  }

  private val View.savedStateRegistry: SavedStateRegistry
    get() = ViewTreeSavedStateRegistryOwner.get(this)!!.savedStateRegistry
}
