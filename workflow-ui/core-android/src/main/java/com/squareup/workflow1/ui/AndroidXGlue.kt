package com.squareup.workflow1.ui

import android.os.Bundle
import android.os.Parcel
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Lifecycle.Event.ON_DESTROY
import androidx.lifecycle.Lifecycle.Event.ON_RESUME
import androidx.lifecycle.Lifecycle.Event.ON_STOP
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.ViewTreeSavedStateRegistryOwner

/**
 *
 * @param restored True iff this glue was created by restoring from a parcel.
 */
// TODO make this a proper Parcelable
public class AndroidXGlue(
  private val compatibilityId: String,
  public val restored: Boolean
) : SavedStateRegistryOwner {

  private val lifecycle = LifecycleRegistry(this)
  private val savedStateRegistry = SavedStateRegistryController.create(this)

  override fun getLifecycle(): Lifecycle = lifecycle
  override fun getSavedStateRegistry(): SavedStateRegistry = savedStateRegistry.savedStateRegistry

  public fun install(view: View) {
    ViewTreeLifecycleOwner.set(view, this)
    ViewTreeSavedStateRegistryOwner.set(view, this)
  }

  public fun matches(rendering: Any): Boolean =
    compatibilityId == compatibilityId(rendering)

  public fun resume() {
    lifecycle.handleLifecycleEvent(ON_RESUME)
  }

  public fun stop() {
    lifecycle.handleLifecycleEvent(ON_STOP)
  }

  public fun destroy() {
    lifecycle.handleLifecycleEvent(ON_DESTROY)
  }

  public fun writeToParcel(out: Parcel) {
    out.writeString(compatibilityId)
    val bundle = Bundle()
    savedStateRegistry.performSave(bundle)
    out.writeBundle(bundle)
  }

  public companion object {
    public fun forRendering(rendering: Any): AndroidXGlue =
      AndroidXGlue(compatibilityId(rendering), restored = false)

    public fun readFromParcel(source: Parcel): AndroidXGlue? {
      val compatibilityId = source.readString() ?: return null
      val savedStateBundle = source.readBundle(AndroidXGlue::class.java.classLoader) ?: return null
      return AndroidXGlue(compatibilityId, restored = true).also { glue ->
        glue.savedStateRegistry.performRestore(savedStateBundle)
      }
    }

    /**
     * Returns a string that corresponds to [compatible] – any two objects that are [compatible] will
     * return the same key, and any two objects that are not [compatible] will return different keys.
     */
    @OptIn(WorkflowUiExperimentalApi::class)
    private fun compatibilityId(rendering: Any): String = buildString {
      append(rendering::class.java.name)
      if (rendering is Compatible) {
        append(rendering.compatibilityKey)
      }
    }
  }
}
