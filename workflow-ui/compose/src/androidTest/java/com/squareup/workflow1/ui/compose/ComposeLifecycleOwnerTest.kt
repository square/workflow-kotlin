package com.squareup.workflow1.ui.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.State.CREATED
import androidx.lifecycle.Lifecycle.State.RESUMED
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class ComposeLifecycleOwnerTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private var mParentLifecycle: LifecycleRegistry? = null

  @Test
  fun childLifecycleOwner_initialStateIsResumedWhenParentIsResumed() {
    val parentLifecycle = ensureParentLifecycle()

    lateinit var childLifecycleOwner: LifecycleOwner
    composeTestRule.setContent {
      parentLifecycle.currentState = RESUMED
      childLifecycleOwner = rememberChildLifecycleOwner(parentLifecycle)
      CompositionLocalProvider(LocalLifecycleOwner provides childLifecycleOwner) {
        // let's assert right away as things are composing, because we want to ensure that
        // the lifecycle is in the correct state as soon as possible & not just after composition
        // has finished
        assertThat(childLifecycleOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.RESUMED)
      }
    }

    // Allow the composition to complete
    composeTestRule.waitForIdle()

    // Outside the composition, assert the lifecycle state again
    assertThat(childLifecycleOwner.lifecycle.currentState)
      .isEqualTo(Lifecycle.State.RESUMED)
  }

  @Test
  fun childLifecycleOwner_initialStateIsResumedAfterParentResumed() {
    val parentLifecycle = ensureParentLifecycle()

    lateinit var childLifecycleOwner: LifecycleOwner
    composeTestRule.setContent {
      childLifecycleOwner = rememberChildLifecycleOwner(parentLifecycle)
      parentLifecycle.currentState = CREATED
      CompositionLocalProvider(
        LocalLifecycleOwner provides childLifecycleOwner
      ) {
        // let's assert right away as things are composing, because we want to ensure that
        // the lifecycle is in the correct state as soon as possible & not just after composition
        // has finished
        assertThat(childLifecycleOwner.lifecycle.currentState).isEqualTo(Lifecycle.State.CREATED)
      }
    }

    // Allow the composition to complete
    composeTestRule.waitForIdle()

    // Outside the composition, assert the lifecycle state again
    assertThat(childLifecycleOwner.lifecycle.currentState)
      .isEqualTo(Lifecycle.State.CREATED)
  }

  @Test
  fun childLifecycleOwner_initialStateRemainsSameAfterParentLifecycleChange() {
    lateinit var updatedChildLifecycleOwner: LifecycleOwner
    lateinit var tempChildLifecycleOwner: LifecycleOwner

    val customParentLifecycleOwner: LifecycleOwner = object : LifecycleOwner {
      private val registry = LifecycleRegistry(this)
      override val lifecycle: Lifecycle
        get() = registry
    }

    composeTestRule.setContent {
      var seenRecomposition by remember { mutableStateOf(false) }
      // after initial composition, change the parent lifecycle owner
      LaunchedEffect(Unit) { seenRecomposition = true }
      CompositionLocalProvider(
        if (seenRecomposition) {
          LocalLifecycleOwner provides customParentLifecycleOwner
        } else {
          LocalLifecycleOwner provides LocalLifecycleOwner.current
        }
      ) {

        updatedChildLifecycleOwner = rememberChildLifecycleOwner()
        // let's save the original reference to lifecycle owner on first pass
        if (!seenRecomposition) {
          tempChildLifecycleOwner = updatedChildLifecycleOwner
        }
      }
    }

    // Allow the composition to complete
    composeTestRule.waitForIdle()
    // assert that the [ComposeLifecycleOwner] is the same instance when the parent lifecycle owner
    // is changed.
    assertThat(updatedChildLifecycleOwner).isEqualTo(tempChildLifecycleOwner)
  }

  private fun ensureParentLifecycle(): LifecycleRegistry {
    if (mParentLifecycle == null) {
      val owner = object : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
      }
      mParentLifecycle = owner.lifecycle
    }
    return mParentLifecycle!!
  }
}
