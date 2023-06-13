package com.squareup.workflow1.ui.androidx

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_CREATE
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
@OptIn(WorkflowUiExperimentalApi::class)
internal class WorkflowSavedStateRegistryAggregatorTest {
  private val fakeOnBack = object : OnBackPressedDispatcherOwner {
    override fun getLifecycle(): Lifecycle = error("")
    override fun getOnBackPressedDispatcher(): OnBackPressedDispatcher = error("")
  }

  @Test fun `attach stops observing previous parent when called multiple times without detach`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent1 = SimpleStateRegistry()
    val parent2 = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent1)
    assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(1)

    aggregator.attachToParentRegistry("key", parent2)
    assertThat(parent1.lifecycleRegistry.observerCount).isEqualTo(0)
  }

  @Test fun `attach throws more helpful exception when key already registered`() {
    val key = "fizzbuz"
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.savedStateRegistry.registerSavedStateProvider(key) { Bundle() }
    }

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.attachToParentRegistry(key, parent)
    }

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

    assertThat(error).hasMessageThat().startsWith("Expected android.view.View{")
    assertThat(error).hasMessageThat()
      .endsWith("(key) to have a ViewTreeLifecycleOwner. Use WorkflowLifecycleOwner to fix that.")
  }

  @Test fun `install throws on redundant call`() {
    val view = View(ApplicationProvider.getApplicationContext()).apply {
      this.setViewTreeSavedStateRegistryOwner(SimpleStateRegistry())
      WorkflowLifecycleOwner.installOn(this, fakeOnBack)
    }

    val aggregator = WorkflowSavedStateRegistryAggregator()

    val error = assertFailsWith<IllegalArgumentException> {
      aggregator.installChildRegistryOwnerOn(view, "key")
    }

    assertThat(error).hasMessageThat()
      .contains(
        "already has SavedStateRegistryOwner: com.squareup.workflow1.ui.androidx." +
          "WorkflowSavedStateRegistryAggregatorTest\$SimpleStateRegistry"
      )
  }

  @Test fun `attach observes parent lifecycle`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }

    aggregator.attachToParentRegistry("key", parent)

    // The androidx internals add 2 additional observers. This test could break if that changes, but
    // unfortunately there's no other public API to check.
    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(3)
  }

  @Test fun `attach doesn't observe parent when already restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
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
    val aggregator = WorkflowSavedStateRegistryAggregator()
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
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry()

    aggregator.attachToParentRegistry("key", parent)
    aggregator.detachFromParentRegistry()

    assertThat(parent.lifecycleRegistry.observerCount).isEqualTo(0)
  }

  @Test fun `saveRegistryController saves when parent is restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      // Must restore the parent controller in order to initialize the aggregator.
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parent.lifecycle }
    }

    var childSaveCount = 0
    aggregator.installChildRegistryOwnerOn(childView, "childKey")
    childView.savedStateRegistry.registerSavedStateProvider("counter") {
      childSaveCount++
      Bundle()
    }

    aggregator.attachToParentRegistry("parentKey", parent)
    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = RESUMED

    aggregator.saveAndPruneChildRegistryOwner("childKey")

    assertThat(childSaveCount).isEqualTo(1)
  }

  @Test fun `saveRegistryController doesn't save if not restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parent.lifecycle }
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

    assertThat(childSaveCount).isEqualTo(0)
  }

  @Test fun `restores only when parent is restored`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
    val parent = SimpleStateRegistry().apply {
      stateRegistryController.performRestore(null)
    }
    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parent.lifecycle }
    }
    aggregator.installChildRegistryOwnerOn(childView, "childKey")
    assertThat(childView.savedStateRegistry.isRestored).isFalse()

    aggregator.attachToParentRegistry("parentKey", parent)
    assertThat(childView.savedStateRegistry.isRestored).isFalse()

    // Advancing the lifecycle triggers restoration.
    parent.lifecycleRegistry.currentState = RESUMED
    assertThat(childView.savedStateRegistry.isRestored).isTrue()
  }

  // This is really more of an integration test.
  @Test fun `saves and restores child state controller`() {
    val aggregatorToSave = WorkflowSavedStateRegistryAggregator()
    val parentToSave = SimpleStateRegistry().apply {
      // Need to call restore before moving lifecycle state past INITIALIZED.
      stateRegistryController.performRestore(null)
    }
    aggregatorToSave.attachToParentRegistry("parentKey", parentToSave)
    parentToSave.lifecycleRegistry.currentState = RESUMED

    // Store some data in the system.
    val viewToSave = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parentToSave.lifecycle }
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
    aggregatorToRestore.attachToParentRegistry("parentKey", parentToRestore)
    val viewToRestore = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parentToRestore.lifecycle }
    }
    aggregatorToRestore.installChildRegistryOwnerOn(viewToRestore, "childKey")
    parentToRestore.lifecycleRegistry.currentState = RESUMED

    // Verify that our leaf data was restored.
    val restoredChildContent = viewToRestore.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(restoredChildContent.getString("data")).isEqualTo("value")
  }

  @Test fun `restores child state controller`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()
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
    parent.lifecycleRegistry.handleLifecycleEvent(ON_CREATE)

    val childView = View(ApplicationProvider.getApplicationContext()).apply {
      WorkflowLifecycleOwner.installOn(this, fakeOnBack) { parent.lifecycle }
    }
    aggregator.installChildRegistryOwnerOn(childView, "childKey")

    val childState = childView.savedStateRegistry.consumeRestoredStateForKey("key")!!
    assertThat(childState.getString("data")).isEqualTo("value")
  }

  @Test fun `detach doesn't throws when called without attach`() {
    val aggregator = WorkflowSavedStateRegistryAggregator()

    aggregator.detachFromParentRegistry()
  }

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
    override val savedStateRegistry: SavedStateRegistry
      get() = stateRegistryController.savedStateRegistry

    override fun getLifecycle(): Lifecycle = lifecycleRegistry

    fun saveToBundle(): Bundle = Bundle().also { bundle ->
      stateRegistryController.performSave(bundle)
    }
  }

  private val View.savedStateRegistry: SavedStateRegistry
    get() = this.findViewTreeSavedStateRegistryOwner()!!.savedStateRegistry
}
