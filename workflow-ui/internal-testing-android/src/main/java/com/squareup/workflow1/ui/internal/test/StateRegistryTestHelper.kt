package com.squareup.workflow1.ui.internal.test

import android.os.Bundle
import android.view.View
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.ViewTreeSavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.SavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowAndroidXSupport.createSavedStateRegistryClient
import com.squareup.workflow1.ui.WorkflowUiExperimentalApi
import kotlin.test.fail

/**
 * Helper class for testing interactions with a [SavedStateRegistry].
 *
 * Write values to [statesToSaveByName] before asking the registry to save, and later check
 * [restoredStatesByName] to see if the values were restored that you were expecting.
 */
@WorkflowUiExperimentalApi
public class StateRegistryTestHelper {
  public val statesToSaveByName: MutableMap<String, String> = mutableMapOf()
  public val restoredStatesByName: MutableMap<String, String?> = mutableMapOf()

  public fun initialize(activity: AbstractLifecycleTestActivity) {
    var key: String? = null

    val registryClient = createSavedStateRegistryClient(
      object : SavedStateRegistryClient {
        override val savedStateRegistryKey: String get() = key!!

        override fun onSaveToRegistry(bundle: Bundle) {
          bundle.putString("rendering.name", key!!)
          statesToSaveByName[key!!]?.let { state ->
            bundle.putString("state", state)
          }
        }

        override fun onRestoreFromRegistry(bundle: Bundle?) {
          if (bundle != null) {
            assertThat(bundle.getString("rendering.name")).isEqualTo(key!!)
            restoredStatesByName[key!!] = bundle.getString("state")
          }
        }
      }
    )

    activity.onViewAttachStateChangedListener = { view, rendering, attached ->
      key = rendering.compatibilityKey

      if (attached) {
        assertThat(restoredStatesByName).doesNotContainKey(key)
        registryClient.onViewAttachedToWindow(view)
      } else {
        registryClient.onViewDetachedFromWindow(view)
      }
    }
  }
}

public fun View.requireStateRegistry(): SavedStateRegistry =
  ViewTreeSavedStateRegistryOwner.get(this)?.savedStateRegistry
    ?: fail("Expected ViewTreeSavedStateRegistryOwner to be set on view.")
