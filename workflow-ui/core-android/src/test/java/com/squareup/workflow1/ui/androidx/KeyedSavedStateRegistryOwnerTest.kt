package com.squareup.workflow1.ui.androidx

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class KeyedSavedStateRegistryOwnerTest {

  @Test
  fun `throws when onRestoreNeeded fails to restore the registry`() {
    val delegateLifecycle = SimpleLifecycleOwner()
    val owner =
      KeyedSavedStateRegistryOwner(
        key = "childKey",
        lifecycleOwner = delegateLifecycle,
        // Broken callee: the contract requires restoring the controller before returning.
        onRestoreNeeded = {},
      )
    owner.installObserver()

    val error =
      assertFailsWith<IllegalStateException> {
        delegateLifecycle.lifecycleRegistry.currentState = RESUMED
      }
    assertThat(error)
      .hasMessageThat()
      .contains("onRestoreNeeded contract violation: registry for key 'childKey' was not restored")
  }

  private class SimpleLifecycleOwner : LifecycleOwner {
    val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
      get() = lifecycleRegistry
  }
}
