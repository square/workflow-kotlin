package com.squareup.workflow1.ui.androidx

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class StateRegistryAggregatorTest {

  @Test fun `attach stops observing previous parent when called multiple times without detach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent1 = SimpleStateRegistry()
    val parent2 = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent1)
    assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(1)

    aggregator.attachToParentRegistry("key", parent2)
    assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(0)
  }

  @Test fun `attach throws more helpful exception when key already registered`() {
    val key = "fizzbuz"
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.savedStateRegistry.registerSavedStateProvider(key) { Bundle() }
    }

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.attachToParentRegistry(key, parent)
    }

    assertThat(error).hasMessageThat()
      .contains("Error registering SavedStateProvider: key \"$key\" is already in use")
  }

  @Test fun `attach observes parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }

    aggregator.attachToParentRegistry("key", parent)

    // The androidx internals add 2 additional observers. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(3)
  }

  @Test fun `attach doesn't observe parent when already restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }

    // Restore the aggregator.
    aggregator.attachToParentRegistry("key", parent)
    parent.lifecycleRegistry.currentState = RESUMED

    // Re-attach it.
    aggregator.detachFromParentRegistry()
    aggregator.attachToParentRegistry("key", parent)

    // The androidx internals add 2 additional observers. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(1)
  }

  @Test fun `stops observing parent after ON_CREATED`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore parent in order to advance lifecycle.
      stateRegistryController.performRestore(null)
    }

    aggregator.attachToParentRegistry("key", parent)
    parent.lifecycleRegistry.handleLifecycleEvent(ON_CREATE)

    // The androidx internals add 1 additional observer. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(1)
  }

  @Test fun `detach stops observing parent lifecycle`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent)
    aggregator.detachFromParentRegistry()

    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(0)
  }

  @Test fun `saveRegistryController saves when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    var childSaveCount = 0
    val child = SimpleStateRegistry().apply {
      savedStateRegistry.registerSavedStateProvider("key") {
        childSaveCount++
        Bundle()
      }
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = RESUMED

    aggregator.saveRegistryController("childKey", child.stateRegistryController)

    assertThat(childSaveCount).isEqualTo(1)
  }

  @Test fun `saveRegistryController doesn't save if not restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    var childSaveCount = 0
    val child = SimpleStateRegistry().apply {
      savedStateRegistry.registerSavedStateProvider("key") {
        childSaveCount++
        Bundle()
      }
    }
    aggregator.attachToParentRegistry("parentKey", parent)
    // Not advancing the lifecycle here means we don't trigger restoration.

    aggregator.saveRegistryController("childKey", child.stateRegistryController)

    assertThat(childSaveCount).isEqualTo(0)
  }

  @Test fun `restoreRegistryControllerIfReady restores when parent is restored`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val child = SimpleStateRegistry()
    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
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
  }

  // This is really more of an integration test.
  @Test fun `saves and restores child state controller`() {
    val holderToSave = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parentToSave = SimpleStateRegistry().apply {
      // Need to call restore before moving lifecycle state past INITIALIZED.
      stateRegistryController.performRestore(null)
    }
    holderToSave.attachToParentRegistry("parentKey", parentToSave)
    parentToSave.lifecycleRegistry.currentState = RESUMED

    // Store some data in the system.
    val childToSave = stateRegistryOf("key" to bundleOf("data" to "value"))
    holderToSave.saveRegistryController("childKey", childToSave.stateRegistryController)

    // Save the entire tree. This simulates what would happen just before a config change, e.g.
    val parentSavedBundle = parentToSave.saveToBundle()

    // Create a whole new tree, restored from our bundle.
    val holderToRestore = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parentToRestore = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(parentSavedBundle)
    }
    holderToRestore.attachToParentRegistry("parentKey", parentToRestore)
    parentToRestore.lifecycleRegistry.currentState = RESUMED
    val childToRestore = SimpleStateRegistry()
    holderToRestore.restoreRegistryControllerIfReady(
      "childKey",
      childToRestore.stateRegistryController
    )
    val restoredChildContent = childToRestore.savedStateRegistry.consumeRestoredStateForKey("key")!!

    // Verify that our leaf data was restored.
    assertThat(restoredChildContent.getString("data")).isEqualTo("value")
  }

  @Test fun `restores child state controller`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )
    val parent = stateRegistryOf(
      "parentKey" to bundleOf(
        // The childKey is associated with a "saved" SavedStateRegistryController, so we need to
        // give it the special internal bundle structure instead of just using bundleOf() directly.
        "childKey" to stateRegistryOf(
          "key" to bundleOf(
            "data" to "value"
          )
        ).saveToBundle()
      )
    )

    aggregator.attachToParentRegistry("parentKey", parent)
    parent.lifecycleRegistry.handleLifecycleEvent(ON_CREATE)

    val child = SimpleStateRegistry()
    aggregator.restoreRegistryControllerIfReady("childKey", child.stateRegistryController)

    val childState = child.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(childState.getString("data")).isEqualTo("value")
  }

  @Test fun `detach doesn't throws when called without attach`() {
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = {}
    )

    aggregator.detachFromParentRegistry()
  }

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

  @Test fun `do not restore from an unrestored registry`() {
    var restoreCount = 0
    val aggregator = StateRegistryAggregator(
      onWillSave = {},
      onRestored = { restoreCount++ }
    )
    val parent = SimpleStateRegistry()
    assertThat(parent.stateRegistryController.savedStateRegistry.isRestored).isFalse()
    aggregator.attachToParentRegistry("parentKey", parent)

    parent.lifecycleRegistry.currentState = RESUMED
    // We once crashed here:
    //   IllegalStateException: You can consumeRestoredStateForKey only after super.onCreate
    assertThat(restoreCount).isEqualTo(0)
  }

  /**
   * Creates a [SimpleStateRegistry] that is seeded with [pairs], where each key in the pair is the
   * state registry key passed to [SavedStateRegistry.consumeRestoredStateForKey], and each value
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
}
