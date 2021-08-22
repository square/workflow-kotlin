package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.DESTROYED
import androidx.lifecycle.Lifecycle.State.INITIALIZED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.Lifecycle.State.STARTED
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Test
import kotlin.test.assertFailsWith

internal class RealWorkflowLifecycleOwnerTest {

  private val rootContext = mock<Context>()
  private val view = mock<View> {
    on { context } doReturn rootContext
  }
  private var parentLifecycle: LifecycleRegistry? = null
  private val owner = RealWorkflowLifecycleOwner(
    enforceMainThread = false,
    findParentLifecycle = { parentLifecycle }
  )

  @Test fun `lifecycle starts initialized`() {
    assertThat(owner.lifecycle.currentState).isEqualTo(INITIALIZED)
  }

  @Test fun `attach throws when no parent lifecycle found`() {
    val error = assertFailsWith<IllegalStateException> {
      makeViewAttached()
    }
    assertThat(error.message).isEqualTo(
      "Expected to find either a ViewTreeLifecycleOwner in the view tree, or for the" +
        " view's context to be a LifecycleOwner."
    )
  }

  @Test fun `lifecycle not destroyed when detached`() {
    ensureParentLifecycle()
    makeViewAttached()
    makeViewDetached()
    assertThat(owner.lifecycle.currentState).isEqualTo(INITIALIZED)
  }

  @Test fun `lifecycle is not destroyed after detachOnDestroy while attached`() {
    ensureParentLifecycle()
    makeViewAttached()

    owner.destroyOnDetach()

    assertThat(owner.lifecycle.currentState).isEqualTo(INITIALIZED)
  }

  @Test fun `lifecycle is destroyed after detachOnDestroy and detach`() {
    ensureParentLifecycle()
    parentLifecycle!!.currentState = CREATED
    makeViewAttached()
    owner.destroyOnDetach()

    makeViewDetached()

    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)
  }

  @Test fun `lifecycle is destroyed after detachOnDestroy when already detached`() {
    ensureParentLifecycle()
    parentLifecycle!!.currentState = CREATED
    // Attach and detach the view to move past the INITIALIZED state by synchronizing with the
    // parent.
    makeViewAttached()
    makeViewDetached()

    owner.destroyOnDetach()

    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)
  }

  @Test fun `lifecycle doesn't resume after destroy`() {
    ensureParentLifecycle()
    parentLifecycle!!.currentState = CREATED
    // Attach and detach the view to move past the INITIALIZED state by synchronizing with the
    // parent.
    makeViewAttached()
    makeViewDetached()
    owner.destroyOnDetach()
    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)

    makeViewAttached()
    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)
  }

  @Test fun `lifecycle moves to parent state when attached`() {
    ensureParentLifecycle().currentState = STARTED
    makeViewAttached()
    assertThat(owner.lifecycle.currentState).isEqualTo(STARTED)
  }

  @Test fun `lifecycle follows parent state while attached`() {
    ensureParentLifecycle().currentState = INITIALIZED
    makeViewAttached()

    listOf(
      // Going up…
      CREATED,
      STARTED,
      RESUMED,
      // …and back down.
      CREATED,
      DESTROYED,
    ).forEach { state ->
      ensureParentLifecycle().currentState = state
      assertThat(owner.lifecycle.currentState).isEqualTo(state)
    }
  }

  @Test fun `lifecycle follows parent state while detached`() {
    ensureParentLifecycle().currentState = INITIALIZED
    makeViewAttached()
    makeViewDetached()

    listOf(
      // Going up…
      CREATED,
      STARTED,
      RESUMED,
      // …and back down.
      CREATED,
      DESTROYED,
    ).forEach { state ->
      ensureParentLifecycle().currentState = state
      assertThat(owner.lifecycle.currentState).isEqualTo(state)
    }
  }

  @Test fun `lifecycle stays destroyed after parent destroyed`() {
    ensureParentLifecycle().currentState = RESUMED
    makeViewAttached()

    ensureParentLifecycle().currentState = DESTROYED
    ensureParentLifecycle().currentState = RESUMED

    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)
  }

  @Test fun `lifecycle stops observing parent when destroyed`() {
    ensureParentLifecycle().currentState = RESUMED
    makeViewAttached()
    assertThat(ensureParentLifecycle().observerCount).isEqualTo(1)

    owner.destroyOnDetach()
    makeViewDetached()

    assertThat(owner.lifecycle.currentState).isEqualTo(DESTROYED)
    assertThat(ensureParentLifecycle().observerCount).isEqualTo(0)
  }

  @Test fun `lifecycle switches subscription to new parent when reattached`() {
    val originalParent = ensureParentLifecycle()
    originalParent.currentState = CREATED
    makeViewAttached()
    originalParent.currentState = RESUMED
    assertThat(owner.lifecycle.currentState).isEqualTo(RESUMED)

    makeViewDetached()
    // Force the parent to be recreated.
    parentLifecycle = null
    ensureParentLifecycle().currentState = STARTED
    makeViewAttached()

    // Should have unsubscribed, so this should be a no-op.
    originalParent.currentState = DESTROYED
    assertThat(owner.lifecycle.currentState).isEqualTo(STARTED)
  }

  @Test fun `lifecycle stays in INITIALIZED when moved immediately to DESTROYED`() {
    val events = mutableListOf<Event>()
    ensureParentLifecycle()
    parentLifecycle!!.currentState = DESTROYED
    // The lifecycle is more strict when there's at least one observer, so add one.
    owner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
      events += event
    })

    makeViewAttached()

    assertThat(events).containsExactly().inOrder()
    assertThat(owner.lifecycle.currentState).isEqualTo(INITIALIZED)
  }

  private fun makeViewAttached() {
    owner.onViewAttachedToWindow(view)
    whenever(view.isAttachedToWindow).thenReturn(true)
  }

  private fun makeViewDetached() {
    owner.onViewDetachedFromWindow(view)
    whenever(view.isAttachedToWindow).thenReturn(false)
  }

  private fun ensureParentLifecycle(): LifecycleRegistry {
    if (parentLifecycle == null) {
      val owner = object : LifecycleOwner {
        val lifecycle = LifecycleRegistry.createUnsafe(this)
        override fun getLifecycle(): Lifecycle = lifecycle
      }
      parentLifecycle = owner.lifecycle
    }
    return parentLifecycle!!
  }
}
